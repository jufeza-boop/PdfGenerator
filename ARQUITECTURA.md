# Análisis de Arquitectura — PdfGenerator KMP

**Fecha:** 2026-06-22  
**Proyecto:** PDF Generator (Kotlin Multiplatform — Android + Desktop)  
**Analista:** Claude Sonnet 4.6

---

## 1. Mapa de flujo de datos

```
┌─────────────────────────────────────────────────────────────────┐
│  ENTRY POINTS (Platform-specific)                               │
│  androidMain/MainActivity.kt  ←→  desktopMain/Main.kt           │
│  Inyectan:                                                      │
│    Android → AndroidPdfGenerator, AndroidFolderSyncManager     │
│    Desktop → DesktopPdfGenerator, DesktopFolderSyncManager     │
└─────────────────────┬───────────────────────────────────────────┘
                      │ instancia ProjectViewModel
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  VIEWMODEL (commonMain)                                         │
│  ProjectViewModel.kt [594 líneas]                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ StateFlow<List<Project>>   allProjects                  │   │
│  │ StateFlow<Project?>        selectedProject              │   │
│  │ StateFlow<List<Block>>     draftBlocks / originalBlocks │   │
│  │ StateFlow<Boolean>         isDirty                      │   │
│  │ StateFlow<SyncState>       syncState / syncConfig       │   │
│  │ StateFlow<File?>           generatedPdfFile             │   │
│  │ StateFlow<Boolean>         isGeneratingPdf              │   │
│  │ SharedFlow<Unit>           uploadSuccess (eventos)      │   │
│  └─────────────────────────────────────────────────────────┘   │
│  Depende de: ProjectRepository, PdfGenerator, FolderSyncMgr    │
└─────────────────────┬───────────────────────────────────────────┘
                      │ collectAsStateWithLifecycle()
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│  VIEW (commonMain)                                              │
│  ProjectApp.kt [2,935 líneas] + BlockEditorForms.kt [207]       │
│  + FolderSyncDialog.kt [396]                                    │
│                                                                 │
│  Las Composables:                                               │
│  • Reciben estado como parámetros inmutables                    │
│  • Emiten acciones como lambdas → viewModel.metodo()            │
│  • Usan expect/actual para diferencias de plataforma            │
│                                                                 │
│  expect/actual UI:                                              │
│  • PlatformBackHandler    • PlatformImagePicker                 │
│  • SignatureDialog        • PdfPreviewScreen                    │
│  • PlatformFolderSelector                                       │
└──────────────┬──────────────────────────┬───────────────────────┘
               │                          │
               ▼                          ▼
   UI.android.kt [318]          UI.desktop.kt [306]
   PlatformUtils.android.kt     PlatformUtils.desktop.kt
```

---

## 2. Inventario completo de archivos

### commonMain — Código compartido (3,957 líneas | 64%)

| Archivo | Líneas | Capa | Descripción |
|---|---|---|---|
| `data/AppDatabase.kt` | 142 | Model | Entidades Room (Project, Visit, ContentBlock), DAOs, expect DB builder |
| `data/Models.kt` | 69 | Model | DTOs: SyncState, TableBlockContent, ChecklistBlockContent, etc. |
| `data/ProjectRepository.kt` | 201 | Repository | CRUD de proyectos y bloques, orquesta DAOs + PdfGenerator + SyncManager |
| `data/PdfGenerator.kt` | 9 | Interface | Contrato para generación de PDF (implementado por cada plataforma) |
| `data/FolderSyncManager.kt` | 16 | Interface | Contrato para sincronización de carpetas (implementado por cada plataforma) |
| `ui/PlatformUtils.kt` | 12 | Interface | Contrato para utilidades de plataforma (toast, share, storage) |
| `viewmodel/ProjectViewModel.kt` | 594 | ViewModel | Gestión de estado central; StateFlows, operaciones CRUD, PDF, sync |
| `ui/ProjectApp.kt` | 2,935 | View | Composable raíz; contiene dashboard, editor, visor PDF, diálogos |
| `ui/BlockEditorForms.kt` | 207 | View | Formularios reutilizables para edición de bloques |
| `ui/FolderSyncDialog.kt` | 396 | View | Diálogo de configuración de sincronización de carpetas |
| `ui/theme/Color.kt` | 22 | Utility | Definición de colores Material 3 |
| `ui/theme/Theme.kt` | 36 | Utility | Composición del tema Material 3 |
| `ui/theme/Type.kt` | 36 | Utility | Definición de tipografías |

