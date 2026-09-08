package com.konstantyp.gymcal.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.data.WorkoutType
import com.konstantyp.gymcal.ui.theme.LocalTypeColorMap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DayDetailScreen(
    date: LocalDate,
    types: List<WorkoutType>,
    initialTypeId: String?,
    onBack: () -> Unit,
    onSave: (typeId: String) -> Unit,
    onClear: () -> Unit,
    onManageTypes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // If initial type was deleted, treat as null
    val validInitial = initialTypeId?.takeIf { id -> types.any { it.id == id } }
    var selectedId by remember(date, validInitial, types) { mutableStateOf(validInitial) }
    val colorMap = LocalTypeColorMap.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val title = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
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
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.workout_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (types.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_types_mark_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onManageTypes) {
                    Text(stringResource(R.string.add_type))
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val maxChipHeight = maxHeight * 0.4f
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxChipHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        types.forEach { type ->
                            val isSelected = selectedId == type.id
                            val runtime = colorMap[type.id]
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedId = type.id },
                                label = {
                                    Text(
                                        text = type.name,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                                colors = if (isSelected && runtime != null) {
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = runtime.container,
                                        selectedLabelColor = runtime.onContainer,
                                    )
                                } else {
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                border = if (isSelected) {
                                    null
                                } else {
                                    FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = false,
                                        borderColor = MaterialTheme.colorScheme.outline,
                                    )
                                },
                                modifier = Modifier.semantics { contentDescription = type.name },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onManageTypes) {
                    Text(
                        text = stringResource(R.string.manage_types),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { selectedId?.let(onSave) },
                enabled = selectedId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.save),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (validInitial != null || selectedId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.clear_day),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
