package com.example.litcompose.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.litcompose.domain.repository.CollectionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionSheet(
    isOpen: Boolean,
    collections: List<CollectionSummary>,
    onDismiss: () -> Unit,
    onCreateCollection: (String) -> Unit,
    onAddToCollection: (Long) -> Unit,
    title: String = "添加到歌单",
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        if (!isOpen) {
            showCreateDialog = false
        }
    }

    if (isOpen) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = title)
                    Button(onClick = { showCreateDialog = true }) {
                        Text("新建歌单")
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))

                if (collections.isEmpty()) {
                    Text(
                        text = "还没有歌单，先新建一个吧",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    collections.forEach { c ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddToCollection(c.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = c.name)
                            Text(text = "${c.trackCount}首")
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateCollection(trimmed)
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                Button(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("歌单名称") },
                )
            },
        )
    }
}
