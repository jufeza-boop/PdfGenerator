# Informe: almacenamiento de datos en PdfGenerator

## Resumen
La app usa un almacenamiento **híbrido**:
- **Base de datos local Room (SQLite)** para metadatos y estructura del contenido.
- **Sistema de archivos interno de Android** para binarios (imágenes y firmas).
- **Cache interna** para PDFs generados.

## 1) Base de datos local (Room)

El almacenamiento estructurado está en `AppDatabase` con nombre:
- `project_manager_db`

Se define en:
- `/tmp/workspace/jufeza-boop/PdfGenerator/app/src/main/java/com/example/data/AppDatabase.kt`

### Tablas

#### `projects`
Representa cada proyecto/informe.
Campos relevantes:
- `id` (PK autogenerada)
- `name`
- `createdAt`
- Configuración de cabecera (`reportLabel`, `showHeaderLabel`, `headerCompany`, etc.)

#### `content_blocks`
Representa bloques de contenido dentro de un proyecto.
Campos relevantes:
- `id` (PK autogenerada)
- `projectId` (FK a `projects.id`)
- `type` (`TEXT`, `IMAGE`, `SIGNATURE`, `TITLE`, `FOOTER`, `TABLE`, `CHECKLIST`)
- `content` (texto o ruta local de archivo)
- `sequence` (orden de renderizado)
- `isHalfWidth`

### Relación entre tablas
- Relación **1:N** (`ProjectEntity` -> `ContentBlockEntity`).
- Hay `ForeignKey(... onDelete = CASCADE)`, por lo que al borrar un proyecto se borran sus bloques en BD.

## 2) Acceso a datos

El DAO (`ProjectDao`) expone:
- Lectura reactiva con `Flow` (`getAllProjectsFlow`, `getProjectByIdFlow`).
- CRUD de proyectos y bloques (`insert/update/delete`).
- Borrado masivo de bloques por proyecto (`deleteBlocksForProject`).

El `ProjectViewModel` consume estos flujos vía `ProjectRepository` y mantiene:
- Estado del proyecto seleccionado.
- Estado de borrador (`draftBlocks`) antes de persistir.
- Persistencia final al guardar (`saveDraft`), que inserta/actualiza/borrar según diferencias.

## 3) Archivos locales (imágenes y firmas)

En lugar de guardar binarios en la BD, se guarda la **ruta absoluta** en `content_blocks.content`.

Implementación principal en:
- `/tmp/workspace/jufeza-boop/PdfGenerator/app/src/main/java/com/example/data/ProjectRepository.kt`

### Ubicaciones
- Imágenes: `filesDir/project_<projectId>_images/`
- Firmas: `filesDir/project_<projectId>_signatures/`

### Flujo
1. Se recibe imagen/firma.
2. Se escribe archivo en almacenamiento interno (`FileOutputStream`).
3. Se persiste en BD un bloque con el path absoluto.

### Borrado
Al eliminar un bloque `IMAGE` o `SIGNATURE`, el repositorio intenta borrar también el archivo físico asociado antes de borrar el registro en BD.

## 4) PDFs generados

Los PDFs se generan en:
- `cacheDir/project_report_<projectId>.pdf`

Esto indica almacenamiento temporal en caché, no como activo persistente principal del proyecto.

## 5) Migraciones y versionado de esquema

`AppDatabase` está en versión `4` y usa:
- `fallbackToDestructiveMigration()`

Implicación:
- Si cambia el esquema sin migración explícita, la BD puede recrearse y perder datos locales.

## 6) Conclusión

El modelo de almacenamiento está diseñado para:
- Mantener metadatos y estructura en SQLite/Room.
- Evitar BLOBs en BD moviendo binarios al filesystem interno.
- Soportar UI reactiva con `Flow`.
- Generar documentos PDF en caché para exportación/visualización.

Es una arquitectura local, simple y efectiva para edición de informes con contenido mixto (texto + multimedia).
