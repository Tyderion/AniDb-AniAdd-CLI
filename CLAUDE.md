# AniAdd CLI

Headless CLI fork of the old AniDB Java applet (GUI removed at v1.1.1). Scans or watches folders for anime video files, ed2k-hashes them, identifies them against the AniDB UDP API, renames/moves them via a tag-system DSL, adds them to the user's MyList, and can mark episodes watched from Kodi playback events. Built to run on a NAS, usually as the Docker image `tyderion/aniadd-cli`.

## Toolchain — JDK 21 only, preview features required

- The code uses **Java 21 preview features, including String templates** (`STR."..."`), which were removed from later JDKs. It compiles and runs ONLY on JDK 21 with `--enable-preview`. The system default here is Java 26 and will fail; `jdk21-openjdk` is installed at `/usr/lib/jvm/java-21-openjdk`.
- Build with the wrapper (Gradle 8.8): `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build`
- Run the jar with `java --enable-preview -jar ...` — the flag is needed at runtime too (the `.run/*.sh` scripts all pass it).
- Lombok everywhere (`@Slf4j`, `@Getter`, `val`) via the freefair plugin; slf4j-simple for logging, levels overridable via a properties file named by env `LOG_CONFIG_FILE`.

## Worktrees

Development usually happens in a `.bare` container with one worktree per branch, sharing one `.env` and one AniDB cache. `docs/WorktreeSetup.md` has the layout; `.claude/skills/worktree-setup/` has the procedure for adding one. A plain clone works too and needs none of it.

## Commands

- Test: `./gradlew test` (JUnit 5 + params + vintage engine, Mockito, Hamcrest). Config loading, path resolution and the tracked `.run/` configs are covered; the processing pipeline and the AniDB client are not, so don't assume tests catch regressions there.
- Fat jar: `./gradlew fatJar` → `build/libs/aniadd-cli-all-<version>.jar`, main class `startup.Main`
### Docker image (custom tasks, not the plugin defaults)

The bmuschko `docker-java-application` plugin is applied but its auto-generated tasks (`dockerBuildImage`, `dockerPushImage`, `dockerCreateDockerfile`, `dockerSyncBuildContext`) are **deliberately disabled** in `build.gradle.kts` — use the custom ones:

- `./gradlew buildDockerImage` — builds `tyderion/aniadd-cli:<version>` locally. Chain: `fatJar` → `prepareForRelease` (copies the fat jar + `.run/*.sh` + `logging.properties` into `build/docker/`) → `createDockerfile` (generates the Dockerfile: base `amazoncorretto:21.0.3-al2023-headless`, jar at `/app/aniadd-cli.jar`, entrypoint scripts at `/app/*.sh`, default command `/app/noop.sh`) → `buildDockerImage`.
- `./gradlew pushDockerImage` — pushes that image to Docker Hub (needs credentials).
- `./gradlew release` — full release: `pushDockerImage` → `createGitTag` (refuses off `master`) → pushes the git tag. Version lives in `build.gradle.kts` (`version = "..."`); bumping it changes the image tag and the git tag.

## Architecture

Entry point `startup.Main` → picocli `CliCommand` with subcommands: `anidb` (`scan`, `watch`, `connect-to-kodi`/`KodiWatcherCommand`, `watch-and-kodi`), `config` (convert/new), `tags` (test the tag DSL against sample data), `run` (reads a `run:` block from yaml and re-invokes the CLI with generated args — see `.run/*.yaml`), plus `debug`.

- `startup/validation/` — custom config-binding framework: `@MapConfig(configPath=..., envVariableName=...)` fields are filled from the yaml config / env when the CLI option is absent, then validated (`@NonBlank`, `@Port`, `@Min`, `@Max`) by `ConfigValidatingExecutionStrategy` before execution. Precedence: CLI option > env var > config file. **The password is deliberately forbidden in the config file** (`configMustBeNull = true`); keep it that way.
- `config/` + `config/blocks/` — snakeyaml-backed typed config (`RootConfiguration`: `run`, `anidb`, `file`, `kodi`, `tags` blocks). Reference config: `config/docker.yaml`; format docs: `config/Readme.md`.
- `udpapi/` — AniDB UDP protocol client (`UdpApi`): single command in flight, command queue + query map keyed by tag, session login/logout, reply parsing in `receive`/`reply`/`query`. AniDB rate-limits and **bans aggressive clients** (`exitOnBan` config) — never loop real API calls in testing; use the `debug` subcommands (fake files, canned responses) instead.
- `processing/` — `EpisodeProcessing` orchestrates per-file state (`FileInfo`, `MultiKeyDict` keyed by id+path) through the `FileAction` steps: `HashFile` → `FileCmd` (AniDB lookup) → tag-system evaluation → `Rename` → `MyListAddCmd` → `LoadWatchedState` → `GenerateKodiMetadata`. `processing/tagsystem/TagSystem` is the renaming DSL (examples: `config/tagging-system*.txt`, docs in Readme).
- `kodi/` (this branch, `feature/kodi-nfo`) — generates Kodi `.nfo` metadata files plus artwork for processed files; see the next section.
- `kodi/library/` (`feature/kodi-library-scan`) — at the end of each directory scan run, `KodiLibraryScanner` asks Kodi to scan the folders that received files (`kodi.libraryScan`, default off). `EpisodeProcessing` marks a `FileInfo` `libraryChanged` when it was actually moved or got an NFO that did not exist before; `FileProcessor.Scan` hands its files over as one run (`addScanRun`), and `addScanRunFinishedListener` fires once when every file of that run has finished. Files added one at a time (`addFiles` with a config, e.g. Kodi watched marks) belong to no run and never trigger a scan. `AniAdd` waits for `lastScan()` before shutting down a `scan` command. `LibraryScanPlanner` (pure, unit-tested) maps local paths to Kodi paths per `localPath`; libraries given by `name` are resolved via `Files.GetSources`. With `kodi.libraryScan.clean`, each scanned library root is then cleaned via `VideoLibrary.Clean` with its configured `content` (per-show cleans are a silent no-op in Kodi, and a wrong `content` is too, hence the startup validation). Scans go through `aniAdd/kodi/KodiRpcClient` (id-matched replies, waits for `VideoLibrary.OnScanFinished` between scans) on a fresh websocket connection per run, separate from the watched-state subscriber.
- `fileprocessor/` (directory scanning), `ed2kHasher/` (ed2k/MD4 hashing), `cache/` (Hibernate + SQLite cache of AniDB file data, `AniDBFileData`), `aniAdd/kodi/` (Kodi JSON-RPC over WebSocket, marks watched episodes).

