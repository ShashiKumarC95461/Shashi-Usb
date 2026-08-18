# Shashi-Usb-Media v0.8

This version is designed around the screenshots supplied by the user.

## Included
- Screenshot-style orange title bar
- Source selection screen
- Internal storage
- USB/OTG folder access through Android Storage Access Framework
- Cloud provider file picker (providers such as Google Drive/OneDrive/Dropbox may appear if installed)
- Photos / Photos and Videos / Videos / Music / Docs / File Manager
- Fast MediaStore scan for internal media
- Recursive deep scan for a user-selected storage/USB folder
- No photo thumbnails in file lists
- Full-screen image viewing with 50%-300% zoom controls
- Built-in video playback with 50%-300% viewing-size controls
- Search
- Smooth lazy scrolling with stable list state
- Pinch-to-zoom and double-tap image viewing (0.5x–5x)
- No thumbnail generation in the file list
- Pinch-to-zoom, pan and double-tap image viewing
- Smooth lazy scrolling with preserved position
- List/grid toggle button (the file list remains thumbnail-free)
- Rescan
- Defensive error handling so one inaccessible file does not crash the scan

## Build
Push these files to the `main` branch. GitHub Actions will build:
`app/build/outputs/apk/debug/app-debug.apk`

The workflow uses Java 17, Gradle 8.9, and actions/checkout@v5.

## App identity
The installed app name is `Shashi-Usb-Media`. The supplied statue photo is used as the launcher icon.
