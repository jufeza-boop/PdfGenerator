package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*

@Composable
fun TableEditorForm(
    content: TableBlockContent,
    onSave: (TableBlockContent) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(content.title) }
    var headers by remember { mutableStateOf(content.headers) }
    var rows by remember { mutableStateOf(content.rows) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título (Opcional)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Encabezados", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            headers.forEachIndexed { idx, h ->
                OutlinedTextField(
                    value = h,
                    onValueChange = { newValue ->
                        headers = headers.toMutableList().apply { set(idx, newValue) }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Col ${idx + 1}") }
                )
            }
            IconButton(onClick = { 
                headers = headers + ""
                rows = rows.map { it + "" }
            }) { Icon(Icons.Default.Add, null) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Filas", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        rows.forEachIndexed { rowIdx, rowCells ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowCells.forEachIndexed { colIdx, cell ->
                    OutlinedTextField(
                        value = cell,
                        onValueChange = { newValue ->
                            val newRows = rows.toMutableList()
                            val newRow = newRows[rowIdx].toMutableList()
                            newRow[colIdx] = newValue
                            newRows[rowIdx] = newRow
                            rows = newRows
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = { rows = rows.toMutableList().apply { removeAt(rowIdx) } }) { 
                    Icon(Icons.Default.Delete, null, tint = Color.Red) 
                }
            }
        }
        
        Button(
            onClick = { rows = rows + listOf(List(headers.size) { "" }) },
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Text("Añadir Fila")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = { onSave(TableBlockContent(title, headers, rows)) }) { Text("Guardar") }
        }
    }
}

@Composable
fun ChecklistEditorForm(
    content: ChecklistBlockContent,
    onSave: (ChecklistBlockContent) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(content.title) }
    var items by remember { mutableStateOf(content.items) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título del Checklist") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        items.forEachIndexed { idx, item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Checkbox(checked = item.checked, onCheckedChange = { checked ->
                    items = items.toMutableList().apply { set(idx, item.copy(checked = checked)) }
                })
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { text ->
                        items = items.toMutableList().apply { set(idx, item.copy(text = text)) }
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { items = items.toMutableList().apply { removeAt(idx) } }) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red)
                }
            }
        }
        
        Button(onClick = { items = items + ChecklistItem("", false) }) {
            Icon(Icons.Default.Add, null)
            Text("Añadir Elemento")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = { onSave(ChecklistBlockContent(title, items)) }) { Text("Guardar") }
        }
    }
}

@Composable
fun ChecklistTableEditorForm(
    content: ChecklistTableBlockContent,
    onSave: (ChecklistTableBlockContent) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(content.title) }
    var headers by remember { mutableStateOf(content.headers) }
    var rows by remember { mutableStateOf(content.rows) }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título de la Tabla de Chequeo") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Estados (Columnas)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            headers.forEachIndexed { idx, h ->
                OutlinedTextField(
                    value = h,
                    onValueChange = { headers = headers.toMutableList().apply { set(idx, it) } },
                    modifier = Modifier.width(80.dp)
                )
            }
            IconButton(onClick = { headers = headers + "Estado" }) { Icon(Icons.Default.Add, null) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Filas de Comprobación", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        rows.forEachIndexed { rowIdx, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.text,
                    onValueChange = { rows = rows.toMutableList().apply { set(rowIdx, row.copy(text = it)) } },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { rows = rows.toMutableList().apply { removeAt(rowIdx) } }) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red)
                }
            }
        }
        
        Button(onClick = { rows = rows + ChecklistTableRow("", -1) }) {
            Icon(Icons.Default.Add, null)
            Text("Añadir Fila")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = { onSave(ChecklistTableBlockContent(title, headers, rows)) }) { Text("Guardar") }
        }
    }
}