## Kodi NFO generation (`feature/kodi-nfo` branch)

`KodiMetadataGenerator` runs as the final `GenerateKodiMetadata` pipeline step (gated by config `kodi.metadata.generate`, default off) and merges three metadata sources per anime:

1. **AniDB HTTP API** (`api.anidb.net:9001/httpapi`, parsed by `AnimeDetailsLoader` via StAX) — authoritative details, titles, episodes, characters/actors. Responses are cached in the sqlite db (`AnimeXml` entity, `AnimeXmlRepository`), expiring after `anidb.cache.ttlInDays` (same knob as the file cache, default 30) — so the cache lives and moves with `anidb.cache.db` in both local and Docker runs. This API bans abusers just like the UDP one.
2. **Anime-Lists mapping** (`anime-list.xml` from the Anime-Lists GitHub repo, `anime_mapping/`) — maps AniDB anime ids → TVDB series ids / TMDB movie ids; URL overridable via `kodi.metadata.animeMappingUrl`. Cached in the sqlite db (`AnimeMappingXml` entity keyed by source URL, `AnimeMappingRepository`), same `anidb.cache.ttlInDays` knob; a still-fresh legacy CWD `anime-list.xml` is read once as a seed, and a failed refresh falls back to the stale db row with a warning. TMDB-only `<mapping>` elements (no `tvdbseason`) are skipped.
3. **TVDB v4** (`tvdb/`, needs `kodi.metadata.tvDbApiKey`) for series/episode data and artwork, or **TMDB** (`tmdb/`, needs `kodi.metadata.tmDbApiToken`) for movies — both via Retrofit + OkHttp, async callbacks on the shared executor.

Output, written next to the moved video file by `SeriesNfoWriter`/`MovieNfoWriter` (dom4j, XML with CDATA plots): `tvshow.nfo` per series folder, `<video-basename>.nfo` per episode or movie, plus downloaded artwork — `<basename>-thumb.jpg`, `fanart[N].*` (max 10), `poster.jpg`, `banner.jpg`, and actor images under `.actors/`. `AniDB anime type` decides the path: TV series → TVDB route (one cached `SeriesNfoWriter` per anime id), movie → TMDB route, anything else → series route without TVDB data.

Overwrite behavior comes from `kodi.metadata.overwrite` (`series`, `episodes`, `movies`, `artwork`, all default false, so existing files are kept), passed from `AnidbCommand` into the generator as `KodiMetadataConfig.OverwriteConfig`; watched state from MyList lands in the episode NFO as `lastplayed` when `kodi.metadata.syncWatchedStateFromMylist` is on. Config lives under the `kodi.metadata:` block (`config/blocks/KodiMetadataConfig.java`); API credentials can also come from CLI (`--tvDbApiKey`, `--tmDbApiToken`) or env via the usual `@MapConfig` precedence.

## Conventions & gotchas

- Line endings: `.gitattributes` enforces LF everywhere except `*.bat`/`*.cmd`/`*.ps1` (CRLF). The repo history predates this (developed on Windows); do not "fix" ending-only diffs in old commits and never commit CRLF text files.
- A local pre-commit hook (`.git/hooks/pre-commit`, source in `hooks/`) auto-unstages `TestCommand.java` and rejects commits referencing `TestCommand` — that class is scratch/debug tooling, keep it out of commits.
- `.run/` holds both the IntelliJ-style run yamls consumed by the `run` command and the shell entrypoints (`watch.sh`, `scan.sh`, `kodi.sh`, ...) that get baked into the Docker image; changing script names means updating `prepareForRelease` and `createDockerfile` in `build.gradle.kts`.
- `combined.log` / `error.log` are runtime output, never commit them.
- Branch names follow `feature/<topic>`; current work happens off `master` and releases are tagged from `master` only.
