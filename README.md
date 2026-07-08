# Image to PDF

A minimal, privacy-first Android app that combines one or more images into a
single multi-page PDF, at full original resolution, entirely on-device.

## What it does

- One "Select Images" button opens the system picker and lets you choose a
  single image or multiple images at once (same flow either way).
- Selected images appear as an ordered list of pages, each with a thumbnail,
  page number, and ▲ / ▼ / ✕ controls to reorder or remove it.
- "Convert to PDF" combines the pages, in the order shown, into one PDF —
  each image is placed on its own page sized to that image's exact pixel
  dimensions, with no downscaling or re-compression.
- You then pick where to save the PDF using the normal Android "Save As"
  dialog.

## Privacy / security properties (verifiable in the source)

- `AndroidManifest.xml` declares **zero** `<uses-permission>` elements — no
  `INTERNET`, no `READ_EXTERNAL_STORAGE`, no `WRITE_EXTERNAL_STORAGE`.
- There is no networking code or HTTP library anywhere in the project
  (`grep -ri "http\|socket\|okhttp\|retrofit" app/src` returns nothing) —
  the app is structurally incapable of making a network request.
- All file access goes through the Storage Access Framework:
  `ActivityResultContracts.OpenMultipleDocuments` for picking images and
  `ActivityResultContracts.CreateDocument` for the save dialog. The app never
  touches any file the user didn't explicitly pick.
- Images are decoded straight from the picked `Uri` into memory, drawn onto
  PDF pages, and released (`bitmap.recycle()`) immediately after — nothing is
  ever written to app-private/cache storage.
- `android:allowBackup="false"` in the manifest disables both cloud Auto
  Backup and device-to-device transfer backup of app data.
- The release build type has `isMinifyEnabled = true` / `isShrinkResources =
  true`, enabling R8 minification (`app/build.gradle.kts`).

## Project structure

```
ImageToPDF/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/imagetopdf/
│       │   ├── MainActivity.kt
│       │   └── PageAdapter.kt
│       └── res/...
└── .github/workflows/build.yml   # CI pipeline that builds the APK
```

## Getting an installable APK

This project was built as source in an environment without internet/SDK
access, so it hasn't been compiled here. The included GitHub Actions
workflow (`.github/workflows/build.yml`) will compile it automatically —
GitHub's `ubuntu-latest` runners come with the Android SDK preinstalled, so
no extra setup is required. Steps:

1. Create a new (public or private) repository on GitHub.
2. Upload the entire contents of this `ImageToPDF/` folder to the repo
   (either `git push` from your machine, or use GitHub's "Add file → Upload
   files" web UI and drag the whole folder in, preserving the folder
   structure).
3. Once pushed to the `main` (or `master`) branch, go to the repo's
   **Actions** tab. The "Build APK" workflow starts automatically (you can
   also click **Run workflow** to trigger it manually).
4. Wait for the run to finish (a few minutes). Open the completed run and
   scroll to **Artifacts** at the bottom — you'll see:
   - `ImageToPDF-debug-apk` — install this directly on a device/emulator to
     try the app immediately (debug-signed automatically by Gradle).
   - `ImageToPDF-release-unsigned-apk` — the minified/R8-shrunk release
     build. It's **unsigned**, so before installing it on a device you'd
     need to sign it with your own key (`jarsigner`/`apksigner`) or use
     `gradle assembleRelease` locally with a configured signing config.
5. Download the artifact zip, unzip it to get the `.apk`, and install it on
   an Android device (enable "Install unknown apps" for the source you use,
   e.g. your file manager or browser).

### Building locally instead (if you have Android Studio)

Just open the `ImageToPDF/` folder in Android Studio (Giraffe or newer) — it
will sync automatically — and click **Run**, or use **Build → Generate
Signed Bundle / APK** for a release build.

From the command line with the Android SDK installed:

```bash
cd ImageToPDF
gradle assembleDebug      # or: ./gradlew assembleDebug if you generate a wrapper
```

## Known limitations

- Very large image sets may take a while to convert and use noticeable
  memory, since each full-resolution bitmap is decoded (one at a time, then
  released) to preserve quality — this is an inherent tradeoff of not
  downscaling or recompressing.
- The release APK produced by CI is unsigned; Google Play and most
  "unknown sources" installs require a signed APK, so sign it before wider
  distribution.