### androidMain — Implementaciones Android (1,231 líneas | 20%)

| Archivo | Líneas | Capa | Descripción |
|---|---|---|---|
| `MainActivity.kt` | 53 | Entry Point | Inyecta implementaciones Android, crea ViewModelFactory, inicia Compose |
| `AndroidPlatform.kt` | 5 | Utility | Holder global del `Context` de Android |
| `data/AndroidPdfGenerator.kt` | 554 | Platform Impl | Genera PDF usando `android.graphics.pdf.PdfDocument` nativo |
| `data/AndroidFolderSyncManager.kt` | 275 | Platform Impl | Sincronización con SAF (Storage Access Framework) y DocumentFile API |
| `data/AppDatabase.android.kt` | 13 | Platform Impl | Builder de Room con Context de Android |
| `ui/UI.android.kt` | 318 | Platform Impl | `actual` de BackHandler, ImagePicker, SignatureDialog, PdfPreview, FolderSelector |
| `ui/PlatformUtils.android.kt` | 31 | Platform Impl | Toast, FileProvider para compartir PDF, callbacks de almacenamiento |

### desktopMain — Implementaciones Desktop (1,026 líneas | 16%)

| Archivo | Líneas | Capa | Descripción |
|---|---|---|---|
| `Main.kt` | 27 | Entry Point | Inyecta implementaciones Desktop, lanza `singleWindowApplication` |
| `data/DesktopPdfGenerator.kt` | 400 | Platform Impl | Genera PDF usando OpenPDF (iText) |
| `data/DesktopFolderSyncManager.kt` | 255 | Platform Impl | Sincronización usando `java.util.prefs.Preferences` y File I/O |
| `data/AppDatabase.desktop.kt` | 13 | Platform Impl | Builder de Room con ruta `user.home` |
| `ui/UI.desktop.kt` | 306 | Platform Impl | `actual` de BackHandler (no-op), ImagePicker (JFileChooser), SignatureDialog, PdfPreview, FolderSelector |
| `ui/PlatformUtils.desktop.kt` | 38 | Platform Impl | JOptionPane para mensajes, Desktop API para abrir archivos, JFileChooser para guardar |

---

## 3. Evaluación MVVM

### Veredicto: Se respeta MVVM — con problemas de escala

| Criterio | Estado | Detalle |
|---|---|---|
| ViewModel único en commonMain | ✅ | `ProjectViewModel` compartido entre Android y Desktop |
| Lógica de negocio fuera de las vistas | ✅ | Las Composables no contienen lógica de dominio |
| Flujo unidireccional de datos | ✅ | `StateFlow → collectAsState → UI → callback → ViewModel` |
| Repository pattern aplicado | ✅ | `ProjectRepository` desacopla acceso a datos del ViewModel |
| Inyección de dependencias plataforma | ✅ | Interfaces + implementaciones inyectadas en el Entry Point |
| ViewModel con demasiadas responsabilidades | ⚠️ | Gestiona estado + serialización JSON + operaciones de archivo |
| Sin modelo de eventos/intenciones | ⚠️ | Llamadas directas `viewModel.metodo()` sin capa de intenciones |
| `ProjectApp.kt` excesivamente grande | ⚠️ | 2,935 líneas en un solo archivo Composable |

### Flujo unidireccional actual

```
Usuario interactúa con Composable
        ↓
lambda callback → viewModel.metodo()
        ↓
ViewModel llama a Repository / SyncManager / PdfGenerator
        ↓
Repository actualiza Room DB o estado interno
        ↓
Flow emite nuevo valor
        ↓
_MutableStateFlow.value = nuevoEstado
        ↓
StateFlow emite (si el valor cambió)
        ↓
collectAsStateWithLifecycle() detecta cambio
        ↓
Composable recompone con nuevo estado
```

---

## 4. Análisis de duplicación entre plataformas

### Resumen de duplicación

