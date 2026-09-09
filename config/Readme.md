# Configuration
# Logging
Override log levels by class namespace, see [logging.override.properties](logging.override.properties)
Can be injected into the application by setting env `LOG_CONFIG_FILE` to the path of the file.

## New Configuration Format

If you are using the old configuration (AniConfiguration), you can convert it by using:

`config convert <old-config-path> <new-config-path>`.

If you have a tagging system (examples: [Kodi](tagging-system.kodi.txt), [Other](tagging-system.txt)) you can convert it by using:

`config convert --tagging-system <taggingfile-path> <old-config-path> <new-config-path>`

Example: See [docker.yaml](docker.yaml)

### Structure

#### MyList

```yaml
mylist:
  add: true
  storageType: remote
  watched: false
```

- `add`: Add to MyList.
- `storageType`: unknown, internal, external, deleted, remote see [AniDb](https://wiki.anidb.net/Filestates)
- `watched`: Mark as watched when adding

#### AniDB

```yaml
anidb:
  cache:
    db: aniadd.sqlite
    ttlInDays: 31
```

- `cache`: Cache configuration
    - `db`: Path to the sqlite cache db. Stores hashed file data, downloaded AniDB anime details XML, and the Anime-Lists anime mapping list (`anime-list.xml` content, keyed by its source URL) — everything cached lives and moves with this one file.
    - `ttlInDays`: Time to live for all cached entries (file data, anime XML, and the anime mapping list)

#### File

```yaml
file:
  rename:
    # Rename Config
  move:
    # Move Config
```

##### Rename

```yaml
file:
  rename:
    mode: tagsystem
    related: true
```

- `mode`: Rename mode possible values:
    - `tagsystem`: Use tag system
    - `anidb`: Use AniDBFileName
    - `none`: No rename
- `related`: Rename related files

##### Move

```yaml
file: 
  move:
    mode: tagsystem # or folder or none
    folder: /unused/with/tagsystem
    deleteEmptyDirs: true
    duplicates:
      mode: move # or delete or none
      folder: /duplicates/
    unknown:
      mode: move # or delete or none
      folder: /unknown/
```

- `mode`: Move mode, possible values:
  - `tagsystem`: Use tag system
  - `folder`: Use specific folder
  - `none`: No move
- `deleteEmptyDirs`: Delete empty (source) directories after moving
- `duplicates`: Duplicates configuration
  - `mode`: Duplicates mode, possible values:
      - `move`: Move duplicates
      - `delete`: Delete duplicates
      - `none`: Do nothing
  - `folder`: Duplicates folder (if mode is move)
- `unknown`: Same as duplicates, just for unknown files

#### Tagsystem

```yaml
tags:
  paths:
    # Path Config
  tagSystem: 
    # Tag System Code
```

##### Paths
Configured paths will be injected into the tagsystem with the given name
Will also be used to find the correct file for the kodi watcher.
```yaml
tags:
  paths:
    movieFolders:
      - path: /movies/
        tagSystemName: BaseMoviePath
    tvShowFolders:
      - path: /shows/
        tagSystemName: BaseTVShowPath
```

- `movieFolders`: List of movie folders
  - `path`: Path to movie folder
  - `tagSystemName`: Tag system name
- `tvShowFolders`: Same configuration as movieFolders, just for series

#### Kodi
Kodi watcher will mark files as watched on anidb if they are watched in kodi.
```yaml
kodi:
  host: kodi.local
  pathFilter: anime
  port: 9090
  metadata:
    generate: true
    syncWatchedStateFromMylist: true
    animeMappingUrl: https://raw.githubusercontent.com/Anime-Lists/anime-lists/master/anime-list.xml
```

- `kodi`: Kodi Configuration
  - `host`: Kodi host (or ip)Configuration
  - `port`: Kodi port (default: 9090)
  - `pathFilter`: Only paths containing this string will be marked as watched
  - `metadata`: Kodi NFO metadata generation
    - `generate`: Generate `.nfo` files and artwork next to processed files (default: false)
    - `syncWatchedStateFromMylist`: Write the MyList watched state into episode NFOs as `lastplayed` (default: false)
    - `animeMappingUrl`: Source URL of the Anime-Lists mapping list (default: the Anime-Lists GitHub master `anime-list.xml`). The downloaded list is cached in the sqlite db configured at `anidb.cache.db` (keyed by this URL, refreshed after `anidb.cache.ttlInDays`), not as a file next to the app; a pre-existing fresh `anime-list.xml` in the working directory is imported into the db once as a seed.
    - `tvDbApiKey` / `tmDbApiToken`: API credentials for TVDB (series) and TMDB (movies). Like the AniDB password they must not be placed in the config file — pass them via CLI (`--tvDbApiKey`, `--tmDbApiToken`) or env (`TVDB_APIKEY`, `TMDB_ACCESS_TOKEN`)

#### Run
If this block is present, you can run the configured task with `run -r <config-file-path>`.

example (Scan folder every 45 minutes):
```yaml
run:
  task: watch
  args:
    path: /newfiles/
    interval: 45
```
- `task`: The task to run:
  - `watch`: Watch folder
  - `scan`: Scan folder
  - `kodi`: Connect to kodi and mark watched files as watched on anidb
- `args`: Arguments for the tasks.
  - `path: /newfiles/`: Will be used as parameter for watch and scan task
  - `anyarg: anyValue`: Set arbitrary argument of the given task

