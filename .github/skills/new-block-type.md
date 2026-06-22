# Skill: new-block-type

Checklist completo para añadir un nuevo tipo de bloque al sistema de informes PDF.

## Cuándo usar este skill

Invoca este skill cuando vayas a implementar un nuevo `BlockType` (ej. `VIDEO`, `MAP`, `RATING`).

## Decisión previa: ¿el bloque necesita contenido estructurado?

- **Sí** (datos con múltiples campos, como TABLE o CHECKLIST) → crea una data class + adaptador Moshi.
- **No** (texto plano, como TEXT o TITLE) → usa el campo `content: String` directamente.

---

## Checklist de implementación

### Paso 1 — Tipo y modelo de datos

- [ ] Añadir el nuevo valor al enum `BlockType` en `AppDatabase.kt:9`
  ```kotlin
  enum class BlockType {
      TEXT, IMAGE, ..., MI_NUEVO_BLOQUE
  }
  ```
- [ ] *(Si contenido estructurado)* Crear la data class en `Models.kt` con `@JsonClass(generateAdapter = true)`
- [ ] *(Si contenido estructurado)* Registrar el adaptador Moshi en `ProjectViewModel.kt:20-23`
  ```kotlin
  private val miNuevoAdapter = moshi.adapter(MiNuevoBloqueContent::class.java)
  ```

### Paso 2 — ViewModel

- [ ] Añadir método `addMiNuevoBloqueBlock()` en `ProjectViewModel.kt` siguiendo el patrón de los métodos existentes (líneas 177-326)

### Paso 3 — UI: menú de bloques

- [ ] Añadir botón en la **barra de herramientas principal** de `ProjectApp.kt:940-988`
- [ ] Añadir chip en la **barra de visitas** de `ProjectApp.kt:1862-1898`
- [ ] Añadir parámetro `onAddMiNuevoBloqueBlock` a la función `ProjectEditorContent` (firma en línea 636)

### Paso 4 — UI: renderizado del bloque

- [ ] Añadir caso al `when` de **badge/icono** en `ProjectApp.kt:2155`
  ```kotlin
  BlockType.MI_NUEVO_BLOQUE -> Triple(Icons.Default.XYZ, "ETIQUETA", MaterialTheme.colorScheme.primary)
  ```
- [ ] Añadir caso al `when` de **renderizado en editor** en `ProjectApp.kt:2293`

### Paso 5 — PDF Desktop (OpenPDF)

- [ ] Añadir caso al `when` de `addBlockToContainer()` en `DesktopPdfGenerator.kt:197`
- [ ] Aplicar el skill `pdf-parity` para sincronizar con el motor Android

### Paso 6 — PDF Android (Canvas)

- [ ] Añadir caso al `when` de `getRequiredHeight()` en `AndroidPdfGenerator.kt:209`
  - Calcula la altura máxima que puede ocupar el bloque en puntos (pt)
- [ ] Añadir caso al `when` de `drawBlock()` en `AndroidPdfGenerator.kt:260`

---

## Verificación final

- [ ] Todos los `when` sobre `BlockType` son exhaustivos (sin `else` que enmascare el nuevo tipo)
- [ ] El bloque se visualiza correctamente en el editor de la app
- [ ] El bloque aparece en el PDF generado en Desktop
- [ ] El bloque aparece en el PDF generado en Android
- [ ] Si soporta `isHalfWidth = true`, se renderiza en columna doble en ambos motores

## Archivos modificados (resumen)

| Archivo | Cambios |
|---|---|
| `commonMain/data/AppDatabase.kt` | Enum `BlockType` |
| `commonMain/data/Models.kt` | Data class de contenido (si aplica) |
| `commonMain/viewmodel/ProjectViewModel.kt` | Adapter Moshi + método `add...Block()` |
| `commonMain/ui/ProjectApp.kt` | Botón, chip, badge `when`, editor `when`, parámetro función |
| `desktopMain/data/DesktopPdfGenerator.kt` | `when` en `addBlockToContainer()` |
| `androidMain/data/AndroidPdfGenerator.kt` | `when` en `getRequiredHeight()` y `drawBlock()` |