| Componente | Android | Desktop | Solapamiento | Riesgo |
|---|---|---|---|---|
| PDF Generation | 554 líneas | 400 líneas | ~60–70% lógica idéntica | 🔴 Alto |
| Folder Sync | 275 líneas | 255 líneas | ~80% lógica idéntica | 🔴 Alto |
| Platform UI Composables | 318 líneas | 306 líneas | Medio (SignatureDialog, PdfPreview) | 🟡 Medio |
| PlatformUtils | 31 líneas | 38 líneas | Bajo (APIs fundamentalmente distintas) | 🟢 Bajo |
| Database builder | 13 líneas | 13 líneas | Solo rutas distintas | 🟢 Trivial |

### Detalle: PDF Generation (🔴 Alta prioridad)

Ambas implementaciones duplican:
- Configuración de márgenes, fuentes y espaciado
- Lógica de renderizado de bloque de texto
- Lógica de renderizado de tabla
- Lógica de renderizado de checklist
- Lógica de renderizado de firma
- Cabecera y pie de página

Solo difieren en:
- API de renderizado (`android.graphics.pdf.PdfDocument` vs `com.lowagie.text.Document`)

### Detalle: Folder Sync (🔴 Alta prioridad)

Ambas implementaciones duplican:
- Máquina de estados de sincronización
- Reporte de progreso
- Serialización/deserialización JSON de metadatos
- Manejo de errores

Solo difieren en:
- Acceso al filesystem (`DocumentFile` SAF vs `java.io.File`)
- Persistencia de ruta (`ContentResolver URI` vs `Preferences`)

---

## 5. Patrones de gestión de estado

| Patrón | Uso | Ubicación |
|---|---|---|
| `MutableStateFlow` | Estado privado mutable | `ProjectViewModel` (campos backing) |
| `StateFlow` | Estado público inmutable expuesto a la UI | `ProjectViewModel` (18+ flujos) |
| `SharedFlow` | Eventos one-shot (uploadSuccess) | `ProjectViewModel._uploadSuccess` |
| `collectAsStateWithLifecycle()` | Suscripción lifecycle-aware en Composables | `ProjectApp.kt` |
| `flatMapLatest()` | Selección reactiva proyecto → detalles | `ProjectViewModel.selectedProject` |
| `combine()` | Estado derivado de múltiples flujos | `ProjectViewModel.isDirty` |
| `Flow` | Streams de datos del Repository | `ProjectRepository` |
| `viewModelScope` | Coroutines lifecycle-safe | Métodos de `ProjectViewModel` |
| `Dispatchers.IO` | Operaciones de DB/archivo en hilo correcto | `ProjectRepository` |

---

## 6. Fortalezas de la arquitectura actual

1. **Excelente abstracción de plataforma** — El patrón expect/actual se usa correctamente en UI, PDF, sync, base de datos y utilidades.
2. **ViewModel único compartido** — No existe duplicación de ViewModels entre plataformas.
3. **Estado reactivo correcto** — `StateFlow`/`MutableStateFlow` con exposición pública inmutable.
4. **Repository desacoplado** — El ViewModel no accede directamente a DAOs ni al filesystem.
5. **UI lifecycle-aware** — `collectAsStateWithLifecycle` previene leaks de memoria.
6. **Alta proporción commonMain** — El 64% del código es compartido, lo que indica buena arquitectura base.

---

## 7. Problemas identificados

### Problema 1 — Duplicación crítica: Generación de PDF
- **Impacto:** ~350 líneas duplicadas en dos archivos
- **Riesgo:** Un cambio en el diseño del PDF (fuente, márgenes, nuevo tipo de bloque) requiere editar dos archivos simultáneamente, con riesgo de inconsistencias visuales entre plataformas.

### Problema 2 — Duplicación crítica: Folder Sync
- **Impacto:** ~200 líneas duplicadas en dos archivos
- **Riesgo:** Bugs en la lógica de sincronización pueden manifestarse solo en una plataforma si se corrigen en un solo archivo.

### Problema 3 — `ProjectApp.kt` es un God Composable
- **Impacto:** 2,935 líneas en un solo archivo
- **Riesgo:** Imposible navegar, testear o modificar pantallas individualmente. El tiempo de compilación incremental se degrada.

