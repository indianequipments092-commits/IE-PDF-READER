# PDF MASTER — Master Requirements

## Project Identity
- App name: **PDF MASTER**
- Repository: `indianequipments092-commits/IE-PDF-READER`
- Selected logo: the user-approved premium PDF MASTER logo; integrate it during implementation.
- Core principle: **FAST + SMOOTH + SHARP + LIGHTWEIGHT + COMFORTABLE UI**

## Product Goal
PDF MASTER is a lightweight, professional Android PDF reader focused on extremely fast-feeling opening, smooth reading, high-quality rendering, smooth zoom and 2D pan, and comfortable UI. The app must not become unnecessarily heavy by adding unrelated PDF-editor/AI/scanner features.

## Non-Negotiable Core Requirements

### 1. Fast PDF Opening
- Open PDFs as quickly as reasonably possible.
- Show the first visible page as early as possible.
- Do not load the entire PDF into memory before displaying it.
- Use progressive/lazy rendering and prioritize visible content.
- Avoid unnecessary loading screens, blank screens, freezes, and hangs.

### 2. High-Quality PDF Rendering
- Use a proper/native/optimized PDF rendering approach.
- Text-based, editable/selectable and vector PDF content must remain sharp when zoomed.
- Do not convert pages to permanently low-resolution screenshots for viewing.
- Preserve the original PDF content quality as far as the rendering engine allows.

### 3. Smooth Zoom
- Pinch-to-zoom.
- Double-tap zoom.
- Smooth zooming without sudden jumps or unnecessary reloads.
- Support multiple useful zoom levels.

### 4. Full 2D Pan After Zoom
- After zooming, users must be able to move the PDF freely in both axes: up/down and left/right.
- Pan must feel natural and responsive.
- Correct boundaries must prevent unwanted empty movement.

### 5. Smart Minimum Zoom — Mandatory
- Zooming out must stop around a screen-fit/readable minimum.
- The PDF must never become a tiny dot or unnecessarily distant page.
- Intended behavior: **Zoom out → Screen Fit → Stop**.
- The default view should use the available screen comfortably.

### 6. Smooth Continuous Reading
- Vertical continuous page scrolling.
- Lazy page rendering.
- Page recycling where appropriate.
- Smart page caching.
- Visible-page priority.
- Nearby-page preparation.
- Memory release for pages no longer needed.
- No unnecessary page flashing, blank rendering, or reloads during normal scrolling.

## Stage 1 Foundation + Premium App UI Requirements
- Android project foundation.
- PDF MASTER branding.
- Approved logo integration.
- Lightweight architecture and clean project structure.
- App launch flow.
- Basic file handling.
- Premium but clean home screen.
- Comfortable spacing and touch targets.
- Easy-to-understand controls.
- Recent PDFs foundation.
- Large, comfortable Open PDF action.
- Empty state when there are no PDFs.
- Appropriate dark/light foundation.
- PDF file picker.
- PDF open.
- Optimized PDF rendering foundation.
- Fast first-page rendering.
- Basic PDF viewer.
- Clear loading and error states.
- UI must remain fast; avoid heavy unnecessary animations/effects.

## Stage 2 Ultra-Smooth Viewer Requirements
- Continuous vertical scrolling.
- Lazy page rendering.
- Page recycling.
- Smart caching.
- Visible-page priority.
- Nearby-page preparation.
- Memory optimization/release.
- Large PDF optimization.
- Fast first page.
- Smooth scrolling.
- No unnecessary reloads.
- No screen flashing.
- No blank pages during normal scrolling.
- Intelligent background rendering.
- Maximum useful reading area.
- Comfortable page spacing.
- Subtle loading indicators.

## Stage 3 Advanced Zoom + 2D Pan Requirements
- Pinch-to-zoom.
- Double-tap zoom.
- Smooth zoom animation.
- Multiple useful zoom levels.
- High-quality re-rendering after zoom.
- Maintain useful zoom state.
- Full 2D pan.
- Sharp text PDF rendering.
- Sharp vector content.
- Clear editable/selectable PDFs.
- No fixed low-resolution screenshot rendering.
- Minimum zoom locked around screen-fit.
- Natural pinch center.
- No unexpected jump after zoom.
- Correct pan boundaries.
- Comfortable double-tap behavior.

## Stage 4 Viewer Top Bar + Controls
When a PDF opens, provide a clean, comfortable top toolbar containing, as appropriate:
- Back.
- PDF name.
- Info.
- Share.
- Open With / Open in another app.
- More menu.

### Info
- File name.
- File size.
- Total pages.
- Available PDF metadata.
- File location.
- Other useful document information.

### Share
- Use the native Android share sheet.
- Allow sharing to apps such as WhatsApp, Telegram, Gmail, Drive, etc. according to what is installed.

### Open With
- Support Android's native open-with/another-app flow.

### Toolbar UX
- Comfortable touch targets.
- Clearly visible icons.
- No cramped controls.
- Auto-hide while reading where appropriate.
- Tap screen to reveal controls.
- Reading mode should maximize PDF area.

## Stage 5 Advanced Page Navigation
- Current page / total pages.
- Page number indicator.
- Page jump.
- Previous/next navigation where useful.
- Fast page navigation.
- Page thumbnails.
- Smooth thumbnail loading.
- Tap thumbnail to open the page.
- Efficient thumbnails for large PDFs.
- Accurate current-page tracking.
- Thumb-friendly navigation controls.

