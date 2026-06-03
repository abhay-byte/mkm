---
description: How to prepare and publish a new MKM release
---

# Release Workflow

## HARD RULE — Commit Identity

```
APK commit == tag commit == release commit == HEAD
```

All four MUST resolve to the same hash. No drift, no gap. If any is different, delete the tag/release and redo from the correct commit.

## Commit Hash Location

The APK embeds the git commit hash at:

```
META-INF/version-control-info.textproto
```

Extract it with:

```bash
unzip -p <APK> META-INF/version-control-info.textproto | grep revision
```

This hash MUST match HEAD. If it doesn't, the APK is stale — run `./gradlew clean assembleRelease` to rebuild.

## Prerequisites

- Version code and version name updated in `app/build.gradle.kts`
- Tree must be clean — all changes committed and pushed BEFORE building

## Steps

### 1. Clean Working Tree

Ensure no uncommitted or untracked files. If any exist, stage, commit, and push.

```bash
git status
# If dirty:
git add -A
git commit -m "chore: cleanup before release v<VERSION_NAME>"
git push origin main
```

### 2. Clean Build Release APK

Always clean build to ensure version-control-info.textproto is fresh.

```bash
./gradlew clean assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

### 3. Verify APK Commit — EXTRACT FROM APK (MANDATORY)

Extract the commit hash embedded in the APK and compare with HEAD. If they don't match, rebuild.

```bash
HEAD=$(git log -1 --format="%H")
APK_COMMIT=$(unzip -p app/build/outputs/apk/release/app-release.apk META-INF/version-control-info.textproto | grep revision | sed 's/.*"\(.*\)".*/\1/')
echo "HEAD:  $HEAD"
echo "APK:   $APK_COMMIT"
[ "$HEAD" = "$APK_COMMIT" ] && echo "OK: APK commit matches HEAD" || echo "FAIL: APK stale — run ./gradlew clean assembleRelease"
```

### 4. Create Release MD File

If `app/release/release-<VERSION>.md` does not exist, create it. Base it on the previous release format and list all new features/fixes since the last tag.

```bash
ls app/release/release-*.md
git log <PREV_TAG>..HEAD --oneline  # gather changelog content
```

Format: headings for each feature area, bullet points for features/fixes.

### 5. Create Changelog TXT (F-Droid)

Create `fastlane/metadata/android/en-US/changelogs/<VERSION_CODE>.txt`. Point-wise, each line under 100 characters. List only new features and fixes.

```bash
ls fastlane/metadata/android/en-US/changelogs/
```

### 6. Commit and Push Release Artifacts

```bash
git add app/release/ fastlane/metadata/android/en-US/changelogs/
git commit -m "release: v<VERSION_NAME> — changelog and release notes"
git push origin main
```

### 7. Tag HEAD (after artifacts committed)

```bash
git tag -a v<VERSION_NAME> -m "Release v<VERSION_NAME>"
git push origin v<VERSION_NAME>
```

### 8. Verify Local APK Commit Again (after tag)

Confirm APK still matches HEAD (should be same since only artifacts changed).

```bash
HEAD=$(git log -1 --format="%H")
TAG=$(git rev-list -1 v<VERSION_NAME>)
APK_COMMIT=$(unzip -p app/build/outputs/apk/release/app-release.apk META-INF/version-control-info.textproto | grep revision | sed 's/.*"\(.*\)".*/\1/')
echo "HEAD: $HEAD"
echo "TAG:  $TAG"
echo "APK:  $APK_COMMIT"
[ "$HEAD" = "$APK_COMMIT" ] && [ "$TAG" = "$APK_COMMIT" ] && echo "OK: all match before upload" || echo "FAIL: mismatch"
```

### 9. Create GitHub Release with APK

```bash
gh release create v<VERSION_NAME> \
  --repo abhay-byte/mkm \
  --title "v<VERSION_NAME>" \
  --notes-file app/release/release-<VERSION>.md \
  app/build/outputs/apk/release/app-release.apk
```

### 10. Final Verification — DOWNLOAD RELEASE APK AND EXTRACT COMMIT (MANDATORY)

Download the released APK from GitHub and extract its commit hash. Must match TAG and HEAD.

```bash
TAG=$(git rev-list -1 v<VERSION_NAME>)
HEAD=$(git log -1 --format="%H")
curl -sL -o /tmp/release-verify.apk "https://github.com/abhay-byte/mkm/releases/download/v<VERSION_NAME>/app-release.apk"
RELEASE_APK_COMMIT=$(unzip -p /tmp/release-verify.apk META-INF/version-control-info.textproto | grep revision | sed 's/.*"\(.*\)".*/\1/')
LOCAL_APK_COMMIT=$(unzip -p app/build/outputs/apk/release/app-release.apk META-INF/version-control-info.textproto | grep revision | sed 's/.*"\(.*\)".*/\1/')

echo "HEAD:              $HEAD"
echo "TAG:               $TAG"
echo "LOCAL APK:         $LOCAL_APK_COMMIT"
echo "RELEASE APK (dl):  $RELEASE_APK_COMMIT"

[ "$HEAD" = "$RELEASE_APK_COMMIT" ] && [ "$TAG" = "$RELEASE_APK_COMMIT" ] \
  && echo "OK: release APK commit matches TAG and HEAD" \
  || echo "FAIL: mismatch — delete release and redo"
```

### 11. F-Droid (Auto)

F-Droid metadata uses `UpdateCheckMode: Tags` — new tags are auto-picked up. No manual metadata update needed unless metadata fields change.

---

## Quick Reference

| Artifact | Path |
|----------|------|
| APK | `app/build/outputs/apk/release/app-release.apk` |
| Release notes | `app/release/release-<VERSION>.md` |
| Changelog | `fastlane/metadata/android/en-US/changelogs/<CODE>.txt` |
| Commit in APK | `META-INF/version-control-info.textproto` → `revision` |

## Troubleshooting

### APK commit stale (invariant broken)
```bash
# Delete broken release
gh release delete v<VERSION_NAME> --repo abhay-byte/mkm --yes
git tag -d v<VERSION_NAME>
git push origin :refs/tags/v<VERSION_NAME>
# Clean rebuild from HEAD
./gradlew clean assembleRelease
# Re-run from step 3
```

### Release needs new APK
```bash
# Delete release, redo steps 2-10
gh release delete v<VERSION_NAME> --repo abhay-byte/mkm --yes
./gradlew clean assembleRelease
# ... redo steps 3-10
```
