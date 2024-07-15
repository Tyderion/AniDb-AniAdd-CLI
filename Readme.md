# AniAdd

Forked from [AniDB](https://github.com/svn2github/AniDB)
Thanks for Anidb team for publishing the source of this java applet.

This Version replaces the GUI with a CLI only version.
Old Versions still support GUI, see Tag [v1.1.1](https://github.com/Tyderion/AniDb-AniAdd-CLI/releases/tag/1.1.1)

### Environment Variables
- `LOG_CONFIG_FILE`: Path to a properties file for logging [sample](https://github.com/Tyderion/AniDb-AniAdd-CLI/blob/feature/kodi-integration/src/main/resources/logging.properties). Will be used to update logging configuration and set log levels.

The following cli commands and options are available:
### Scan
```
Usage: aniadd-cli.jar anidb scan [-hV] [-c=<configPath>] -p=<password>
                      [--tagging-system=<taggingSystem>] -u=<username>
                      <directory>
Scans the directory for files and adds them to AniDb
      <directory>   The directory to scan.
  -c, --config=<configPath>
                    The path to the config file. Specified parameters will
                      override values from the config file.
  -h, --help        Show this help message and exit.
  -p, --password=<password>
                    The AniDB password
      --tagging-system=<taggingSystem>
                    the path to a file containing the Tagging System definition
  -u, --username=<username>
                    The AniDB username
  -V, --version     Print version information and exit.
```

### Connect To Kodi
```
Usage: aniadd-cli.jar anidb connect-to-kodi [-hV] [-c=<configPath>]
       --kodi=<kodiUrl> [--localport=<localPort>] -p=<password>
       [--path-filter=<pathFilter>] [--port=<port>]
       [--tagging-system=<taggingSystem>] -u=<username>
Connects to a kodi instance via websockets and marks watched episodes as
watched on anidb as well. Filepath must contain 'anime'.
  -c, --config=<configPath>
                         The path to the config file. Specified parameters will
                           override values from the config file.
  -h, --help             Show this help message and exit.
      --kodi=<kodiUrl>   The ip/hostname of the kodi server.
      --localport=<localPort>
                         The AniDB password
  -p, --password=<password>
                         The AniDB password
      --path-filter=<pathFilter>
                         The path filter to use to detect anime files. Default
                           is 'anime'. Case insensitive.
      --port=<port>      The port to connect to
      --tagging-system=<taggingSystem>
                         the path to a file containing the Tagging System
                           definition
  -u, --username=<username>
                         The AniDB username
  -V, --version          Print version information and exit.
```

### Config Save
```
Usage: aniadd-cli.jar config save [-hV] [-c=<configPath>]
                                  [--tagging-system=<taggingSystem>] <path>
Save the options to a new file which then can be edited (manually) and loaded
by using -c
      <path>      The path to the file to save the configuration to.
  -c, --config=<configPath>
                  The path to the config file. Specified parameters will
                    override values from the config file.
  -h, --help      Show this help message and exit.
      --tagging-system=<taggingSystem>
                  the path to a file containing the Tagging System definition
  -V, --version   Print version information and exit.
```

### Tags
```
Usage: aniadd-cli.jar anidb tags [-hV] [--movie] [-c=<configPath>] -p=<password>
                      [--tagging-system=<taggingSystem>] -u=<username>
Test Tags
  -c, --config=<configPath>
                  The path to the config file. Specified parameters will
                    override values from the config file.
  -h, --help      Show this help message and exit.
      --movie     Test movie naming
  -p, --password=<password>
                  The AniDB password
      --tagging-system=<taggingSystem>
                  the path to a file containing the Tagging System definition
  -u, --username=<username>
                  The AniDB username
  -V, --version   Print version information and exit.
```


This version is meant to be used on headless system (like your NAS) and still have the flexibility and useability of the official applet.

I suggest using a cron-job or something similar to periodically scan your download folder for new anime and automatically add them to your mylist and store them in the correct folder.

# Docker

There is a docker image available for now: https://hub.docker.com/repository/docker/tyderion/aniadd-cli/general

# Tutorial

There are intellij run configs available for these commands

1. Install compatible Java (21 or higher, 8 or higher for v1.1.1 or lower)
2. Generate default config file (`java --enable-preview -jar AniAdd.jar -u username -p password -d /path/to/anime -save config.conf`)
3. Edit configuration file with the editor of your choice, if you use the tagging system to move just replace it with your system
4. run AniAdd to scan and move your files (`java -jar AniAdd.jar -u username -p password -d /path/to/anime -c config.conf`

# Development
I recommend to use IntelliJ (Community Edition is enough) to develop this project.
Be sure to install the Lombok Plugin and enable annotation processing in the settings.
It currently only works with Java 21, higher/lower does not work due to the usage of a preview feature (template strings)