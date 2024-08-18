## New Configuration Format

If you are using the old configuration (AniConfiguration), you can convert it by using:
`config convert <old-config-path> <new-config-path>`.

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
    filename: aniadd.sqlite
    ttlInDays: 31
```

- `cache`: Cache configuration
    - `filename`: Cache filename
    - `ttlInDays`: Time to live in

#### Rename

```yaml
rename:
  type: tagsystem
  related: true
```

- `type`: Rename type, possible values:
    - `tagsystem`: Use tag system
    - `anidb`: Use AniDBFileName
    - `none`: No rename
- `related`: Rename related files

#### Move

```yaml
move:
  type: tagsystem # or folder or none
  folder: /unused/with/tagsystem
  deleteEmptyDirs: true
  duplicates:
    type: move # or delete or ignore
    folder: /duplicates/
  unknown:
    type: move # or delete or ignore
    folder: /unknown/
```

- `type`: Move type, possible values:
  - `tagsystem`: Use tag system
  - `folder`: Use specific folder
  - `none`: No move
- `deleteEmptyDirs`: Delete empty (source) directories after moving
- `duplicates`: Duplicates configuration
  - `type`: Duplicates type, possible values:
      - `move`: Move duplicates
      - `delete`: Delete duplicates
      - `ignore`: Ignore duplicates
  - `folder`: Duplicates folder (if type is move)
- `unknown`: Same as duplicates, just for unknown files

#### Paths
Configured paths will be injected into the tagsystem with the given name
Will also be used to find the correct file for the kodi watcher.
```yaml
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


#### Run
If this block is present, you can run the configured task with `run -r <config-file-path>`.

example (Scan folder every 45 minutes):
```yaml
run:
  tasks:
    - watch
  args:
    path: /newfiles/
    interval: 45
```
- `tasks`: List of tasks to run, possible values:
  - `watch`: Watch folder
  - `scan`: Scan folder
  - `kodi`: Connect to kodi and mark watched files as watched on anidb
- `args`: Arguments for the tasks, Key-Value pair with the task option as key and the value as value, e.g.
  - `path: /newfiles/`: Path to watch or scan
  - `path-filter: anime`: Filter for the kodi task
  - `interval: 60`: Interval for the watch task

