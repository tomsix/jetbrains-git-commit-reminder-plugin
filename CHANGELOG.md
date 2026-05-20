<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Commit Reminder Changelog

## [Unreleased]

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