## Stage 6 Smart Reading Features
### Search
- Search inside text PDFs where supported.
- Search results.
- Result count.
- Page number.
- Jump to the exact result location where supported.

### Bookmarks
- Add bookmark.
- Remove bookmark.
- Bookmark list.
- Direct jump.

### Text
- Select/copy text where the PDF exposes selectable text.
- Do not fake text selection for image/scanned PDFs.

### Password PDFs
- Password prompt.
- Correct password opens the PDF.
- Incorrect password gives a clear error.

### Recent / Continue Reading
- Recently opened PDFs.
- Last-read page/position.
- Continue from the previous position.

## Stage 7 Android Integration
- Open PDFs from Android file managers.
- Android Open With integration.
- Open PDFs received from WhatsApp, Telegram, browsers, Gmail, Downloads and other file managers where Android permits.
- Native Android share sheet.
- Correct URI permissions.
- Temporary/persistent access where required.
- Compatibility across supported Android versions.
- Invalid/missing file handling.

## Stage 8 Premium Comfortable UI/UX Polish
- Premium layout without clutter.
- Comfortable spacing.
- Clear visual hierarchy.
- Easy navigation.
- Recent PDFs.
- Empty states.
- Proper icons.
- Lightweight smooth interactions.
- Clean viewer toolbar.
- Comfortable controls.
- Proper icon sizes and touch targets.
- Reading-first layout.
- Minimal distractions.
- Clear loading/error messages.
- No confusing buttons.
- No unnecessary popups.
- Consistent design.
- Portrait and landscape support.
- Adaptation to different screen sizes.
- Tablet-friendly foundation where practical.
- Accessibility basics.

## Stage 9 Performance + Edge-Case Testing
Test at minimum:
- 1–5 page PDFs.
- 20 page PDFs.
- 50 page PDFs.
- 100+ page PDFs.
- Very large file-size PDFs.
- Text PDFs.
- Editable/selectable PDFs.
- Image PDFs.
- Mixed PDFs.
- Tables.
- High-resolution PDFs.
- Password PDFs.
- Corrupted/invalid PDFs.
- Very long pages.

Full flow:
**Open → Render → Scroll → Zoom → Pan → Zoom-out → Search → Bookmark → Share → Open With → Close → Reopen**

Stress cases:
- Rapid zoom.
- Rapid scrolling.
- Zoom + scroll together.
- Repeated page changes.
- Background/foreground transitions.
- Screen rotation.
- Large-PDF memory behavior.
- Multiple PDFs.

The objective is to identify and fix crashes, hangs, blank rendering, memory problems and serious performance issues.

## Stage 10 Final Optimization + Release
### APK
- Reduce APK size where safely possible.
- Remove unnecessary dependencies.
- Optimize resources.
- Configure release build.

### Performance
- RAM optimization.
- CPU optimization.
- GPU/rendering review.
- Startup performance.
- PDF opening performance.
- Scrolling performance.
- Zoom performance.

### Final UI/Branding
- Verify approved logo.
- Verify PDF MASTER name.
- App icon.
- Splash.
- Toolbar.
- Home screen.
- Error screens.
- Empty states.

### Final QA
- Full regression test.
- No known critical errors.
- Verify all requirements.
- Release APK.
- Repository cleanup.
- README/documentation.

## UI/UX Rule — Applies Everywhere
Premium UI does **not** mean decorative UI only. Every control must be readable, properly spaced, comfortably tappable, understandable, and designed around the PDF reading area. Visual polish must never sacrifice rendering performance, memory efficiency, or responsiveness.

## Priority Order
1. Performance
2. PDF quality
3. Zoom + 2D pan
4. Memory efficiency
5. Stability
6. Comfortable UI/UX
7. Reader features
8. Visual polish

## Explicitly Out of Scope for the Initial Reader
Do not add unrelated heavy systems merely to make the app look feature-rich, including a PDF editor, PDF creator/converter suite, OCR system, AI PDF chat/summarization, scanner, e-signature platform, or cloud/account backend unless separately approved as a requirement.

## Locked Development Stages
The project has exactly these high-level stages:
- **Stage 1:** Foundation + Premium App UI
- **Stage 2:** Ultra-Smooth PDF Viewer
- **Stage 3:** Advanced Zoom + 2D Pan Engine
- **Stage 4:** PDF Viewer Top Bar + Controls
- **Stage 5:** Advanced Page Navigation
- **Stage 6:** Smart Reading Features
- **Stage 7:** Android Integration
- **Stage 8:** Premium Comfortable UI + UX Polish
- **Stage 9:** Performance + Edge-Case Master Testing
- **Stage 10:** Final Optimization + Release

## Stage Completion Rule
For every stage, the required workflow is:
1. **Step 1 — Build:** Completely implement the current stage.
2. **Step 2 — Verification 1:** Check compilation, runtime errors, functionality, and basic UI/functionality.
3. **Step 3 — Verification 2:** Deeply verify original requirements, integration, performance, edge cases, UI/UX, Android compatibility, and previous-stage features.
4. **Step 4 — Fix + Pass:** Fix every discovered issue, retest, and only consider the stage complete after both verifications pass.

Do not skip a stage or silently move to the next stage before the current stage passes its workflow.
