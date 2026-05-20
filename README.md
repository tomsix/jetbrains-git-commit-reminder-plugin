# Git Commit Reminder

[![Tests](https://github.com/tomsix/jetbrains-git-commit-reminder-plugin/actions/workflows/test.yml/badge.svg?branch=main)](https://github.com/tomsix/jetbrains-git-commit-reminder-plugin/actions/workflows/test.yml)
[![Plugin Verification](https://github.com/tomsix/jetbrains-git-commit-reminder-plugin/actions/workflows/verify.yml/badge.svg?branch=main)](https://github.com/tomsix/jetbrains-git-commit-reminder-plugin/actions/workflows/verify.yml)

A JetBrains IDE plugin that warns you before closing a project (or quitting the IDE) when there are still uncommitted changes or unpushed commits in any of the project's Git repositories.

## What it does

When you close a project, Git Commit Reminder checks every Git repository in that project and, if anything is unfinished, shows a confirmation dialog so you can decide what to do.

It detects two states:

- **Uncommitted changes** — modified, added, or deleted files in your working tree (anything `ChangeListManager` would show in the Local Changes view).
- **Unpushed commits** — local commits that are not on the upstream branch, or commits on a branch that has no upstream configured.

Depending on what's detected, the dialog offers contextual actions:

| Button | Shown when | Effect |
|---|---|---|
| **Close Anyway** | Always | Close the project, ignoring the warning. |
| **Close All** | Multiple projects are open | Close this project *and* every other open project. |
| **Cancel** | Always | Keep the project open. |
| **Commit Files** | There are uncommitted changes | Cancel the close and open the Commit dialog. |
| **Push** | There are unpushed commits | Cancel the close and open the Push dialog. |

## Install

### From JetBrains Marketplace

Search for **Git Commit Reminder** in **Settings → Plugins → Marketplace**, or install from the [Marketplace listing](https://plugins.jetbrains.com).

### From disk

1. Grab the latest ZIP from the [releases page](#) (or build it locally with `./gradlew buildPlugin`).
2. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and select the ZIP.

## Requirements

- Any JetBrains IDE based on IntelliJ Platform 2026.1+ (IntelliJ IDEA, PyCharm, WebStorm, PhpStorm, Rider, GoLand, …).
- The bundled **Git** plugin must be enabled (it is by default).

## Building from source

```bash
./gradlew buildPlugin       # produces build/distributions/*.zip
./gradlew runIde            # launches a sandbox IDE with the plugin loaded
./gradlew test              # runs the unit tests
./gradlew verifyPlugin      # verifies against the target IDE versions
```

The `.run/` directory contains matching IDEA run configurations (**Run Plugin**, **Run Tests**, **Run Verifications**).

## License

See [LICENSE.md](LICENSE.md).