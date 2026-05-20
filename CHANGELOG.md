<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Commit Reminder Changelog

## [Unreleased]

## [1.0.0-alpha.3]

### Fixed

- "Close All" now appears only when quitting the IDE. Previously the button showed whenever multiple projects were open, including when closing a single project — which made it look like the button would close every project but it only allowed the current one. The button now appears only during an actual IDE quit (File → Quit / ⌘Q), where it correctly skips the remaining Git Commit Reminder prompts for the other projects being closed in the same batch.

### Internal

- Replaced an `@ApiStatus.Internal` platform API (`isExitInProgress`) with the public `AppLifecycleListener.appWillBeClosed` event. No behaviour change for users; makes the plugin resilient to internal-API removal in future IDE versions.
- Added tests covering the close-all visibility rules, including a regression test for the single-project-close scenario above.