### Problema 4 — `ProjectViewModel` hace demasiado
- **Impacto:** Instancia adaptadores Moshi directamente; gestiona `copyImageToLocalFile` y `saveSignatureToLocalFile`
- **Riesgo:** El ViewModel no debería conocer el mecanismo de serialización ni el filesystem. Dificulta el testing unitario.

### Problema 5 — Sin modelo de eventos/intenciones (UiIntent)
- **Impacto:** Las Composables llaman métodos del ViewModel directamente
- **Riesgo:** A medida que crece la funcionalidad, el contrato entre View y ViewModel se vuelve implícito y difícil de seguir.

---

## 8. Plan de refactoring recomendado

### Prioridad 1 — Extraer lógica de PDF a commonMain

```
Crear: commonMain/data/PdfLayoutEngine.kt
  • Define estructura de datos: PdfPage, PdfBlock, PdfStyle (platform-agnostic)
  • Calcula layout: márgenes, posiciones, saltos de página
  • Retorna: List<RenderedPage> con instrucciones de dibujo

Modificar:
  • AndroidPdfGenerator: solo convierte RenderedPage → android.graphics.pdf
  • DesktopPdfGenerator: solo convierte RenderedPage → com.lowagie.text

Ahorro estimado: ~300 líneas de duplicación eliminadas
```

### Prioridad 2 — Extraer lógica de Sync a commonMain

```
Crear: commonMain/data/FolderSyncOrchestrator.kt
  • Máquina de estados de sincronización
  • Reporte de progreso
  • Serialización JSON de metadatos
  • Acepta: FolderAccessor (interface expect/actual para acceso al filesystem)

Modificar:
  • AndroidFolderSyncManager: implementa FolderAccessor con DocumentFile
  • DesktopFolderSyncManager: implementa FolderAccessor con java.io.File

Ahorro estimado: ~200 líneas de duplicación eliminadas
```

### Prioridad 3 — Partir `ProjectApp.kt`

```
Crear en commonMain/ui/:
  • screens/DashboardScreen.kt   — Lista de proyectos
  • screens/EditorScreen.kt      — Editor de bloques
  • screens/PdfPreviewScreen.kt  — Visor y exportación de PDF
  • navigation/AppNavigation.kt  — Lógica de navegación entre pantallas

Mantener:
  • ProjectApp.kt como punto de entrada ligero (~100 líneas)
```

### Prioridad 4 — Limpiar responsabilidades del ViewModel

```
Mover al Repository:
  • Instanciación de adaptadores Moshi
  • copyImageToLocalFile()
  • saveSignatureToLocalFile()
  • Lógica de creación de plantillas

Resultado: ViewModel se convierte en puro orquestador de estado
```

### Prioridad 5 — Introducir UiIntent (opcional, largo plazo)

```kotlin
// Ejemplo de estructura
sealed class ProjectIntent {
    data class SelectProject(val id: Long) : ProjectIntent()
    data class AddBlock(val type: BlockType) : ProjectIntent()
    object SaveDraft : ProjectIntent()
    object ExportPdf : ProjectIntent()
}

// ViewModel expone un solo punto de entrada
fun onIntent(intent: ProjectIntent) { ... }
```

---

## 9. Resumen ejecutivo

| Métrica | Valor |
|---|---|
| Total de archivos fuente | 23 |
| Total de líneas (no build/test) | 6,214 |
| % código en commonMain | 64% |
| Líneas duplicadas estimadas (PDF + Sync) | ~500 líneas (~8%) |
| Archivos ViewModel | 1 (compartido) |
| Archivos con God Object pattern | 2 (`ProjectApp.kt`, `ProjectViewModel.kt`) |

**Conclusión:** La arquitectura base es sólida. El patrón MVVM se respeta, el ViewModel es único y compartido, y el 64% del código ya vive en `commonMain`. Los principales riesgos son la duplicación en la generación de PDFs y la sincronización de carpetas, y el crecimiento descontrolado de `ProjectApp.kt`. Con los refactors de Prioridad 1 y 2, se eliminarían ~500 líneas de código duplicado y se reduciría significativamente el riesgo de inconsistencias entre plataformas.
