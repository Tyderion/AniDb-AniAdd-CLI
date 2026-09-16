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
    overwrite:
      series: false
      episodes: false
      movies: false
      artwork: false
  libraryScan:
    enabled: true
    scope: changed
    showDialogs: false
    timeoutInMinutes: 30
    libraries:
      - name: anime series
        localPath: /shows
      - path: /storage/media/anime/movies/
        localPath: /movies
```

- `kodi`: Kodi Configuration
  - `host`: Kodi host (or ip)Configuration
  - `port`: Kodi port (default: 9090)
  - `pathFilter`: Only paths containing this string will be marked as watched
  - `metadata`: Kodi NFO metadata generation
    - `generate`: Generate `.nfo` files and artwork next to processed files (default: false)
    - `syncWatchedStateFromMylist`: Write the MyList watched state into episode NFOs as `lastplayed` (default: false)
    - `animeMappingUrl`: Source URL of the Anime-Lists mapping list (default: the Anime-Lists GitHub master `anime-list.xml`). The downloaded list is cached in the sqlite db configured at `anidb.cache.db` (keyed by this URL, refreshed after `anidb.cache.ttlInDays`), not as a file next to the app; a pre-existing fresh `anime-list.xml` in the working directory is imported into the db once as a seed.
    - `overwrite`: Which existing metadata files are replaced when they already exist. All default to false, so files from an earlier run are kept.
      - `series`: `tvshow.nfo` of a series folder
      - `episodes`: `<video-basename>.nfo` of an episode
      - `movies`: `<video-basename>.nfo` of a movie
      - `artwork`: episode/movie thumbnails, fanart, poster, banner and actor images
    - `tvDbApiKey` / `tmDbApiToken`: API credentials for TVDB (series) and TMDB (movies). Like the AniDB password they must not be placed in the config file — pass them via CLI (`--tvDbApiKey`, `--tmDbApiToken`) or env (`TVDB_APIKEY`, `TMDB_ACCESS_TOKEN`)
  - `libraryScan`: Ask Kodi to scan its video library once, at the end of each directory scan run (every `scan`, every `watch` interval), if that run moved or renamed files or wrote NFOs Kodi has not seen yet. Files re-processed to mark them watched from Kodi never trigger a scan. Connects to `host`/`port` over the websocket (no Kodi web-server login needed). For `scan` and `watch` the host and port can also come from `--kodi-host`/`--kodi-port` or `KODI_HOST`/`KODI_PORT`. A Kodi that cannot be reached is logged as an error and the changed files are retried at the end of the next run.
    - `enabled`: Turn the scan on (default: false). Startup fails if it is enabled without libraries or with an entry that has both or neither of `name` and `path`.
    - `scope`: `changed` scans only the top-level folder (the show or movie folder) below a library that received files, or the library root for files placed directly in it; `library` scans every configured library whole whenever anything changed (default: `changed`)
    - `showDialogs`: Show Kodi's scan progress bar on screen (default: false)
    - `timeoutInMinutes`: Scans run one at a time, each waiting for Kodi's `VideoLibrary.OnScanFinished`; after this long the next one starts anyway (default: 30)
    - `libraries`: The Kodi video sources to scan, each given by exactly one of:
      - `name`: The source label as shown in Kodi, matched case-insensitively and resolved to its path through `Files.GetSources`. An unknown name is logged together with the labels Kodi does know.
      - `path`: The source path as Kodi sees it, e.g. `/storage/media/anime/series/` or `smb://nas/anime/`
      - `localPath`: The same folder as AniAdd sees it (inside Docker, the container path). Needed by `scope: changed` to map a moved file to its Kodi folder; a library without it is scanned whole whenever anything changed, and files outside every `localPath` trigger no scan.

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

