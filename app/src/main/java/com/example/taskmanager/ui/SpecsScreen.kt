package com.example.taskmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.taskmanager.DeviceSpecs
import com.example.taskmanager.ui.components.ExpressiveCard

import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecsScreen(specs: DeviceSpecs?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Specifications", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
        ) {
            item { SpecItem("Model", specs?.modelName ?: "Unknown") }
            item { SpecItem("Processor", specs?.processorName ?: "Unknown") }
            item { SpecItem("GPU", specs?.gpuName ?: "Unknown") }
            item { SpecItem("RAM", formatBytes(specs?.totalRam ?: 0L)) }
            item { SpecItem("Storage", formatBytes(specs?.totalStorage ?: 0L)) }
            item { SpecItem("Android Version", specs?.androidVersion ?: "Unknown") }
        }
    }
}

@Composable
fun SpecItem(label: String, value: String) {
    ExpressiveCard(modifier = Modifier.padding(vertical = 4.dp)) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
