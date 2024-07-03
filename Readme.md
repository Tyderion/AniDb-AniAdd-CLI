# AniAdd

Forked from [AniDB](https://github.com/svn2github/AniDB)
Thanks for Anidb team for publishing the source of this java applet.

This Version replaces the GUI with a CLI only version.
Old Versions still support GUI, see Tag [v1.1.1](https://github.com/Tyderion/AniDb-AniAdd-CLI/releases/tag/1.1.1)

The following cli options are available:
```
usage: aom [-c <FILEPATH>] -d <PATH> -p <PASSWORD> [-s <FILENAME>]
       [--tagging-system <PATH>] -u <USERNAME>
Use AniAdd from the commandline.

 -c,--config <FILEPATH>       the path to the config file. Specified
                              parameters will override values from the
                              config file.
 -d,--directory <PATH>        directory
 -p,--password <PASSWORD>     password
 -s,--save <FILENAME>         save the options to a new file which then
                              can be edited (manually) and loaded by using
                              -c
    --tagging-system <PATH>   the path to a file containing the Tagging
                              System definition
 -u,--username <USERNAME>     username
```


This version is meant to be used on headless system (like your NAS) and still have the flexibility and useability of the official applet.

I suggest using a cron-job or something similar to periodically scan your download folder for new anime and automatically add them to your mylist and store them in the correct folder.

# Docker

There is a docker image available for now: https://hub.docker.com/repository/docker/tyderion/aniadd-cli/general

# Tutorial
1. Install compatible Java (21 or higher, 8 or higher for v1.1.1 or lower)
2. Generate default config file (`java -jar AniAdd.jar --no-gui  -u username -p password -d /path/to/anime -save config.conf`)
3. Edit configuration file with the editor of your choice, if you use the tagging system to move just replace it with your system
4. run AniAdd to scan and move your files (`java -jar AniAdd.jar --no-gui  -u username -p password -d /path/to/anime -c config.conf`
