package com.konstantyp.gymcal.ui.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.data.WorkoutType
import com.konstantyp.gymcal.ui.theme.LocalTypeColorMap
import com.konstantyp.gymcal.ui.theme.WorkoutPushSeed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypesScreen(
    types: List<WorkoutType>,
    onBack: () -> Unit,
    onAdd: suspend (name: String, seedArgb: Long) -> Result<Unit>,
    onUpdate: suspend (id: String, name: String, seedArgb: Long) -> Result<Unit>,
    onDelete: suspend (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var editorMode by remember { mutableStateOf<EditorMode?>(null) }
    var pendingDelete by remember { mutableStateOf<WorkoutType?>(null) }
    val colorMap = LocalTypeColorMap.current

    // Full-screen TypeEditor replaces the list — same surface for name + color picker
    // (no AlertDialog + ModalBottomSheet stacking / z-order trap).
    editorMode?.let { mode ->
        TypeEditorScreen(
            mode = mode,
            existingNames = types.map { it.name },
            onDismiss = { editorMode = null },
            onSave = { name, seed ->
                val result = when (mode) {
                    is EditorMode.Create -> onAdd(name, seed)
                    is EditorMode.Edit -> onUpdate(mode.type.id, name, seed)
                }
                if (result.isSuccess) {
                    editorMode = null
                }
                result
            },
            scope = scope,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.types_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            if (types.size < WorkoutType.MAX_TYPES) {
                FloatingActionButton(
                    onClick = {
                        editorMode = EditorMode.Create(
                            defaultSeed = WorkoutPushSeed.toArgb().toLong() and 0xFFFFFFFFL,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_type))
                }
            }
        },
    ) { innerPadding ->
        if (types.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.types_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        editorMode = EditorMode.Create(
                            defaultSeed = WorkoutPushSeed.toArgb().toLong() and 0xFFFFFFFFL,
                        )
                    },
                ) {
                    Text(stringResource(R.string.add_first_type))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(types, key = { it.id }) { type ->
                    val runtime = colorMap[type.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                editorMode = EditorMode.Edit(type)
                            }
                            .padding(vertical = 8.dp)
                            .semantics { contentDescription = type.name },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(runtime?.container ?: Color.Gray),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = type.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { pendingDelete = type },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.cd_delete_type, type.name),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { type ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_type_title)) },
            text = {
                Text(stringResource(R.string.delete_type_body, type.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onDelete(type.id)
                            pendingDelete = null
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private sealed class EditorMode {
    data class Create(val defaultSeed: Long) : EditorMode()
    data class Edit(val type: WorkoutType) : EditorMode()
}

/**
 * Full-screen type editor: name field + inline [TypeColorPickerContent] on one Scaffold.
 * No nested AlertDialog / ModalBottomSheet — avoids the z-order trap where the picker
 * opened behind the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeEditorScreen(
    mode: EditorMode,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: suspend (name: String, seedArgb: Long) -> Result<Unit>,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val initialName = when (mode) {
        is EditorMode.Create -> ""
        is EditorMode.Edit -> mode.type.name
    }
    val initialSeed = when (mode) {
        is EditorMode.Create -> mode.defaultSeed
        is EditorMode.Edit -> mode.type.seedArgb
    }
    var name by remember(mode) { mutableStateOf(initialName) }
    var seedArgb by remember(mode) { mutableLongStateOf(initialSeed) }
    var error by remember { mutableStateOf<String?>(null) }
    val emptyName = stringResource(R.string.error_name_empty)
    val nameMax = stringResource(R.string.error_name_max, WorkoutType.NAME_MAX_LEN)
    val nameExists = stringResource(R.string.error_name_exists)

    fun trySave() {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> error = emptyName
            trimmed.length > WorkoutType.NAME_MAX_LEN ->
                error = nameMax
            existingNames.any {
                it.equals(trimmed, ignoreCase = true) &&
                    (mode !is EditorMode.Edit || !it.equals(mode.type.name, ignoreCase = true))
            } -> error = nameExists
            else -> {
                scope.launch {
                    val result = onSave(trimmed, seedArgb)
                    result.exceptionOrNull()?.message?.let { error = it }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (mode is EditorMode.Create) stringResource(R.string.new_type) else stringResource(R.string.edit_type),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { trySave() }) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= WorkoutType.NAME_MAX_LEN) {
                        name = it
                        error = null
                    }
                },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.color_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Inline picker on the same screen — no second sheet/dialog
            TypeColorPickerContent(
                seedArgb = seedArgb,
                onSeedChange = { seedArgb = it },
            )
        }
    }
}
