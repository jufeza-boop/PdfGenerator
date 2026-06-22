# PdfGenerator KMP — Agent Instructions

See [README.md](README.md) for project overview, tech stack, and build/run commands.

## Essential Commands

```bash
./gradlew :composeApp:run          # Run on Windows desktop
./gradlew :composeApp:assembleDebug # Build Android APK
./gradlew :composeApp:packageMsi   # Build Windows installer
```

## Architecture

**MVVM + Kotlin Multiplatform.** Shared UI in `commonMain` (Compose Multiplatform). Platform-specific logic via `expect/actual` in `androidMain` / `desktopMain`.

| Layer | Key Files |
|---|---|
| UI | `commonMain/ui/ProjectApp.kt`, `BlockEditorForms.kt` |
| ViewModel | `commonMain/viewmodel/ProjectViewModel.kt` |
| Repository | `commonMain/data/ProjectRepository.kt` |
| Database | `commonMain/data/AppDatabase.kt` (Room v8, BundledSQLiteDriver) |
| PDF (Desktop) | `desktopMain/data/DesktopPdfGenerator.kt` (OpenPDF) |
| PDF (Android) | `androidMain/data/AndroidPdfGenerator.kt` (android.graphics.pdf) |
| Sync | `FolderSyncManager` interface + platform implementations |

## Block System

Report content is composed of typed blocks stored in `ContentBlockEntity`. Block `content` is a Moshi-serialized JSON string.

**Block types:** `TEXT`, `IMAGE`, `SIGNATURE`, `TITLE`, `FOOTER`, `TABLE`, `CHECKLIST`, `CHECKLIST_TABLE`

Always **sort by `sequence` field** before rendering or exporting blocks.

Half-width blocks (`isHalfWidth = true`) render side-by-side in pairs (e.g., two signatures).

## Key Conventions

- **State updates:** Use `copy()` on immutable entities, assign to `StateFlow`.
- **Async:** Use `viewModelScope.launch` for all ViewModel operations.
- **Platform code:** Use `expect/actual`, not runtime `when (platform)` checks.
- **Database changes:** Bump `AppDatabase` version and add a migration. Dev builds use destructive migration fallback.
- **New block type:** Add to `ContentBlockEntity` enum, `BlockEditorForms.kt`, and both PDF generators.

## Pitfalls

- **Image/signature paths** are stored as absolute local file paths, not embedded. Always check file existence before PDF export.
- **Both PDF engines must stay visually in sync.** A change to layout logic in `DesktopPdfGenerator` usually requires a matching change in `AndroidPdfGenerator`.
- **Sync on startup is silent.** `FolderSyncManager` triggers automatically if configured — don't add blocking calls during init.
- **`ProjectApp.kt` is large (~152 KB).** Prefer editing targeted composables rather than restructuring the file.
- **Gemini API key** is injected via `.env` (see `.env.example`). Never hardcode it.

## Environment

Requires a `.env` file at the project root with `GEMINI_API_KEY=...` for AI Studio features. Desktop files are stored in `~/.pdfgenerator/files/`.
