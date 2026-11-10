<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Commit Reminder Changelog

## [Unreleased]

### Fixed

- Compatibility with 2026.3 IDEs. 2026.3 moved the VCS APIs the plugin uses (`ChangeListManager`, `com.intellij.dvcs.repo`) into a separate bundled plugin, which caused `NoClassDefFoundError`. The plugin now declares an optional dependency on `com.intellij.platform.vcs`, so older IDEs (2025.3–2026.2) keep working unchanged.
- A branch that was only *behind* its remote (for example after a background fetch) was reported as having unpushed commits, with "0 commits ahead". The plugin now counts the commits ahead and only warns when there are any.
- Without a configured upstream, the plugin compared against the first same-named remote branch it found, which could be `upstream/main` in a fork. It now prefers `origin`, and a branch that matches any remote branch exactly counts as pushed.
- Counting unpushed commits ran `git` on the UI thread while closing the project, which could freeze the IDE. It now runs in the background behind a progress dialog.
- "Close All" disappeared from the next project's dialog when the previous dialog was left open for more than five seconds, and could stop skipping the remaining projects partway through a quit.
- Projects closed right after opening, before the startup activity had run, were not checked.

### Internal

- Bumped the IntelliJ Platform Gradle Plugin to 2.20.1; `verifyPlugin` now passes on IntelliJ IDEA, PhpStorm and WebStorm 2025.3 through 2026.3.
- The close check is registered as a `projectCloseHandler` extension instead of a listener added from a startup activity, so it is removed cleanly when the plugin is unloaded.
- Renamed the message bundle to `GitGuardBundle` and moved it into its package directory.
- Raised the plugin verification CI timeout to 60 minutes for the larger IDE matrix.

## [1.0.0]

First stable release. No functional changes since `1.0.0-alpha.4`.

## [1.0.0-alpha.4]

### Fixed

- A branch pushed without `git push -u` (no upstream tracking configured in `.git/config`) was incorrectly reported as having unpushed commits, even when the matching remote branch already had those commits. The plugin now falls back to comparing against a same-named remote ref (e.g. local `main` ↔ `origin/main`) when no upstream is configured.

### Added

- The close dialog now shows which remote branch was compared against and how many commits are ahead, with one bullet per repository (e.g. `• main is 3 commits ahead of origin/main`). Branches that have never been pushed to any remote are called out explicitly.

### Internal

- `GitGuardDialog.decideOutgoing` simplified: dropped the `upstreamConfigured` parameter and let the caller resolve the best available remote hash. Added `GitGuardDialog.OutgoingInfo` and a pure `renderMessage` helper for the new dialog body.
- Added tests for the same-named-remote fallback and the new message rendering (singular/plural, not-pushed, multi-repo, uncommitted-only).

## [1.0.0-alpha.3]

### Fixed

- "Close All" now appears only when quitting the IDE. Previously the button showed whenever multiple projects were open, including when closing a single project — which made it look like the button would close every project but it only allowed the current one. The button now appears only during an actual IDE quit (File → Quit / ⌘Q), where it correctly skips the remaining Git Commit Reminder prompts for the other projects being closed in the same batch.

### Internal

- Replaced an `@ApiStatus.Internal` platform API (`isExitInProgress`) with the public `AppLifecycleListener.appWillBeClosed` event. No behaviour change for users; makes the plugin resilient to internal-API removal in future IDE versions.
- Added tests covering the close-all visibility rules, including a regression test for the single-project-close scenario above.
