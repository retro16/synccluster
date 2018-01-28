#!/bin/bash
###############################################################################
<<LICENSE

The MIT License (MIT)

Copyright (c) 2016 Jean-Matthieu COULON

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

LICENSE
###############################################################################

set -e

# Defaults
SC="sc"
SCVERSION=1006 # Version number (major * 1000 + minor)
SCROOTDIR=""
SCPREFIX="$SCROOTDIR/usr/local"
SCLIBDIR="$SCPREFIX/lib/$SC"
SCBINDIR="$SCPREFIX/sbin"
SCBIN="$SCBINDIR/$SC"
SCDEF="$SCROOTDIR/etc/default/$SC"
SCSETTINGS="$SCROOTDIR/etc/$SC"

# Load settings from etc file
if [ -r "$SCDEF" ]; then
  source "$SCDEF"
fi

if [ -r "$SCSETTINGS" ]; then
  source "$SCSETTINGS"
fi

# Fetch information we can only have at startup
SCPWD="$PWD"
SCCURBIN="$(readlink -fs "$0")"
SCCURBINNAME="$(basename "$SCCURBIN")"
SCCURBINDIR="$(dirname "$SCCURBIN")"
SCINSTALLED=""
ARGS=("$@")

# Fix environment variables to sane values
if [ "$HOME" = / ] && [ "$UID" -eq 0 ]; then
  HOME=/root
fi
PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

### Main program ###

# Initialization
SCMODULES=""
TRAPS=""

trappush() {
  local cmd="$1"; shift
  TRAPS="$cmd"$'\n'"$TRAPS"
  trap "$TRAPS" EXIT
}

trappop() {
  TRAPS="$(tail -n +1 <<< "$TRAPS")"
  trap "$TRAPS" EXIT
}

### Global functions ###

listhas() {
  local list="$1"; shift
  local elt="$1"; shift

  [[ " ${!list} " == *" $elt "* ]]
}

listadd() {
  local list="$1"; shift

  for elt in "$@"; do
    if ! [ "$elt" ]; then continue; fi
    if ! listhas "$list" "$elt"; then
      eval "$list"="'$elt ${!list}'"
    fi
  done
}

listappend() {
  local list="$1"; shift

  for elt in "$@"; do
    if ! [ "$elt" ]; then continue; fi
    if ! listhas "$list" "$elt"; then
      eval "$list"="'${!list} $elt'"
    fi
  done
}

listdel() {
  local list="$1"; shift

  for elt in "$@"; do
    if ! [ "$elt" ]; then continue; fi
    eval "$list"="'$(sed "s/ $elt //" <<< " ${!list} ")'"
    eval "$list"="'${!list# }'"
    eval "$list"="'${!list% }'"
  done
}

# Returns true if the first parameter is equal to any other parameter
# example:
#  contains standby master standby client
#  Returns true
contains() {
  local value="$1"; shift
  for pattern in "$@"; do
    if [ "$value" = "$pattern" ]; then
      return 0
    fi
  done
  return 1
}

# Tell if a variable is a defined boolean (1 or 0)
isbool() {
  local varname="$1"; shift
  if [ "${!varname}" = "1" ] || [ "${!varname}" = "0" ]; then
    return 0
  fi
  return 1
}

# Returns true if a variable is a boolean with a true value.
# Remember that shell uses inverted logic so 1 is false and 0 is true
getbool() {
  local varname="$1"; shift
  return ${!varname}
}

# Tell if a submodule supports a function
cancall() {
  local mod="$1"; shift
  local sub="$1"; shift
  if ! listhas SCMODULES "$mod"; then
    echo "Error: $mod not loaded"
    exit 2
  fi
  if [ "$(type -t "${mod}_$sub")" = "function" ]; then
    return 0
  fi
  return 1
}

# Call a submodule
subcall() {
  local mod="$1"; shift
  local sub="$1"; shift
  if [ "${sub:0:1}" = "_" ]; then
    echo "Cannot call $mod $sub: private function"
  fi
  if cancall "$mod" "$sub"; then
    "${mod}_$sub" "$@"
  else
    echo "Error: Cannot find $SC $mod $sub"
    exit 1
  fi
}

# Import a common module
require() {
  local mod="$1"; shift
  local setup="$1"; shift || true

  if ! listhas SCMODULES "$mod"; then
    if [ -r "$SCLIBDIR/$mod" ]; then
      source "$SCLIBDIR/$mod"
    else
      echo "Error: Required module $mod not found"
      exit 2
    fi

    # Add the main entry point
    eval "$mod() { subcall $mod \"\$@\"; }"
    listadd SCMODULES "$mod"

    if cancall "$mod" requires; then
      for dep in $("$mod" requires); do
        require "$dep" "$setup"
      done
    fi
  fi

  if [ "$setup" = setup ]; then
    if cancall "$mod" setup && cancall "$mod" ready && ! "$mod" ready; then
      "$mod" setup
    fi
  elif [ "$setup" = ready ]; then
    if cancall "$mod" ready && ! "$mod" ready; then
      echo "Error: Module $mod found but not set up"
      exit 1
    fi
  fi
}

has() {
  require "$@"
}

# Reload all loaded modules from files
reload_modules() {
  local modlist="$SCMODULES"
  SCMODULES=""
  for mod in $modlist; do
    require "$mod"
  done
}

