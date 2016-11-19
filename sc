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
SCVERSION=1002 # Version number (major * 1000 + minor)
SCROOTDIR=""
SCPREFIX="$SCROOTDIR/usr/local"
SCLIBDIR="$SCPREFIX/lib/$SC"
SCBIN="$SCPREFIX/sbin/$SC"
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

### Global functions ###

# Call a submodule
subcall() {
  local mod="$1"; shift
  local sub="$1"; shift
  if [ "${sub:0:1}" = "_" ]; then
    echo "Cannot call $mod $sub: private function"
  fi
  if [ "$(type -t "${mod}_$sub")" = "function" ]; then
    "${mod}_$sub" "$@"
  else
    echo "Cannot find $SC $mod $sub"
    exit 1
  fi
}

# Tell if a submodule supports a function
cancall() {
  local mod="$1"; shift
  local sub="$1"; shift
  if [ "$(type -t "${mod}_$sub")" = "function" ]; then
    return 0
  fi
  return 1
}

# Import a common module
require() {
  local mod="$1"; shift
  local setup="$1"; shift || true

  if [[ " $SCMODULES " != *" $mod "* ]]; then
    if [ -r "$SCLIBDIR/$mod" ]; then
      source "$SCLIBDIR/$mod"
    else
      echo "Required module $mod not found"
      exit 2
    fi
    # Add the main entry point
    eval "$mod() { subcall $mod \"\$@\"; }"
    SCMODULES="$SCMODULES $mod"
  fi

  if [ "$setup" = setup ]; then
    if [ "$(type -t "${mod}_ready")" = "function" ] && [ "$(type -t "${mod}_setup")" = "function" ] && ! "$mod" ready; then
      "$mod" setup
    fi
  elif [ "$setup" = ready ]; then
    if [ "$(type -t "${mod}_ready")" = "function" ] && ! "$mod" ready; then
      echo "Module $mod found but not set up"
      return 1
    fi
  fi
}

# Import an optional module
has() {
  local mod="$1"; shift

  if [[ " $SCMODULES " == *" $mod "* ]]; then
    # Already loaded
    true
  elif [ -r "$SCLIBDIR/$mod" ]; then
    source "$SCLIBDIR/$mod"
    # Add the main entry point
    eval "$mod() { subcall $mod \"\$@\"; }"
  else
    return 2
  fi
  SCMODULES="$SCMODULES $mod"
  if [ "$(type -t "${mod}_enabled")" = "function" ]; then
    if ! "${mod}_enabled"; then
      # The module does not want to exist !
      return 1
    fi
  fi
  if [ "$(type -t "${mod}_ready")" = "function" ]; then
    if ! "${mod}_ready"; then
      # The module is not set up !
      return 1
    fi
  fi
  return 0
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

# Call a method on all commands matching filter
broadcall() {
  local function="$1"; shift

  for f in "$SCLIBDIR"/*; do
    local mod="$(basename "$f")"
    source "$f"
    # Add the main entry point
    eval "$mod() { subcall $mod \"\$@\"; }"
    if [ "$(type -t "${mod}_$function")" = "function" ]; then
      "$mod" "$function" "$@"
    fi
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
    if ! [ -e "$c" ] && ! which "$c" &>/dev/null; then
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

