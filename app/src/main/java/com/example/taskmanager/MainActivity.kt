package com.example.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.taskmanager.ui.DashboardScreen
import com.example.taskmanager.ui.ProcessListScreen
import com.example.taskmanager.ui.SpecsScreen
import com.example.taskmanager.ui.theme.TaskManagerTheme

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Monitor)
    object Processes : Screen("processes", "Tasks", Icons.AutoMirrored.Filled.List)
    object Specs : Screen("specs", "Specs", Icons.Default.Info)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskManagerTheme {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel()
                
                val items = listOf(
                    Screen.Dashboard,
                    Screen.Processes,
                    Screen.Specs
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    NavHost(
                        navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) +
                            scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(200)
                            )
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                            scaleOut(
                                targetScale = 0.92f,
                                animationSpec = tween(180)
                            )
                        }
                    ) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                stats = viewModel.stats.value,
                                specs = viewModel.deviceSpecs.value,
                                cpuHistory = viewModel.cpuHistory,
                                ramHistory = viewModel.ramHistory,
                                gpu3DHistory = viewModel.gpu3DHistory,
                                gpuCopyHistory = viewModel.gpuCopyHistory,
                                gpuComputeHistory = viewModel.gpuComputeHistory,
                                gpuMemHistory = viewModel.gpuMemHistory,
                                diskHistory = viewModel.diskHistory,
                                netSendHistory = viewModel.netSendHistory,
                                netReceiveHistory = viewModel.netReceiveHistory,
                                isTestingCpu = viewModel.isTestingCpu.value,
                                cpuTestResult = viewModel.cpuTestResult.value,
                                cpuBenchProgress = viewModel.cpuBenchProgress.value,
                                cpuBenchPhase = viewModel.cpuBenchPhase.value,
                                onRunCpuBenchmark = { viewModel.runCpuBenchmark() },
                                isTestingGpu = viewModel.isTestingGpu.value,
                                gpuTestResult = viewModel.gpuTestResult.value,
                                gpuBenchProgress = viewModel.gpuBenchProgress.value,
                                gpuBenchPhase = viewModel.gpuBenchPhase.value,
                                onRunGpuBenchmark = { viewModel.runGpuBenchmark() },
                                isTestingDisk = viewModel.isTestingDisk.value,
                                diskTestResult = viewModel.diskTestResult.value,
                                diskBenchProgress = viewModel.diskBenchProgress.value,
                                diskBenchPhase = viewModel.diskBenchPhase.value,
                                onRunDiskBenchmark = { viewModel.runDiskBenchmark() },
                                topProcesses = viewModel.topProcesses.value
                            )
                        }
                        composable(Screen.Processes.route) {
                            LaunchedEffect(Unit) {
                                viewModel.refreshProcesses()
                            }
                            ProcessListScreen(
                                processes = viewModel.runningProcesses,
                                onRefresh = { viewModel.refreshProcesses() }
                            )
                        }
                        composable(Screen.Specs.route) {
                            SpecsScreen(specs = viewModel.deviceSpecs.value)
                        }
                    }

                    // CUSTOM FLOATING NAV BAR
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .navigationBarsPadding()
                    ) {
                        Surface(
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(64.dp)
                                .shadow(16.dp, RoundedCornerShape(32.dp)),
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items.forEach { screen ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                    
                                    Box(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                            .padding(horizontal = if (selected) 16.dp else 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = null,
                                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            AnimatedVisibility(visible = selected) {
                                                Text(
                                                    text = screen.label,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(start = 8.dp),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
