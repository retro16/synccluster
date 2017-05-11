# synccluster

synccluster allows loosely coupled machines to share accounts, data and programs as seamlessly as possible while keeping the scripts "simple". It is written entirely in bash and provides auto-updates.


# Installing synccluster

## Supported platforms

 * Debian 8


## Quick install guide

As root, run the following commands:


    wget -qO- https://github.com/retro16/synccluster/archive/master.tar.gz | tar -xvz
    synccluster-master/sc install


synccluster installs the following files:

 * /usr/local/sbin/sc : The main script
 * /usr/local/lib/sc : Modules
 * /etc/sc : Configuration file

## Update synccluster

As root, run the following command:

    sc update start

# Concepts

sc is split in modules. The command-line is structured as "sc <module> <parameters>".
Dependencies between modules are automatically resolved.

Most modules have 3 exposed functions: setup, cleanup and ready. setup installs the functionality provided by the module, cleanup disables the functionality (cleaning up as much as possible) and ready returns a boolean return value that tells whether the module is setup or not (this uses approximate heuristics).

Modules can expose settings, files to be backed up and other various actions.


# Modules

This documentation presents all interesting modules and commands. Some are not listed here because they are either for internal use (and may disappear/change in the near future) or because they are considered unstable for general use.

## backup

Allows to backup a whole host, files and restore them to a functional state easily.

Module functions:

 * sc backup cleanup : Disables all cron tasks, resets settings.
 * sc backup add host:/path period : Begin backing up the given path on the host. If /path is left blank, calls sc remotely to find which files are necessary to backup all modules of the remote host. Period defaults to "daily" and can be "hourly", "daily" or "weekly".
 * sc backup del host:/path period : Stop backing up a host. Parameters must be exactly the same as the "add" command.
 * sc backup list : List all currently active backups on the host.
 * sc backup restore_from /path/to/source : Restore data from a backup. This will restore all data from all available modules (if present in the backup).


## lifeline

Keeps a reverse SSH tunnel all the time to allow to connect to a host behind an unstable firewall.

If the server is behind a router that can be reset accidentally or behind a fragile NAT, this module can help keeping a safety connection all the time, as outgoing connections are generally more successful than incoming connections in that situation.

Module functions:

 * sc lifeline setup : Create the lifeline link
 * sc lifeline cleanup : Remove the lifeline link


## mass

Setup a mass storage directory. This is often called internally but has its uses off the shelf. The mass storage can be backed up automatically or not (when backing up the whole host, e.g. when calling sc backup add with no /path).

If the host is a slave or a client, 4 modes can be selected:

 * separated : this host has its own mass storage
 * unison : this host synchronizes the whole mass storage directory with its master
 * sshfs : this host mounts the mass storage directory of the master remotely using sshfs
 * nfs4 : this host mounts the mass storage directory of the master remotely using nfs4

Module functions:

 * sc mass setup : Configure a mass storage path.
 * sc mass cleanup : Unset the mass storage path (unmount it if it was remote). Does not delete data in separated or unisson modes.


## master

Manages roles inside a network. Affects the behavior of many other modules.

Roles can be the following:

 * master : A standalone server, with full blown server tools (roundcube, owncloud, mass storage, NFS, ...).
 * standby : A standby server, synchronizing from its master periodically so it can do failover any time.
 * backup : A server dedicated to backup. Has the minimum amount of software installed to only backup other hosts remotely.
 * client : A standard client with a desktop and a mounted NFS home directory.
 * roaming : A roaming client loosely coupled to its master, using unison to synchronize data. Users must be manually declared in roaming mode so the whole /home is not synchronized.

Note: a standby server can be the master of other hosts. Some functionality might not work in that case like resharing mass storage.

Module functions:

 * sc master setup : setup a machine to a new role
 * sc master cleanup : undo the minimum amount of things done by setup so it can be run again
 * sc master promote : Promotes a standby to the role of master. Used for failover.
 * sc master promote standby master.example.com : Switch a master to standby mode using "master.example.com" as its master.
 * sc master promote client : Switch a roaming host to the normal client mode.
 * sc master promote roaming : Switcha a normal client to roaming mode.
 * sc master switch newmaster.example.com : Change the master host of a host.
 * sc master pull : Force synchronization with the master host.
 * sc master exec : Execute a command on the master host.
 * sc master update_sc : Update sc to the version available on the master.


## media

Setup a media center on the host. This is not a role like with "sc master", it justs sets up software needed to run Kodi and RetroArch.

Module functions:

 * sc media setup : setup a media center
 * sc media cleanup : remove media center and turn back to a normal host


## owncloud

Installs owncloud on the server.

Module functions:

 * sc owncloud setup : setup owncloud
 * sc owncloud cleanup : remove owncloud


## roundcube

Setup a postfix+dovecot+procmail+spamassassin+roundcube server, all in 1 command (and a few questions) !

Module functions:

 * sc roundcube setup : Install all the necessary software and configurations. At the debian postfix setup prompt, you can select any option, the package will be reconfigured anyway.
 * sc roundcube cleanup : Try to remove all the software installed. As this can be complex, it may leave some files or configurations here and there !


## unisonsync

Allow to synchronize directories using unison.

Module functions:

 * sc unisonsync add host.example.com /remote/path /local/path period : Synchronizes directories periodically. If period is omitted, synchronization will be only manual using sc unisonsync start.
 * sc unisonsync start host.example.com /remote/path /local/path period : Start synchronizing directories immediatelly. If period is omitted, synchronization will be only manual using sc unisonsync start.
 * sc unisonsync del host.example.com /remote/path period : Removes a synchronization. Parameters must be exactly the same as add and start.
 * sc unisonsync list : List all synchronizations currently active on the host.


## wstunnel

Install wstunnel to access the SSH port through restrictive networks.

Module functions:

 * sc wstunnel setup : setup a wstunnel server.
 * sc wstunnel client : setup a wstunnel client. This will open the 8022 port on the local machine that redirects to the SSH port of the target server.
 * sc wstunnel cleanup : disable wstunnel.

