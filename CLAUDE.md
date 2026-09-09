# AniAdd CLI

Headless CLI fork of the old AniDB Java applet (GUI removed at v1.1.1). Scans or watches folders for anime video files, ed2k-hashes them, identifies them against the AniDB UDP API, renames/moves them via a tag-system DSL, adds them to the user's MyList, and can mark episodes watched from Kodi playback events. Built to run on a NAS, usually as the Docker image `tyderion/aniadd-cli`.

## Toolchain — JDK 21 only, preview features required

- The code uses **Java 21 preview features, including String templates** (`STR."..."`), which were removed from later JDKs. It compiles and runs ONLY on JDK 21 with `--enable-preview`. The system default here is Java 26 and will fail; `jdk21-openjdk` is installed at `/usr/lib/jvm/java-21-openjdk`.
- Build with the wrapper (Gradle 8.8): `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build`
- Run the jar with `java --enable-preview -jar ...` — the flag is needed at runtime too (the `.run/*.sh` scripts all pass it).
- Lombok everywhere (`@Slf4j`, `@Getter`, `val`) via the freefair plugin; slf4j-simple for logging, levels overridable via a properties file named by env `LOG_CONFIG_FILE`.

## Commands

- Test: `./gradlew test` (JUnit 5 + vintage engine, Mockito, Hamcrest; only `ParseReplyTest` and `RunConfigTest` exist — coverage is thin, don't assume tests catch regressions)
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
- `processing/` — `EpisodeProcessing` orchestrates per-file state (`FileInfo`, `MultiKeyDict` keyed by id+path): FILE lookup → tag-system evaluation → rename/move → MYLISTADD. `processing/tagsystem/TagSystem` is the renaming DSL (examples: `config/tagging-system*.txt`, docs in Readme).
- `fileprocessor/` (directory scanning), `ed2kHasher/` (ed2k/MD4 hashing), `cache/` (Hibernate + SQLite cache of AniDB file data, `AniDBFileData`), `aniAdd/kodi/` (Kodi JSON-RPC over WebSocket, marks watched episodes).

## Conventions & gotchas

- Line endings: `.gitattributes` enforces LF everywhere except `*.bat`/`*.cmd`/`*.ps1` (CRLF). The repo history predates this (developed on Windows); do not "fix" ending-only diffs in old commits and never commit CRLF text files.
- A local pre-commit hook (`.git/hooks/pre-commit`, source in `hooks/`) auto-unstages `TestCommand.java` and rejects commits referencing `TestCommand` — that class is scratch/debug tooling, keep it out of commits.
- `.run/` holds both the IntelliJ-style run yamls consumed by the `run` command and the shell entrypoints (`watch.sh`, `scan.sh`, `kodi.sh`, ...) that get baked into the Docker image; changing script names means updating `prepareForRelease` and `createDockerfile` in `build.gradle.kts`.
- `combined.log` / `error.log` are runtime output, never commit them.
- Branch names follow `feature/<topic>`; current work happens off `master` and releases are tagged from `master` only.
