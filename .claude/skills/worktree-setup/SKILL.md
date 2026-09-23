---
name: worktree-setup
description: Add a git worktree to this repository and wire it into the shared development setup (credentials, AniDB cache, sandbox) using the project's Gradle setup tasks. USE WHEN adding a worktree, working on two branches at once, a new checkout is missing .env or alt.env, a run configuration fails because a path or the sandbox does not exist, setupCheck reports problems, or converting a plain clone to the .bare container layout. NOT FOR ordinary branch switching in a single checkout.
---

# Worktree setup

This repository is normally developed as a `.bare` container with one worktree per branch. Everything that is not source lives once in the container and is reached from each worktree through relative symlinks: `.env`, the alternate-account env file, the shared AniDB cache, the media library, and the `sandbox` directory.

`docs/WorktreeSetup.md` describes the layout, the account switch and the sandbox in full. Read it when the question is "how is this arranged"; this skill is for "make me another worktree".

## Adding a worktree

Done means: the new directory exists on the intended branch, `.env` and `alt.env` resolve inside it, `sandbox` points at the shared one, and `setupCheck` passes.

```bash
cd <container>                                  # the directory holding .bare
git worktree add <dir> <existing-branch>
git worktree add -b <new-branch> <dir> <base>   # or start a branch

cd <dir>
./gradlew envLink        # .env and alt.env
./gradlew sandboxInit    # sandbox directories, and the link into every worktree
./gradlew setupCheck     # confirms the result, fails if anything is missing
```

Every one of these needs `JAVA_HOME` pointing at a JDK 21 (see `CLAUDE.md`). `sandboxInit` links the sandbox into all worktrees, so running it once after adding a checkout is enough.

## The tasks

| Task | Does |
|---|---|
| `envLink` | Symlinks `.env`, the shared cache, `alt.env` for the account named by `ADDITIONAL_ENV`, and `library` from `LIBRARY_ROOT`. `-Pall` does every worktree, which is how you switch accounts everywhere at once. |
| `sandboxInit` | Creates the sandbox tree and links `sandbox` into each worktree. Safe to re-run. |
| `sandboxReset` | Refills `sandbox/input/` from `sandbox/media/` and empties the output folders. Run before a test, because a scan moves its own input. |
| `sandboxGuard` | Refuses if any sandbox run configuration could read or write outside the sandbox. It is the before-launch step of `Sandbox Kodi`; the others get the same check through `sandboxReset`. |
| `setupCheck` | Reports missing links, missing directories, and a sandbox config whose safety gates have been edited away. Exits non-zero. |

## Gotchas

- **Sandbox runs are confined, and that is enforced, not documented.** Every configuration in the Sandbox folder must run a `.run/sandbox-*.yaml` entry point, every entry point must delegate to `.run/sandbox.yaml` and read from the sandbox, and every folder the settings write to must be inside the sandbox. The check runs before launch, so a sandbox configuration pointed at real folders refuses to start. Change what a sandbox run touches by editing the sandbox, never by pointing a Sandbox configuration elsewhere; for a real run use `Local Test` or `Local InPlace`.
- **JDK 21, not the system default.** Gradle 8.8 fails on newer JDKs with a message that is only the version number, which reads like a corrupt build rather than a toolchain problem.
- **Never create these symlinks by hand.** The tasks refuse to replace a regular file, so an existing real `.env` in a worktree is reported rather than deleted. Doing it manually loses that.
- **Nothing is generated.** Every config is tracked in `.run/`; a new worktree gets them from git. Copying configs between worktrees means two copies that drift.
- **`ADDITIONAL_ENV` lives in the container `.env`**, names a bare filename next to it, and decides what `alt.env` points at everywhere. Changing it needs `./gradlew envLink -Pall` to take effect. If it is unset there is no `alt.env` at all, deliberately: a silent fallback to the primary account would send test runs to the real MyList.
- **A branch checked out in one worktree cannot be checked out in another.** Start a new branch with `-b`, or switch the other worktree first.
- **Relocating a worktree uses `git worktree move`, never `mv`.** A plain rename leaves git's worktree registry pointing at the old path.
- **`envLink` and `setupCheck` refuse to run in a plain clone**, where there is no container and nothing to share. `sandboxInit` and `sandboxReset` still work and build the sandbox inside the checkout.
- **Paths in config files resolve against the config file, not the working directory.** A config that looks wrong from where you are standing is usually right; check what it is relative to before editing it.
