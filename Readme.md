# AniAdd

Forked from [AniDB](https://github.com/svn2github/AniDB)
Thanks for Anidb team for publishing the source of this java applet.

Big thanks to [@runecalico](https://github.com/runecalico) for testing and providing feedback.

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

I suggest using the watch command to monitor your download folder and automatically add new anime to your mylist and move them to your anime folder.

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

# Tagging System
Use a small DSL to compute the new names of your files. Here is two similar examples example: [Kodi-Conf](config/tagging-system.conf) or [Other-Conf](config/tagging-system.conf)

# Logging Configuration

The logging configuration is done via a properties file. You can specify the path to the file via the `LOG_CONFIG_FILE` environment variable.
Here is a sample override with the default log levels: [logging.override.properties](config/logging.override.properties);

# Transcoding

Optional, off by default. Turn `transcode.enabled` on to re-encode matching video into a single output format while audio, subtitles, attachments and chapters are copied through untouched.

Files are identified on AniDB **before** they are converted, so an unidentified file is never encoded and still lands in the unknown folder in its original format. Once a converted file passes verification, its ed2k hash and size are mapped back to the original's in the sqlite cache (`FileHashMapping`). Everything downstream keeps using the original pair, so AniDB lookups, MyList and the cache behave exactly as they did before the file changed format, and a converted file is recognised again on any later run no matter what it is called.

Because a locally produced file is no longer the release AniDB describes, its `%FVCodec%` and `%FCrc%` tags come from the file on disk rather than from AniDB, so tag-system names stay honest. That override applies on every later run too, since the mapping is what marks a file as locally produced. Nothing else about identity changes: `%FCrc%` is a real CRC32 of the new file, computed in the same read as the ed2k hash.

Verification before a source file is touched: ffmpeg exits 0, the duration still matches within `durationToleranceSeconds`, the output is at least `minSizeRatio` of the source, and video, audio, subtitle and attachment stream counts all match. Anything short of that deletes the temporary file and leaves the source exactly where it was.

Encodes run one at a time on their own thread, so a long encode never blocks hashing or the AniDB session. `ffmpeg` and `ffprobe` come from the docker image; outside docker, set `transcode.ffmpegPath` and `transcode.ffprobePath` or have both on the PATH.

Two ways to run it:

- **As part of the normal pipeline**: enable it in the config and run `scan` or `watch` as usual. New downloads are converted after they are identified.
- **As a pass over an existing library**: `anidb transcode <folder>`, or `run` with `task: transcode`. Identical to `scan` except transcoding is on regardless of `transcode.enabled`, so pointing it at `series/` needs no config edit.

Config reference: the `transcode:` block in [docker.yaml](config/docker.yaml). The shipped `videoArgs` default is x265 CRF 24, preset slow, 10-bit, with the usual anime parameters. It was picked by measurement on a grainy 1080p Blu-ray opening: CRF 18 through 22 came out near-lossless and *larger* than the source, CRF 24 lands at 83% of the source size at VMAF 98.97 against it, and CRF 30 reaches 41% at VMAF 96.26. CAMBI found no banding differences across that whole range. Expect roughly 0.33x realtime at preset slow, so about an hour per 24-minute episode on a fast desktop and longer on a NAS.

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
- `ANIDB_CONF` [required]: path to your config file, [example](config/docker.conf), needs a corresponding mounted location of course :)
- `LOG_CONFIG_FILE`: Path to a properties file for logging [sample](config/logging.override.properties). Will be used to update logging configuration and set log levels. Defaults to the linked file.

### Scanning and watching 
entrypoint: `/app/scan.sh` (default command of docker image)

entrypoint: `/app/watch.sh` 

Runs either `scan` or `watch` command.

#### mounts

The container never contains a host path: config files inside it use container paths like `/from/anidb` and `/cache/aniAdd.sqlite`, and the mounts supply the real locations. Local development mirrors this with a `library` symlink driven by `LIBRARY_ROOT`, so the same config shape works in both places and nothing tracked names a specific machine.

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

Runs the `watch` command and enables kodi command as well.


### Noop
entrypoint: `/app/noop.sh`

Keeps container alive, so you can connect a shell and run commands manually.

### Flexible Run Configuration
entrypoint: `/app/run.sh` 

Enables you to run run config file stored in `$ANIDB_CONF`

#### Env Vars
- `$ANIDB_CONF` [required]: the run config file to run, e.g. run.yaml

# Local Development

I recommend IntelliJ (Community Edition is enough). Install the Lombok plugin and enable annotation processing. The project builds only on **Java 21** — higher and lower both fail, because of the template strings. If you want them, install the git hooks with `./hooks/install.ps1` (Windows) or `./hooks/install.sh` (Linux/macOS).

## Credentials

Put them in a `.env` in the project root, not in a config file. A password in a run block is rejected outright, and the tracked configs are shared, so a credential in one is a credential in everyone's checkout.

```
ANIDB_USERNAME=...
ANIDB_PASSWORD=...
TVDB_APIKEY=...
TMDB_ACCESS_TOKEN=...
```

The run configurations read it through IntelliJ's env-file support. A second account can live in another file and be reached as `alt.env`; see [docs/WorktreeSetup.md](docs/WorktreeSetup.md).

## The sandbox

A local folder tree to run against, so you are never pointing a work-in-progress build at your real library:

```bash
./gradlew sandboxInit     # creates sandbox/ with input, output, unknown, duplicates and media
# copy a few real anime files into sandbox/media/
./gradlew sandboxReset    # fills input/ from media/ and clears the rest
```

Then run the **Sandbox scan** configuration. It runs `sandboxReset` for you as a before-launch task, so every run starts from the same input; the watch configurations do the same. Note that this clears `output/`, so look at the results before starting the next run. **Sandbox Kodi** is left alone, since it has no input folder to refill.

Use real files. AniDB identifies a file by its ed2k hash, so invented ones cannot be identified, and a run full of failed lookups is the quickest way to get your account banned. For the same reason the sandbox shares the ordinary lookup cache rather than starting an empty one, through `../aniAdd.sqlite`, which is the shared file in a worktree setup and an ordinary one in a plain clone. `sandbox/` is ignored by git; the configs that describe it are not.

## Your own library

`scan-local.yaml` and `scan-inplace.yaml` work on a real library rather than the sandbox. They say `../library/input/`, and `library` is a symlink to wherever yours actually lives. Set it once in the shared `.env`:

```
LIBRARY_ROOT=/path/to/your/anime
```

then `./gradlew envLink` creates the link. The tracked configs stay free of anyone's machine layout, and the only place your path appears is an untracked file.

This is the same trick the Docker image uses, one level down: the container config says `/from/anidb` and the host path is supplied by a bind mount. A symlink for local runs, a mount for the container, generic paths in both.

## Config files

Run configurations live in `.run/` and are tracked, so they arrive with a clone and show up in a diff when they change.

A config splits into two layers. A **settings** file describes where things go: paths, the cache, the tag system, and the mylist, Kodi and ban options. A **run** file carries only a `run:` block saying which task to start, and delegates the rest with `config: <settings file>`. `sandbox.yaml` and the `sandbox-*.yaml` files beside it are that pair.

`anidb.cache.db` is required, and a config whose relative `db` now points somewhere new while the old location still has a cache is refused with the line to set, rather than silently starting on an empty one. It used to default to `aniAdd.sqlite` in whatever directory the command ran from, so the same config could open a different cache from the IDE than from a shell; starting from an empty cache means looking every file up again, which risks a ban. `config new` writes one for you.

`file.move.confineTo` restricts a run to one folder: nothing outside it is moved, renamed or deleted, and a scan outside it is refused. The sandbox sets it.

Two rules make those files portable:

- **Paths are relative to the file they are written in**, never to the working directory, the same way compose files and tsconfig behave. Absolute paths are used as written. So a config means the same thing launched from the IDE, from a shell, or from a container, and symlinks are followed first.
- **A tag system can live in its own file**, shared by every config that needs it, with `tags.tagSystemFile: <path>` instead of fifty pasted lines each. An inline `tags.tagSystem` still wins if both are given.

## Several branches at once

The project is usually developed as a bare repository with one worktree per branch, sharing a single `.env`, cache and sandbox. [docs/WorktreeSetup.md](docs/WorktreeSetup.md) has the layout and the setup tasks; `./gradlew setupCheck` verifies a checkout is wired up correctly.

# DISCLAIMER
This software is provided as is. I am not responsible for any damage caused by this software. Use at your own risk.