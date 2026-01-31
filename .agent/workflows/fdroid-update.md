---
description: How to update F-Droid with a new MKM version
---

# F-Droid Version Update Workflow

This workflow guides you through updating the F-Droid listing when releasing a new version of Minimal Kernel Manager (MKM).

## Prerequisites

- New version built and tested locally
- Version code and version name updated in `app/build.gradle.kts`
- All changes committed to main branch

## Steps

### 1. Update Version Information

Update version in `app/build.gradle.kts`:
```kotlin
versionCode = <NEW_VERSION_CODE>
versionName = "<NEW_VERSION_NAME>"
```

### 2. Commit and Tag Release

// turbo
```bash
git add app/build.gradle.kts
git commit -m "chore: Bump version to <VERSION_NAME>"
git push origin main
```

// turbo
```bash
git tag -a v<VERSION_NAME> -m "Release v<VERSION_NAME>"
git push origin v<VERSION_NAME>
```

### 3. Get Latest Commit Hash

// turbo
```bash
git log -1 --format="%H"
```

Copy the full commit hash (40 characters).

### 4. Update F-Droid Metadata

Navigate to fdroiddata repository:
```bash
cd /home/abhay/repos/fdroiddata
git pull origin master
git checkout <YOUR_BRANCH_NAME>
```

Edit `metadata/com.ivarna.mkm.yml`:
- Update `commit:` with the new commit hash
- Update `versionName:` with the new version
- Update `versionCode:` with the new version code
- Update `CurrentVersion:` and `CurrentVersionCode:` at the bottom

### 5. Verify Line Endings

// turbo
```bash
file metadata/com.ivarna.mkm.yml
```

Should show "ASCII text" (not "with CRLF line terminators").

If needed, fix line endings:
```bash
dos2unix metadata/com.ivarna.mkm.yml
```

### 6. Commit and Push to F-Droid

// turbo
```bash
git add metadata/com.ivarna.mkm.yml
git commit -m "Update MKM to v<VERSION_NAME>"
git push
```

### 7. Wait for CI Pipelines

Monitor the F-Droid CI pipelines at:
https://gitlab.com/<YOUR_USERNAME>/fdroiddata/-/pipelines

Ensure all jobs pass:
- ✅ fdroid build
- ✅ check apk
- ✅ checkupdates
- ✅ fdroid lint
- ✅ fdroid rewritemeta
- ✅ git redirect
- ✅ schema validation
- ✅ tools check scripts

### 8. Update Merge Request (if needed)

If this is a new MR, comment on the existing merge request with:
```
Updated to v<VERSION_NAME> (commit: <SHORT_HASH>)
```

## Quick Reference

**Metadata File**: `/home/abhay/repos/fdroiddata/metadata/com.ivarna.mkm.yml`

**Required Fields to Update**:
- `commit:` - Full 40-character commit hash
- `versionName:` - New version name (e.g., "1.1")
- `versionCode:` - New version code (e.g., 2)
- `CurrentVersion:` - Same as versionName
- `CurrentVersionCode:` - Same as versionCode

## Troubleshooting

### Line Ending Issues
If `rewritemeta` job fails:
```bash
dos2unix metadata/com.ivarna.mkm.yml
git add metadata/com.ivarna.mkm.yml
git commit --amend --no-edit
git push --force
```

### Build Failures
Check the build log in F-Droid CI. Common issues:
- NDK version mismatch (should be r29c)
- Gradle version issues
- Missing dependencies

### Tag Issues
If tag needs to be updated:
```bash
git tag -d v<VERSION_NAME>
git tag -a v<VERSION_NAME> -m "Release v<VERSION_NAME>"
git push origin v<VERSION_NAME> --force
```

## Notes

- F-Droid builds from the tagged commit, so ensure the tag points to the correct commit
- Reproducible builds are enabled, so F-Droid will verify the build matches
- The gradle-wrapper.jar is excluded from F-Droid builds (they use their own)
- Always verify line endings are LF (Unix) not CRLF (Windows)
