# PDF Flow [![Android CI](https://github.com/EngAnasMuhammad/PDF-Flow/actions/workflows/build.yml/badge.svg)](https://github.com/EngAnasMuhammad/PDF-Flow/actions/workflows/build.yml) [![Version](https://img.shields.io/badge/version-2.2.1-blue.svg)](https://github.com/EngAnasMuhammad/PDF-Flow/releases)

A lightweight, **100% offline-first** Android PDF reader, annotator, and document utility suite built with **Jetpack Compose**, **Clean Architecture**, and a native **C++ PDFium NDK** engine.

Designed for speed, low RAM usage, and strict privacy—everything runs locally on your device with zero cloud uploads, zero tracking, and zero broad storage permission bloat, while maintaining full **16KB memory page size compliance**.

---

## Features

### Reading & Viewing
- **Native C++ PDFium Rendering:** Fast, smooth rendering and zooming via a modified C++ PDFium NDK engine.
- **Offline In-Document Text Search:** Fast, sequential document-order PDFium text search with a Google Drive-style morphed search bar, viewport auto-centering, active/inactive match highlighting, and **Arabic-aware normalization** (ignoring hamza variants `أ/إ/آ`, `ة/ه`, `ى/ي`, tashkeel, and tatweel).
- **Refined Reading & Selection UX:** Cleaner TopBar with Edit Mode moved to an animated Floating Action Button, background-threaded PDFium text hit-testing, and RTL-aware Material 3 teardrop selection handles with generous touch hitboxes and smooth drag auto-correction.
- **Unified In-Place Selection & Translate Menu:** Anchored floating Material 3 pill with 1-tap Copy, multi-color Highlight, and zero-permission 3-tier **Translate** fallback (`ACTION_PROCESS_TEXT` -> translate app intent -> browser URL fallback) with automatic PDF hyphen/break sanitization.
- **Custom GPU Reading Modes:** Hardware-accelerated `ColorMatrixColorFilter` support for **Sepia**, **Dimmed**, and **Dark** reading modes.
- **Adaptive Portrait & Landscape UI & Lifecycle Resilience:** Responsive layouts with edge-to-edge inset handling, 2-pane landscape sheets, and full state preservation across orientation changes, multi-window mode, and system configuration updates.
- **Viewing Modes & Preferences:** Continuous vertical and horizontal scroll options, and a keep-screen-awake toggle.
- **Bilingual & Theme Support:** Full bidirectional **English** & **Arabic (RTL)** localization and per-app **Theme (System / Light / Dark)** switching powered by `AppCompatDelegate`.

### Annotations & Digital Signatures
- **Instant Straight Lines & Hold-to-Snap:** Dedicated **Freehand / Straight Line** toggle with zero-delay live rubber-band preview and $\pm 2^\circ$ magnetic axis snapping. Smart per-tool defaults (Highlighter defaults to **Straight**; Pen defaults to **Freehand**) with remembered settings. In Freehand mode, pausing at stroke end for 350ms snaps smoothly to a straight line.
- **1-Tap Text Highlighting:** Select text directly in Reading Mode to apply multi-color pastel highlights in a single tap, with tap-to-recolor/delete and eraser support.
- **Freehand Pen & Highlighter:** Smooth Bézier ink strokes with customizable colors, stroke thickness, and a precision eraser.
- **Draggable & Resizable Text Boxes:** Place editable notes anywhere on a page with full **Arabic/RTL**, **LTR**, and **emoji** rendering.
- **Reusable Vector Digital Signatures:** Draw and save a personal signature on a dedicated canvas, stamp it onto any page, drag, and resize with locked aspect ratio.
- **Non-Destructive Editing & Offline Vector Export:** Full **Undo/Redo** history stored locally in Room, with 1-tap **Save Copy** exporting crisp, rotation-aware (`0° / 90° / 180° / 270°`) vector annotations via `PdfBox-Android` straight to `Downloads/PDFReader/`.

### Offline PDF Tools
- **Images to PDF:** Pick multiple images via Android's native Photo Picker (no storage permissions required), reorder pages, set a custom filename, and convert offline with automatic **EXIF orientation correction**, OOM-safe downscaling, and uniform `595 pt` (A4 width) page sizing for consistent viewing across all PDF readers.
- **Merge PDFs:** Combine multiple PDFs from different folders in any order using low-RAM temp-file streaming (`MemoryUsageSetting.setupTempFileOnly()`) with graceful handling for encrypted files.

### Organization & Persistence
- **Document Collections:** Group study materials, books, or work files into custom persistent collections.
- **Recent Files & Resume Reading:** Automatically tracks recently opened documents, remembers your last read page, and recovers cleanly from moved or deleted files.

---

## Engineering Challenges & Solutions

- **Rotation-Aware Vector Export (`0° / 90° / 180° / 270°`):**
    - *Challenge:* Android's top-left screen coordinates do not align with PDF's bottom-left coordinate system, and rotated PDF pages shift annotation positions on export.
    - *Solution:* Stored all strokes, text highlights, text boxes, and vector signatures in page-normalized (`0f..1f`) coordinates, then mapped them through a dedicated rotation-aware transform into `PdfBox-Android` cubic Bézier paths and `BlendMode.MULTIPLY` vector rectangles—verified with unit tests across all four page rotations.

