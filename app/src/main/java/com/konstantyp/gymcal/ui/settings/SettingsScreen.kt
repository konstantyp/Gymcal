package com.konstantyp.gymcal.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.data.LocalePreferences
import com.konstantyp.gymcal.widget.GymcalWidgetUpdater
import com.konstantyp.gymcal.data.WorkoutRepository
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedTag: String,
    onLocaleSelected: (String) -> Unit,
    repository: WorkoutRepository,
    onBack: () -> Unit,
    showMotivationQuote: Boolean,
    onShowMotivationQuoteChange: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showReplaceDialog by remember { mutableStateOf(false) }

    val exportFailedMsg = stringResource(R.string.export_failed)
    val exportSuccessMsg = stringResource(R.string.export_success)
    val importFailedMsg = stringResource(R.string.import_failed)
    val importSuccessMsg = stringResource(R.string.import_success)
    val importInvalidMsg = stringResource(R.string.import_invalid_json)

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repository.exportPlanJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                }.isSuccess
            }
            snackbarHostState.showSnackbar(if (ok) exportSuccessMsg else exportFailedMsg)
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                snackbarHostState.showSnackbar(importInvalidMsg)
            } else {
                pendingImportJson = json
                showReplaceDialog = true
            }
        }
    }

    val options = listOf(
        LocalePreferences.TAG_EN to R.string.lang_en,
        LocalePreferences.TAG_PL to R.string.lang_pl,
        LocalePreferences.TAG_DE to R.string.lang_de,
    )

    val exportLabel = stringResource(R.string.export_workout_plan)
    val importLabel = stringResource(R.string.import_workout_plan)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (tag, labelRes) ->
                    val selected = selectedTag == tag
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = selected,
                                onClick = { onLocaleSelected(tag) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable { onLocaleSelected(tag) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_workout_plan),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = {
                    val suggested = "liftloq-plan-" +
                        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                        ".json"
                    scope.launch {
                        val shared = withContext(Dispatchers.IO) {
                            runCatching {
                                val json = repository.exportPlanJson()
                                val dir = File(context.cacheDir, "export").apply { mkdirs() }
                                val file = File(dir, suggested)
                                file.writeText(json, Charsets.UTF_8)
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                            }.getOrNull()
                        }
                        if (shared != null) {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, shared)
                                putExtra(Intent.EXTRA_SUBJECT, suggested)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, exportLabel))
                            snackbarHostState.showSnackbar(exportSuccessMsg)
                        } else {
                            createDocument.launch(suggested)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = exportLabel },
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = exportLabel)
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = {
                    openDocument.launch(arrayOf("application/json", "text/*", "*/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = importLabel },
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = importLabel)
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsNavRow(
                title = stringResource(R.string.stats_row),
                onClick = onOpenStats,
            )
            SettingsNavRow(
                title = stringResource(R.string.about_row),
                onClick = onOpenAbout,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_show_quote),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_show_quote_supporting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showMotivationQuote,
                    onCheckedChange = onShowMotivationQuoteChange,
                )
            }
        }
    }

    if (showReplaceDialog && pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = {
                showReplaceDialog = false
                pendingImportJson = null
            },
            title = { Text(stringResource(R.string.import_replace_title)) },
            text = { Text(stringResource(R.string.import_replace_body)) },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        val json = pendingImportJson
                        showReplaceDialog = false
                        pendingImportJson = null
                        if (json == null) return@FilledTonalButton
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                repository.importPlanJson(json)
                            }
                            if (result.isSuccess) {
                                GymcalWidgetUpdater.requestUpdateAsync(context)
                            }
                            snackbarHostState.showSnackbar(
                                if (result.isSuccess) {
                                    importSuccessMsg
                                } else {
                                    result.exceptionOrNull()?.message ?: importFailedMsg
                                },
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.import_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showReplaceDialog = false
                        pendingImportJson = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
