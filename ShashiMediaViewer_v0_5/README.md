# Shashi Media Viewer v0.5 PRO

Major upgrade over v0.4.

## Included
- High-speed metadata-only deep scanner using DocumentsContract.
- SQLite local file index for fast searching without external database dependencies.
- Indexed file metadata: URI, parent, name, size, MIME, modified time and category.
- Search index on filename.
- Category index for fast filtering.
- Live files/sec and access diagnostics.
- Safe cancellation.
- USB attach/detach notifications.
- Persistent read permission for selected USB tree.
- Wider media/document/archive detection.
- Premium dashboard foundation.

## Important limitation
Android's Storage Access Framework exposes only entries that the selected storage provider permits.
Protected, damaged, encrypted or unsupported entries can remain inaccessible. The app counts those
conditions rather than falsely reporting them as scanned.

## Next production step
- Full folder navigation.
- Open/play/preview files.
- Copy/move/rename/delete/share through SAF.
- Thumbnail cache.
- Duplicate detection.
- Incremental rescan instead of clearing the index.
- USB filesystem/provider benchmarks.
- Background scan service and notification.
- Final signed AAB/APK and Play Store release configuration.
