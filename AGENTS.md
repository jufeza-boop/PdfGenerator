# PdfGenerator KMP — Agent Instructions

Este repositorio está diseñado para trabajo de agentes de codificación de larga duración. El objetivo no es maximizar la salida bruta de código. El objetivo es dejar el repositorio en un estado donde la próxima sesión pueda continuar sin adivinar.

## Flujo de Trabajo de Inicio

Antes de escribir código:

1. Confirma el directorio de trabajo.
2. Lee `agent-progress.md` para el estado verificado más reciente y el próximo paso.
3. Lee `feature_list.json` y elige la característica inacabada de mayor prioridad.
4. Revisa los commits recientes con `git log --oneline -5`.
5. Ejecuta `.\init.ps1`.
6. Si la verificación de referencia ya está fallando, corrígela primero. No apiles trabajo de características nuevas sobre un estado inicial roto.

## Reglas de Trabajo de Harness

- Trabaja en una característica a la vez.
- No marques una característica como completa solo porque se añadió código. Se requiere evidencia verificable.
- Mantén los cambios dentro del alcance de la característica seleccionada.
- **Mantenimiento Continuo:** Al introducir cambios arquitectónicos, nuevas funcionalidades o refactorizaciones, debes actualizar obligatoriamente `README.md`, `ARQUITECTURA.md` y el propio `AGENTS.md` (si procede) en la misma iteración.
- **Actualización del Harness:** Si modificas el entorno, los scripts de inicio o los comandos esenciales, asegúrate de mantener `init.ps1` y `feature_list.json` actualizados y funcionales.
- Al final de la sesión, actualiza `agent-progress.md` detallando el estado alcanzado y la siguiente acción recomendada.

## Reglas Específicas del Proyecto

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
| UI | `commonMain/ui/navigation/AppNavigation.kt`, `commonMain/ui/screens/` |
| ViewModel | `commonMain/viewmodel/ProjectViewModel.kt` |
| Repository | `commonMain/data/ProjectRepository.kt` |
| Database | `commonMain/data/JsonProjectStore.kt` (Almacenamiento local basado en JSON) |
| PDF Layout | `commonMain/data/PdfLayoutEngine.kt` (Platform-agnostic layout calculations) |
| PDF Renderer | `desktopMain/data/DesktopPdfGenerator.kt` & `androidMain/data/AndroidPdfGenerator.kt` (Low-level rendering canvas adapters) |
| Workspace | `WorkspaceManager.kt`, `WorkspaceAccessor.kt` (common) + platform managers |


## Block System

Report content is composed of typed blocks stored in `ContentBlockEntity`. Block `content` is a Moshi-serialized JSON string.

**Block types:** `TEXT`, `IMAGE`, `SIGNATURE`, `TITLE`, `FOOTER`, `TABLE`, `CHECKLIST`, `CHECKLIST_TABLE`

Always **sort by `sequence` field** before rendering or exporting blocks.

Half-width blocks (`isHalfWidth = true`) render side-by-side in pairs (e.g., two signatures).

## Key Conventions

- **State updates:** Use `copy()` on immutable entities, assign to `StateFlow`.
- **Async:** Use `viewModelScope.launch` for all ViewModel operations.
- **Platform code:** Use `expect/actual`, not runtime `when (platform)` checks.
- **Database changes:** Todo cambio en la estructura de datos debe reflejarse en `JsonProjectStore.kt` manteniendo la retrocompatibilidad del formato JSON local.
- **New block type:** Add to `ContentBlockEntity` enum, `BlockEditorForms.kt`, y `PdfLayoutEngine.kt`.
- **Decoupled Business Logic:** All logic regarding layout, data flow, synchronization rules, and template creation must live in `commonMain`. Platform modules are pure adapters.
- **Decoupled Serialization:** La serialización y el acceso a disco se gestionan dentro de `JsonProjectStore.kt` y `WorkspaceManager.kt`. No importar Moshi en los ViewModels.

## UI & Layout Modularization Rules

- **Max File Size:** Composable screen files in `ui/` should not exceed 600 lines. If they do, extract subcomponents into smaller helper files or local composables.
- **AppNavigation as routing source:** Navigation between screens must be orchestrated in `AppNavigation.kt` using routes. Screens must reside under the `screens/` subfolder.
- **Expect/Actual Screen definitions:** Declaring an `expect fun Screen(...)` must be done in its dedicated file under `commonMain/ui/screens/` and NOT inside `ProjectApp.kt` to avoid duplicate expect overrides.

## Pitfalls

- **Image/signature paths** are stored as absolute local file paths, not embedded. Always check file existence before PDF export.
- **PDF visual consistency:** Do not implement styling or layouts in `AndroidPdfGenerator` or `DesktopPdfGenerator`. All layout logic is central in `PdfLayoutEngine.kt` to ensure pixel-perfect parity.
- **Experimental APIs:** Using experimental Jetpack Compose APIs (e.g., `ExperimentalMaterial3Api`) requires tagging screens with `@OptIn(ExperimentalMaterial3Api::class)`. Ensure all internal functions invoke Composable APIs correctly in `@Composable` contexts.
- **Workspace on startup:** `WorkspaceManager` maneja la inicialización silenciosamente. No añadir bloqueos durante el init.
- **Gemini API key** is injected via `.env` (see `.env.example`). Never hardcode it.

## Environment

Requires a `.env` file at the project root with `GEMINI_API_KEY=...` for AI Studio features. Desktop files are stored in `~/.pdfgenerator/files/`.

## Language / Idioma

- **Language of work:** Always interact, document, and communicate in Spanish (español). All responses, comments, code documentation, and chat interactions must be in Spanish.
- **Idioma de trabajo:** Comunicarse, documentar y responder siempre en español. Todas las respuestas, explicaciones, comentarios de código y chats con el usuario deben ser en español.

