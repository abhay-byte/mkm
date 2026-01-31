---
description: How to deploy MKM to F-Droid
---

# F-Droid Deployment Workflow

This workflow documents the process for deploying and updating Minimal Kernel Manager (MKM) on F-Droid.

## Prerequisites

1.  **GitLab Account**: You need a GitLab account to interact with the F-Droid data repository.
2.  **Fork of fdroiddata**: You must have a fork of [https://gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).

## Step 1: Prepare Release

1.  **Update Version**:
    *   Edit `app/build.gradle.kts`:
        *   Increment `versionCode` (e.g., `2`)
        *   Update `versionName` (e.g., `"1.1"`)

2.  **Update Changelog**:
    *   Create a new file in `fastlane/metadata/android/en-US/changelogs/`.
    *   Filename must match the new `versionCode` (e.g., `2.txt`).
    *   Keep it under 500 characters.

3.  **Create Git Tag**:
    *   Commit changes: `git commit -am "Release v1.1"`
    *   Tag the release: `git tag -a v1.1 -m "Release v1.1"`
    *   Push tag to GitHub: `git push origin v1.1`

## Step 2: Update F-Droid Metadata

1.  **Access your fdroiddata fork**.
2.  **Edit / Create Metadata File**: `metadata/com.ivarna.mkm.yml`
    *   If this is the first submission, create the file using the template below.
    *   If updating, F-Droid's bot usually picks up new tags automatically if `UpdateCheckMode: Tags` is set. You might not need to do anything!

### Metadata Template

```yaml
Categories:
  - Development
  - System
License: GPL-3.0-or-later
AuthorName: Abhay Raj
AuthorWebSite: https://github.com/abhay-byte
SourceCode: https://github.com/abhay-byte/mkm
IssueTracker: https://github.com/abhay-byte/mkm/issues

AutoName: Minimal Kernel Manager

RepoType: git
Repo: https://github.com/abhay-byte/mkm

Builds:
  - versionName: '1.0'
    versionCode: 1
    commit: v1.0
    subdir: app
    sudo:
      - apt-get update
      - apt-get install -y cmake
      - update-alternatives --auto java
    gradle:
      - yes
    ndk: r29c

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '1.0'
CurrentVersionCode: 1
```

## Step 3: Verify and Submit

1.  **Local Test (Optional but Recommended)**:
    *   Use `fdroidserver` docker container to build locally.
    *   Run `fdroid lint com.ivarna.mkm`.

2.  **Submit Merge Request**:
    *   On GitLab, create a Merge Request from your fork to the official `fdroid/fdroiddata` repository.
    *   For new apps, use title `New App: com.ivarna.mkm`.
    *   Wait for review and address any feedback.

## Post-Submission

*   Once merged, it takes a few days for the app to appear on F-Droid.
*   F-Droid will sign the APK with their own keys.
