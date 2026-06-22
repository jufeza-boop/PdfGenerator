# Skill: pdf-parity

Verifica que los dos motores de generación de PDF estén sincronizados tras un cambio de layout.

## Cuándo usar este skill

Invoca este skill cuando:
- Modifiques el renderizado de cualquier tipo de bloque en `DesktopPdfGenerator.kt` o `AndroidPdfGenerator.kt`
- Cambies márgenes, tamaños de fuente, colores o espaciado
- Añadas o elimines un tipo de bloque

## Archivos clave

| Motor | Archivo |
|---|---|
| Desktop (OpenPDF) | `composeApp/src/desktopMain/kotlin/com/example/data/DesktopPdfGenerator.kt` |
| Android (Canvas) | `composeApp/src/androidMain/kotlin/com/example/data/AndroidPdfGenerator.kt` |

## Checklist de paridad

Para cada cambio, verifica que el valor/lógica esté sincronizado en **ambos archivos**:

### Constantes de layout (deben ser idénticas)
- [ ] Márgenes: `marginX = 54f`, `marginBottom = 60f`
- [ ] Dimensiones A4: 595×842 pt
- [ ] Padding half-width: `usableWidth/2 - 6f`
- [ ] Tamaños de fuente: body 12f, title 16f, mainTitle 22f, table headers 11f, footer 9f
- [ ] Colores RGB: dark gray (31,41,55), medium gray (55,65,81), border (229,231,235), bg (243,244,246)

### Por tipo de bloque modificado
- [ ] TEXT: tamaño fuente, color, espaciado posterior
- [ ] TITLE: fuente bold, color
- [ ] FOOTER: fuente, color (nota: Desktop usa italic, Android no — es intencional)
- [ ] IMAGE: escala, centrado, borde
- [ ] SIGNATURE: división por `|`, dimensiones max (150f Desktop / 180f Android), fallback sin imagen
- [ ] TABLE: anchos de columna, altura de fila (22f), colores de cabecera
- [ ] CHECKLIST: tamaño checkbox (8.5f), posición texto (+16f)
- [ ] CHECKLIST_TABLE: split de columnas (65%/35%), altura cabecera (22f), tamaño checkbox (12f)

## Divergencias intencionales — NO corregir

Estas diferencias son correctas por las limitaciones de cada motor:

| Aspecto | Desktop | Android |
|---|---|---|
| Gestión de páginas | Automática (Document.add) | Manual (dry-run pre-cálculo) |
| Evento de cabecera | `PdfPageEventHelper` al final de página | `drawCompanyHeader()` al inicio |
| Fallback de firma faltante | `LineSeparator()` | Rectángulo + línea canvas |
| Notas de visita | No incluidas | Añadidas como bloque TEXT |

## Proceso

1. Identifica qué bloque(s) y constantes cambiaron en el archivo que editaste.
2. Abre el otro generador y aplica el cambio equivalente adaptado a su API (OpenPDF vs Canvas).
3. Marca cada ítem del checklist anterior.
4. Si añades un **nuevo tipo de bloque**: impleméntalo en ambos archivos antes de declararlo completo.