# Call upgrade_NUMBER to upgrade all modules from an old version to a newer one
upgrade_modules() {
  local old="$1"; shift
  local new="$1"; shift || new="$SCVERSION"

  reload_modules
  while [ "$old" -lt "$new" ]; do
    old="$((old+1))"
    broadcall upgrade_$old
  done
}

# Get the list of modules, with correct dependency order
modlist() {
  local mlist=""

  for f in "$SCLIBDIR"/*; do
    local mod="$(basename "$f")"
    listappend mlist $(moddeps "$mod") "$mod"
  done
  echo $mlist
}

# Return the list of dependencies for a module,
# either direct or indirect, in correct dependency order
moddeps() {
  local mod="$1"; shift
  require "$mod"
  local deps="$mod"
  if cancall "$mod" requires; then
    local ndeps=""
    while [ "$deps" != "$ndeps" ]; do
      ndeps="$deps"
      for d in $deps; do
        if cancall "$d" requires; then
          listadd deps $("$d" requires)
        fi
      done
    done
    listappend mlist $deps
  fi
  deps="${deps% $mod}"
  echo $deps
}

# Call a method for all modules
broadcall() {
  local function="$1"; shift

  for mod in $(modlist); do
    require "$mod"
    if cancall "$mod" "$function"; then
      "$mod" "$function" "$@"
    fi
  done
}

# Call a method for all ready modules
broadcall_ready() {
  local function="$1"; shift

  for mod in $(modlist); do
    require "$mod"
    if ( ! cancall "$mod" ready || "$mod" ready ) && cancall "$mod" "$function"; then
      "$mod" "$function" "$@"
    fi
  done
}

# Call a method on dependencies of a module
depcall() {
  local mod="$1"; shift

  require "$mod"
  for d in $(moddeps "$mod"); do
    "$d" "$@"
  done
}

# Setup all dependencies of a module
depsetup() {
  local mod="$1"; shift

  require "$mod"
  for d in $(moddeps "$mod"); do
    require "$d" setup
  done
}

# Return the list of all commands.
# takes an optional filter function
cmdlist() {
  local filter="$1"; shift || true

  for f in "$SCLIBDIR"/*; do
    local mod="$(basename "$f")"
    if [ "$filter" ]; then
      source "$f"
      # Add the main entry point
      eval "$mod() { subcall $mod \"\$@\"; }"
      if [ "$(type -t "${mod}_$filter")" != "function" ] || "$mod" "$filter"; then
        echo "$mod"
      fi
    else
      echo "$mod"
    fi
  done
}

# Optionally call a function. Returns true if function missing
optcall() {
  local mod="$1"; shift
  local function="$1"; shift

  if has "$mod" && [ "$(type -t "${mod}_$function")" = "function" ]; then
    "$mod" "$function" "$@"
  fi
}

# Check that command-lines are available, and if some are missing, install the corresponding system package
syspackage() {
  local package="$1"; shift

  require aptpackage setup

  if [ "$#" -eq 0 ]; then
    if apt-get -y install "$package"; then
      return 0
    else
      echo "Cannot install required package $package"
      exit 1
    fi

    return 0
  fi

  for c in "$@"; do
    if ( [ "${c:0:1}" != "/" ] || ! [ -e "$c" ] ) && ! which "$c" &>/dev/null; then
      if apt-get -y install "$package"; then
        return 0
      else
        echo "Cannot install required package $package"
        exit 1
      fi
    fi
  done

  return 0
}

git_get() {
  local url="$1"
  local target="$2"
  syspackage git git

  if [ -d "$target/.git" ]; then
    pushd "$target" &>/dev/null
    git clean -dfx
    git pull
    popd &>/dev/null
  elif [ -d "$target" ]; then
    rm -rf "$target"
    git clone "$url" "$target"
  else
    git clone "$url" "$target"
  fi
}

# Install function
install_sc() {
  # Install SC
  mkdir -p "$(dirname "$SCBIN")" || true
  cp "$SCCURBIN" "$SCBIN"
  chown root:root "$SCBIN"
  chmod u+rwx,g+rx,o+rx "$SCBIN"

  rm -rf "$SCLIBDIR" || true
  mkdir -p "$(dirname "$SCLIBDIR")" || true
  cp -r "$SCCURBINDIR/lib" "$SCLIBDIR"
  chown -R root:root "$SCLIBDIR"
  chmod -R u+rwX,g+rX,o+rX "$SCLIBDIR"

  # Run post install operations
  require settings
  if ! [ -e "$SCSETTINGS" ]; then
    SETTINGS_VERSION="$SCVERSION"
  fi
  settings save
  echo "$SC installed"
}

### Command-line parsing ###

subcmd="${ARGS[0]}"
ARGS=("${ARGS[@]:1}")

### Check install and privileges ###

if [ "$UID" -ne 0 ]; then
  echo "This program requires root privileges."
  exit 1
fi

if [ -d "$SCLIBDIR" ] && [ "$subcmd" != "install" ]; then
  SCINSTALLED=1
else
  # Not installed
  if ! [ -d "$SCCURBINDIR/lib" ]; then
    echo "$SC cannot be installed."
    echo "Error: lib directory not found"
    exit 1
  fi
  if [ "$subcmd" = "install" ]; then
    install_sc
    exit 0
  else
    echo "$SC must be installed."
    echo "Please run:"
    echo " # $SC install"
  fi
fi

### Call subcommand ###

require "$subcmd"
subcall "$subcmd" "${ARGS[@]}"

