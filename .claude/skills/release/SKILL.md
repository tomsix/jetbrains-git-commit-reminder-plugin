---
name: release
description: Create a GitHub release for this JetBrains plugin. Use when the user asks to "create a release", "publish a release", "release X.Y.Z", or "tag a release".
---

# Release a new plugin version

Cuts a GitHub release for the version currently set in `gradle.properties`, using the matching CHANGELOG section as the release notes.

## Conventions to preserve

- **Tag** is prefixed with `v` (e.g. `v1.0.0`, `v1.0.0-alpha.4`).
- **Release title** is NOT prefixed with `v` (e.g. `1.0.0`, `1.0.0-alpha.4`). This is intentional and visible on the releases page — do not add a `v`.
- **Prerelease flag**: pass `--prerelease` if the version contains `-alpha`, `-beta`, or `-rc`. Stable versions (plain `X.Y.Z`) are not prereleases.
- **Release notes** come from the matching section of `CHANGELOG.md`, followed by a `Full Changelog:` link pointing to the anchor in the repo's CHANGELOG (anchor = version with dots/dashes removed, e.g. `100`, `100-alpha4`).

## Steps

1. Read the current version from `gradle.properties` (the `version=` line).
2. Confirm the working tree is clean and `HEAD` is on `main` and matches `origin/main`. If not, surface the issue and stop — do not create a release from a dirty or unsynced tree.
3. Open `CHANGELOG.md` and extract the section for the current version (between `## [X.Y.Z]` and the next `## [` heading). If the section is missing or empty, stop and ask the user to update the changelog first.
4. Check `gh release list` to confirm a release for this tag doesn't already exist.
5. Look at the most recent release with `gh release view <previous-tag>` to confirm the title/notes format hasn't drifted from these conventions.
6. Create the release:

   ```bash
   gh release create vX.Y.Z \
     --title "X.Y.Z" \
     [--prerelease] \
     --notes "$(cat <<'EOF'
   ## [X.Y.Z]

   <changelog body for this version>

   Full Changelog: https://github.com/tomsix/jetbrains-git-commit-reminder-plugin/blob/main/CHANGELOG.md#<anchor>
   EOF
   )"
   ```

7. Report the release URL returned by `gh`.

## Notes

- `gh release create` creates the git tag on the remote — no need to `git tag` and `git push --tags` separately.
- Do not push, force-push, or modify branches as part of this skill. If the user's local `main` is ahead of `origin/main`, stop and ask before pushing.
- Untracked `.DS_Store` files in the working tree are fine to ignore for the cleanliness check.