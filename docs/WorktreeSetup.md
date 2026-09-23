# Working on several branches at once

This project is easier to develop with git worktrees: one checkout per branch, all sharing a single object store, so switching between the Kodi work and the transcode work is `cd`, not `git stash`. This document describes the layout the setup tasks expect, and what they do.

You do not have to use it. A plain clone works exactly as before, and the setup tasks refuse to run in one rather than scattering files around your filesystem.

## The layout

```
AniDb-AniAdd-CLI/            the container: not a checkout, just a folder
├── .bare/                   the repository itself (bare)
├── .git                     a file containing "gitdir: ./.bare"
├── .env                     shared credentials and the paths below, the only copy
├── alt.env targets          e.g. tyd.env, test.env: alternative accounts
├── library                  a link to your real media, from LIBRARY_ROOT
├── aniAdd.sqlite            the shared AniDB lookup cache, linked into each worktree
├── sandbox/                 local test environment, created by sandboxInit
└── kodi/  transcode/  ...   one directory per worktree
```

Everything shared sits in the container, and each worktree reaches it through a relative symlink. Relative, so renaming the container does not break anything.

**The cache is shared on purpose.** AniDB bans accounts that look up too much too fast. One cache across every worktree means a file you have already identified is never looked up again, whichever branch you are working on. A per-worktree cache would quietly undo that.

## Creating the layout

From an existing clone, with no loss of stashes, reflogs or local config:

```bash
cd ~/code
mkdir AniDb-AniAdd-CLI.new
mv AniDb-AniAdd-CLI/.git AniDb-AniAdd-CLI.new/.bare
cd AniDb-AniAdd-CLI.new
git --git-dir=.bare config core.bare true
printf 'gitdir: ./.bare\n' > .git
git worktree add kodi feature/kodi-library-scan
```

Then move any ignored local files (`.env`, `*.yaml`, `*.conf`, the sqlite databases) out of the old checkout, delete it, and rename the container to the name you want. From a fresh start, `git clone --bare <url> .bare` replaces the first three lines, but note that a bare clone sets up no remote-tracking refspec, so run `git config remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*"` and fetch once before anything else.

## Setting it up

Four Gradle tasks, all in the `setup` group, all runnable from any worktree:

| Task | What it does |
|---|---|
| `./gradlew envLink` | Symlinks everything the shared `.env` names into this worktree: `.env` itself, `alt.env` from `ADDITIONAL_ENV`, and `library` from `LIBRARY_ROOT`. Add `-Pall` to do every worktree at once. |
| `./gradlew sandboxInit` | Creates the `sandbox/` directories and, in the worktree layout, links the shared sandbox into every worktree. It generates nothing: the configs are tracked in `.run/`. |
| `./gradlew sandboxReset` | Refills `sandbox/input/` from `sandbox/media/` and empties the output folders, so a test run is repeatable. |
| `./gradlew setupCheck` | Reports anything missing or unsafe and fails if it finds a problem. |

They find the container by asking git for its common directory (`git rev-parse --git-common-dir`) and taking the parent, rather than assuming `..`. Link targets are computed the same way, so a worktree nested a level deeper gets `../../` and still resolves.

## The second account

Run configurations reference `alt.env`, never a specific account's filename. Which account that is comes from the shared `.env`:

```
ADDITIONAL_ENV=tyd.env
```

Point it at a different file and re-run `./gradlew envLink -Pall` to switch every worktree at once. The value must be a bare filename sitting next to `.env` in the container; `setupCheck` rejects anything with a path separator in it.

If `ADDITIONAL_ENV` is unset, no `alt.env` is created anywhere. There is deliberately no fallback to `.env`: a run you believed was using the test account, quietly writing MyList entries to your real one, is worse than a run that fails to start.

## The sandbox

```
sandbox/
├── media/                   real files you supply, left untouched
├── input/                   working copy, refilled by sandboxReset
├── unknown/  duplicates/
└── output/movies/  output/series/
```