- **OOM-Safe Offline Image Conversion & PDF Merging:**
    - *Challenge:* Converting 12MP+ camera photos or merging multi-megabyte PDFs in memory can trigger `OutOfMemoryError` crashes on low-RAM Android devices.
    - *Solution:* Implemented a two-pass `BitmapFactory` (`inSampleSize`) decoder with automatic `ExifInterface` orientation correction and immediate per-page `Bitmap.recycle()` after embedding. For merging, streamed documents via `MemoryUsageSetting.setupTempFileOnly()` into app cache with atomic `MediaStore` writes and guaranteed cleanup.

- **Cross-Viewer Page Consistency (`Images to PDF`):**
    - *Challenge:* Mixing portrait photos and wide banners in one PDF caused desktop/office viewers (such as WPS Office and Adobe Acrobat) to render tall pages as narrow columns due to shared document-wide zoom scaling.
    - *Solution:* Standardized generated page widths to `595 pt` (A4 width) while scaling height proportionally, ensuring uniform edge-to-edge rendering across both mobile and desktop PDF viewers.

- **Non-Destructive Editing Without Storage Bloat:**
    - *Challenge:* Modifying PDFs in place requires broad file permissions and destroys undo history, while exporting a new copy on every edit clutters the user's storage.
    - *Solution:* Persisted editable annotations locally in Room (with explicit schema migrations and zero broad storage permissions via SAF and Android's Photo Picker), offering instant **Undo/Redo** and on-demand **Save Copy** export to `Downloads/PDFReader/`.

- **Hold-to-Snap Stroke Geometry & Timer Debouncing:**
    - *Challenge:* Snapping freehand strokes into straight lines must not interfere with natural handwriting or incur touch lag, while handling touch jitter and multi-segment Bézier curves.
    - *Solution:* Implemented a 350ms touch-hold detection algorithm in `StrokeSnapHelper` with distance threshold filtering (<15dp movement window), transforming dynamic stroke points into a direct line segment while maintaining full Undo/Redo fidelity and vector export compatibility.

- **Zero-Permission Contextual Translation:**
    - *Challenge:* Offering text translation inside an offline-first app without introducing `INTERNET` permissions or heavy on-device ML libraries.
    - *Solution:* Utilized Android's IPC Intent subsystem (`ACTION_PROCESS_TEXT` and targeted URI intents) with an offline text sanitization pipeline that strips PDF hyphenation artifacts and joins fragmented PDF lines prior to delegation.

- **Lean Binary Footprint & Native PDF Performance:**
    - *Challenge:* Bundling native C++ PDFium `.so` binaries alongside `PdfBox-Android` and Room dependencies often balloons APK size past 40–50 MB.
    - *Solution:* Implemented `arm64-v8a` target ABI optimization, strict R8 code shrinking, and resource optimization—keeping the full offline C++ rendering and vector manipulation suite compressed to around **~13 MB**.

---

## Tech Stack
- **UI:** Jetpack Compose, Material 3
- **Architecture:** Clean Architecture (`data`, `domain`, `presentation`), MVVM, `StateFlow` / Coroutines
- **Dependency Injection:** Dagger Hilt
- **Local Storage:** Room Database (with explicit schema migrations) & scoped local storage
- **PDF Engines:** Native C++ PDFium (NDK) for rendering + `PdfBox-Android` for offline vector export, merging, and image conversion
- **Image & EXIF:** `androidx.exifinterface` for image rotation handling
- **CI/CD:** GitHub Actions (Automated Android builds & unit tests)

---

## Release History & Changelog

### `v2.2.1` (Patch Update)
- **Instant Straight-Line vs. Freehand Mode Toggle:** Added a dedicated segmented toggle inside the Pen & Highlighter style popover for zero-delay, 2-point straight line drawing with live rubber-band preview and $\pm 2^\circ$ magnetic axis snapping (horizontal & vertical).
- **Per-Tool Mode Defaults & Persistence:** Highlighter defaults to **Straight** mode for rapid line highlighting; Pen defaults to **Freehand**. Each tool independently remembers its mode when switching.
- **Micro-Tap Filtering & Multi-Touch Guards:** Straight mode ignores accidental taps ($< 4\text{dp}$) and prevents multi-touch gestures from committing invalid endpoints.
- **Contextual Tip:** Automatically hides the hold-to-straighten hint when Straight mode is selected, keeping the UI clean. Full English & Arabic localization.

### `v2.2.0`
- **Hold-to-Snap Straight Lines:** Intelligent 350ms pause-to-straighten gesture in Freehand mode (`StrokeSnapHelper`) with magnetic axis snapping and 2-point vector export optimization.
- **Zero-Permission 3-Tier Translate Action:** Added instant Translation to the text selection popup (`ACTION_PROCESS_TEXT` $\to$ translation app $\to$ browser URL fallback) with automatic PDF line-break and hyphenation cleanup.
- **Unified Text Selection Popup:** Replaced multi-window popups with a smooth, anchored single-container menu (`MAIN`, `COLORS`, `MORE`) with animated transitions and back navigation.
- **Full Android Lifecycle & Rotation Audit:** Preserved active Annotate mode and in-progress strokes across device rotations and multi-window resizing; moved background tasks (export, merge, image conversion) to `Dispatchers.IO` with proper `CancellationException` propagation; and restored external PDF URIs across process recreation.

---

## Installation
1. Go to the [Releases](../../releases) tab.
2. Download the latest `v2.2.1` release (`PDF-Flow-v2.2.1.apk`).
3. Install on any Android 8.0+ device (API 26+).

---

## Author
Designed & Developed by **Anas Muhammad** — [GitHub](https://github.com/EngAnasMuhammad)