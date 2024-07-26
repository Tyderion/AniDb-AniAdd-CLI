# AniAdd

Forked from [AniDB](https://github.com/svn2github/AniDB)
Thanks for Anidb team for publishing the source of this java applet.

Big thanks to @runecalico for testing and providing feedback.

This Version replaces the GUI with a CLI only version.
Old Versions still support GUI, see Tag [v1.1.1](https://github.com/Tyderion/AniDb-AniAdd-CLI/releases/tag/1.1.1)

The following cli commands are available (check designated command help `--help` for more information):
- `tags`: Test your tag system with example data
- `config save`: Convert old config file to new format (or generate a new default config by specifying --default)
- `anidb scan`: Scan given folder for anime, optionally adding them to your mylist and moving the files). Shuts down after scan.
- `anidb watch`: Watch given folder for new anime, optionally adding them to your mylist and moving the files. Will keep running until stopped.
- `anidb connect-to-kodi`: Connect to Kodi and mark episodes as watched in your mylist after watching them.
- `anidb watch-and-kodi`: Combine `watch` and `connect-to-kodi` commands.

This version is meant to be used on headless system (like your NAS) and still have the flexibility and useability of the official applet.

I suggest using a the watch command to monitor your download folder and automatically add new anime to your mylist and move them to your anime folder.

# Tutorial

1. Install compatible Java (21 or higher, 8 or higher for v1.1.1 or lower)
2. Generate config file 
   - new config `java --enable-preview -jar AniAddCli.jar config save config.conf --default`
   - from old config `java --enable-preview -jar AniAddCli.jar config save config.conf -c oldconfig.conf`
3. Edit configuration file with the editor of your choice, if you use the tagging system to move just replace it with your system
4. run AniAddCli to scan and move your files 
   - scan once: `java --enable-preview -jar AniAddCli.jar -u username -p password -anidb -c config.conf scan /path/to/your/anime/folder`
   - watch folder: `java --enable-preview -jar AniAddCli.jar -u username -p password -anidb -c config.conf watch /path/to/your/anime/folder`
5. (optional) run AniAddCli to connect to Kodi and mark episodes as watched in your mylist after watching them
   Make sure to enable remote access to JSON-RPC in your Kodi settings
   - `java --enable-preview -jar AniAddCli.jar -u username -p password -anidb -c config.conf connect-to-kodi --kodi-url <your-kodi-ip>`.

# Docker

There is a docker image available : https://hub.docker.com/r/tyderion/aniadd-cli

## Docker Usage (v4.0.0+)
### Any Run Configuration
mounts:
- `/shows`: Folder to move the files of anime shows into (configurable in your settings file)
- `/movies`: Folder to move the files of anime movies into (configurable in your settings file)

Env:
- `ANIDB_USERNAME` [required]: your username
- `ANIDB_PASSWORD` [required]: your password e
- `ANIDB_CONF` [required]: path to your config file, [example](https://github.com/Tyderion/AniDb-AniAdd-CLI/blob/master/.run/docker.conf), needs a corresponding mounted location of course :)
- `LOG_CONFIG_FILE`: Path to a properties file for logging [sample](https://github.com/Tyderion/AniDb-AniAdd-CLI/blob/master/.run/logging.properties). Will be used to update logging configuration and set log levels. Defaults to the linked file.

### Scanning and watching 
entrypoint: `/app/scan.sh` (default command of docker image)

entrypoint: `/app/watch.sh` 

Runs either `scan` or `watch` command.

#### mounts
- `/from`: Folder containing video files to parse and handle [required], configurable via env var `SCAN_FOLDER`
- `/unknown`: Folder to move files into that anidb does not know [optional, defaults to /unknown], configurable in your settings file
- `/duplicates`: Folder to move duplicate files to (alternatively those can be deleted, configurable in your settings file)

#### Env Vars
- `SCAN_FOLDER` [optional, defaults to `/from`]: the folder to scan for new anime

### Kodi 
entrypoint: `/app/kodi.sh`

Runs the `connect-to-kodi` command.

#### Env Vars
- `KODI_HOST` [required]: the ip/hostname of your kodi instance
- `KODI_PORT` [optional, default=9090]: the websocket port of your kodi instance

### Watch and Kodi
entrypoint: `/app/watch-and-kodi.sh`

Runs the `watch-and-kodi` command which combines `watch` and `connect-to-kodi`.


### Noop
entrypoint: `/app/noop.sh`

Keeps container alive, so you can connect a shell and run commands manually.

### Flexible Run Configuration
entrypoint: `/app/run.sh` 

Enables you to run any command you want by specifying it in an env variable.

#### Env Vars
- `COMMAND` [required]: the command to run (see above), any required args must be set

# Development
I recommend to use IntelliJ (Community Edition is enough) to develop this project.
Be sure to install the Lombok Plugin and enable annotation processing in the settings.
It currently only works with Java 21, higher/lower does not work due to the usage of template strings