The sandbox holds only data. Its configs are tracked in `.run/` and reach it through `../sandbox/`, which `sandboxInit` makes correct in both layouts: in a worktree setup each checkout gets a `sandbox` symlink to the shared one, and in a plain clone the sandbox simply lives in the checkout.

Put real anime files in `media/`. The sandbox deliberately does not generate fake ones: AniDB identifies files by ed2k hash, so invented files are unidentifiable, and a run full of failed lookups is the fastest way to get banned. Transcoding needs real media anyway, and files whose codec actually differs from the configured output, or there is nothing to convert.

`sandboxReset` copies `media/` into `input/` and clears the rest. Run it before each test, because a scan moves its input into `output/`.

It refuses to clear anything that resolves outside the sandbox, and unlinks rather than follows a symlink it finds inside one. The sandbox itself must be a real directory in its own container: a sandbox that is a symlink, or has a different filesystem mounted on it, is refused before anything is touched. Otherwise linking the sandbox at your real library, which has exactly the same folder layout, would make the next reset empty the library. That matters because the sandbox configurations run it automatically before launch, so pointing a sandbox folder at real media would otherwise mean a click of Run erased that folder.

A config file splits in two. `.run/sandbox.yaml` holds the settings and no run block; each `.run/sandbox-<task>.yaml` holds only a run block and delegates with `config: sandbox.yaml`. The `docker-<task>.yaml` files are the same shape for the image, delegating to `config/docker.yaml` instead. That way there is exactly one entry point per task and one place that defines where things go. Adding a task means adding one more `sandbox-<task>.yaml` and a run configuration for it.

All of them are tracked, so they arrive with a clone and show up in a diff when they change. `setupCheck` reads the entry points as well as the settings, rejecting one that delegates somewhere else or sets `exit-on-ban` or `db` in its args, since either would step around a gate. `sandbox.yaml` carries three hard gates, and `setupCheck` re-reads the file to confirm all three are still set: `file.mylist.add: false` so a scan adds nothing, `kodi.markWatched: false` so a play reported by Kodi is observed rather than written back, and `anidb.exitOnBan: true` so a run stops instead of hammering the API.

Every settings file points its cache at `../aniAdd.sqlite`, which `envLink` links to the shared one in the container. The sandbox therefore reuses lookups from real runs instead of starting empty and querying AniDB for every file, and in a plain clone the same path is an ordinary file in the checkout rather than something above it.

None of the settings files carry a tag system of their own: all three reference `config/tagging-system.kodi.txt` through `tags.tagSystemFile`. One definition, no drift. The file is read once and cached, so in `watch` mode an edit takes effect on the next start rather than immediately.

## Paths in config files

A relative path in a config file resolves against that file's own directory, the way compose files and tsconfig behave. It never depends on the working directory, so a config means the same thing launched from IntelliJ, from a shell in a subfolder, or from a container.

This applies to every path-typed setting (`anidb.cache.db`, `file.move.unknown.folder`, `file.move.duplicates.folder`, `tags.paths.*.path`, `kodi.libraryScan.*.localPath`) and to the path-bearing fields of a run block, `run.args.path`, `run.args.db` and `run.config`. Absolute paths are left exactly as written.

Symlinks are followed first, so a config linked into several worktrees behaves as if you had opened the original. That is what lets one `sandbox.yaml` serve every checkout while saying `input/`.

## Run configurations

The configurations in `.run/*.run.xml` are tracked, so every worktree gets them. They read credentials from `.env` via IntelliJ's env-file support and never carry a username or password in their arguments.

| Folder | Configurations |
|---|---|
| Sandbox | `Sandbox scan`, `Sandbox watch`, `Sandbox watch + Kodi`, `Sandbox Kodi`, each running one `sandbox-*.yaml` |
| Local | `Local Test` and `Local InPlace`, which work on real folders rather than the sandbox |
| Setup | the four Gradle tasks |
| (root) | `Gradle build` and `Gradle createDockerfile` |

The sandbox ones need `./gradlew sandboxInit` to have run, because they reference the symlinks it creates.

The Gradle tasks above appear in IntelliJ's Gradle tool window, and `.run/Gradle setupCheck.run.xml` and friends make them one click.
