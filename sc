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
SCROOTDIR=""
SCPREFIX="$SCROOTDIR/usr/local"
SCLIBDIR="$SCPREFIX/lib/$SC"
SCBIN="$SCPREFIX/sbin/$SC"
SCCMDDIR="$SCLIBDIR/commands"
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

### Main program ###

# Command-line parsing
if [ -z "${ARGS[0]}" ] || [ "${ARGS[@]}" = "--help" ]; then
  echo "$SC usage:"
  echo "$SC subcommand [PARAMETERS]"
  exit 1
fi

subcmd="${ARGS[0]}"
ARGS=("${ARGS[@]:1}")

if [ "$UID" -ne 0 ]; then
echo "This program requires root privileges."
exit 1
fi

if [ -r "$SCLIBDIR/common" ] && [ "$subcmd" != "install" ]; then
  SCINSTALLED=1
  source "$SCLIBDIR/common"
else
  # Not installed
  if [ -r "$SCCURBINDIR/lib/common" ]; then
    source "$SCCURBINDIR/lib/common"
  else
    echo "$SC cannot be installed."
    echo "Error: lib directory not found"
    exit 1
  fi
  if [ "$subcmd" = "install" ]; then
    if [ -r "$SCCURBINDIR/lib/commands/install" ]; then
      source "$SCCURBINDIR/lib/commands/install"
      install
    else
      echo "Error: Install module not found."
      exit 1
    fi
    exit 0
  else
    echo "$SC must be installed."
    echo "Please run:"
    echo " # $SC install"
  fi
fi

if [ -r "$SCCMDDIR/$subcmd" ]; then
  source "$SCCMDDIR/$subcmd"
  $subcmd "${ARGS[@]}"
else
  echo "$subcmd: subcommand not found"
  exit 1
fi


