# Open findings on feature/run-configs

From an adversarial review of `git diff feature/kodi-library-scan...HEAD`, 2026-09-22. Two critical findings were fixed on the branch; what follows is what is left. Each entry says what is wrong, where, why it matters, and what fixing it looks like, so it can be picked up cold.

## Already fixed

- **`sandboxReset` deleted through symlinks.** Kotlin's `File.deleteRecursively` follows a directory link, and the sandbox run configurations invoke `sandboxReset` before launch, so a sandbox folder pointed at real media meant a click of Run erased it. Now refuses anything resolving outside the sandbox and deletes with `walkFileTree`, which unlinks rather than follows. Reproduced before and after.
- **`file.mylist.add: false` did not gate the Kodi tasks.** `MarkFileAsWatched` forced its own config. Honouring `mylist.add` there would have broken the deployed Kodi sync, which sets it false and still wants watches recorded, so the decision got its own key: `kodi.markWatched`, default true, false in the sandbox.

## Warnings

### 1. `setupCheck` gates less than it claims

It reads only `.run/sandbox.yaml` (`gradle/sandbox.gradle.kts`, the block near the end of `setupCheck`). Two ways around it:

- `exitOnBan` is also a CLI option (`AnidbCommand.java:61-63`) and the CLI wins over config (`MapConfigValidator.java:34-40`), so a run file carrying `args: {exit-on-ban: false}` passes the check and every related test. `noRunFileCarriesItsOwnSettings` only inspects top-level keys.
- A `sandbox-*.yaml` whose `config:` is redirected at `../config/docker.yaml` (which has `mylist.add: true`) also passes, and nothing runs tests before an IDE launch.

`mylist.add` itself has no CLI option, so that half holds. **Fix:** have `setupCheck` load each `sandbox-*.yaml`, assert `config` resolves to `sandbox.yaml`, and reject `exit-on-ban` and `db` appearing in `args`. Highest value of the four: a gate that can be stepped around is worse than no gate, because it is trusted.

### 2. `run.args.db` bypasses path resolution

`--db` is `Path`-typed (`AnidbCommand.java:66-68`) but `RunConfig.toCommandArgs` resolves only `path` and `config`. So `args: {db: ../aniAdd.sqlite}` resolves against the working directory, and IntelliJ and a shell in `.run/` would use different cache files. `docs/WorktreeSetup.md` claims the rule covers the whole run block, which is wrong as written.

No tracked config sets it today, which is the only reason this is a warning. **Fix:** resolve any arg whose target option is `Path`-typed, or narrow the documented claim and reject `db` in `args` as part of the previous item.

### 3. Docker cache path moves on upgrade

`config/docker.yaml:10` says `db: aniAdd.sqlite`, relative, and the image sets no `WORKDIR`. Under the old working-directory rule that landed at `/aniAdd.sqlite`; it now resolves beside wherever the config is mounted. A deployment that never set `LOCAL_CACHE_FILE` therefore starts from an empty cache after upgrading, which means re-looking-up every file: exactly the AniDB traffic the rest of this work exists to avoid. With a read-only config mount the sqlite create may fail outright at startup, which is **unverified**.

The reference deployment sets `/cache/aniAdd.sqlite` absolutely and is unaffected. **Fix:** make `config/docker.yaml` name an absolute container path, and say in the Docker section that a relative `db` now follows the config file.

Related: `.run/docker-*.yaml` say `config: ../config/docker.yaml` under a header advising you to mount the run file and the settings file together. That only works if the directory they are mounted into is literally named `config`, since `/x/../config/docker.yaml` normalises to `/config/docker.yaml`. Either say "mount at `/config`" or go back to a sibling reference.

### 4. A plain clone silently loses the shared cache

`sandboxInit` skips the cache link outside the worktree layout, so `../sandbox/aniAdd.sqlite` becomes an empty database in the checkout while the Readme says the sandbox shares the ordinary cache. `setupCheck` refuses to run in a plain clone, so nothing says otherwise.

Worse, `.run/scan-local.yaml` and `.run/scan-inplace.yaml` say `db: ../../aniAdd.sqlite`, which in a plain clone creates a sqlite file in the clone's **parent directory**, outside the project entirely. `SandboxConfigTest` asserts exactly that, enshrining it. **Fix:** decide what the cache path means without a container, then make the configs, the docs and the test agree.

## Smaller things

- **Nested worktrees do not work**, despite `docs/WorktreeSetup.md` claiming a deeper worktree "still resolves correctly". Every link target is a hardcoded `../`, so a worktree at `container/group/name` gets broken links and can never pass `setupCheck`. Either support it or drop the claim.
- **Readme examples do not match the repo.** It cites `/anime/sorting/anime` twice, which appears in no tracked config; `config/docker.yaml` uses `/from/anidb`, `/unknown` and `/duplicates`. The example came from a real deployment rather than from the template in this repository.
- **`docs/WorktreeSetup.md` still says `sandboxInit` "generates its config"**, left over from before the configs became tracked. The script header and the skill both say nothing is generated.
- **`envLink -Pall` handles a pruned worktree badly.** `worktreePaths()` does not filter prunable entries, so a registered-but-deleted worktree throws `NoSuchFileException` after earlier worktrees were already linked. Re-running fixes it, but the failure is a stack trace rather than a message.
- **`envLink` without `-Pall` still recreates the container-level `library` link**, a container-wide effect from an invocation that reads as "this worktree only".
- **Three tests assert less than they appear to.** `everySettingsFileSharesTheOneTagSystem` compares the tag file with itself, so a corrupt tag system passes; `RunConfigTest.configArg` exercises the nonexistent-file fallback in `baseDirectoryOf` rather than the resolution rule, because its fixtures are classpath resources; `TagSystemFileTest` has a statement whose result is discarded.
- **A tag system file is read once and cached**, so in `watch` mode an edit takes effect only on restart. Same as an inline tag system, but naming a file invites the opposite expectation. Worth a line in the docs rather than a code change.
