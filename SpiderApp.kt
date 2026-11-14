package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.DocumentBackgroundMode
import com.example.ui.screens.AssetsScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NewDocumentScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderCharcoal
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.StudioViewModel

object SpiderRoutes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val NEW_DOCUMENT = "new_document"
    const val TEMPLATES = "templates"
    const val ASSETS = "assets"
    const val PROJECTS = "projects"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}?selected={selected}"

    fun editorRoute(projectId: String) = "editor/$projectId"
    fun exportRoute(projectId: String, selectedIds: Set<String> = emptySet()): String {
        val sel = selectedIds.joinToString(",")
        return "export/$projectId?selected=$sel"
    }
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val topNavItems = listOf(
    TopNavItem(SpiderRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    TopNavItem(SpiderRoutes.TEMPLATES, "Plantillas", Icons.Filled.DashboardCustomize, Icons.Outlined.DashboardCustomize),
    TopNavItem(SpiderRoutes.ASSETS, "Recursos", Icons.Filled.Category, Icons.Outlined.Category),
    TopNavItem(SpiderRoutes.PROJECTS, "Proyectos", Icons.Filled.Folder, Icons.Outlined.Folder),
    TopNavItem(SpiderRoutes.SETTINGS, "Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun SpiderApp(
    studioViewModel: StudioViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val projects by studioViewModel.projects.collectAsStateWithLifecycle()
    val palettes by studioViewModel.palettes.collectAsStateWithLifecycle()
    val customFonts by studioViewModel.customFonts.collectAsStateWithLifecycle()
    val chromeStyles by studioViewModel.chromeStyles.collectAsStateWithLifecycle()
    val preferences by studioViewModel.preferences.collectAsStateWithLifecycle()
    val statusMessage by studioViewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingInsertAssetId by remember { mutableStateOf<String?>(null) }
    var initializedStart by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            studioViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(preferences.showWelcomeOnLaunch) {
        if (!initializedStart && !preferences.showWelcomeOnLaunch && currentRoute == SpiderRoutes.WELCOME) {
            initializedStart = true
            navController.navigate(SpiderRoutes.HOME) {
                popUpTo(SpiderRoutes.WELCOME) { inclusive = true }
            }
        } else {
            initializedStart = true
        }
    }

    val importProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            studioViewModel.importFileAsProject(it) { projectId ->
                navController.navigate(SpiderRoutes.editorRoute(projectId))
            }
        }
    }

    val showShellNav = currentRoute in topNavItems.map { it.route }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
    ) {
        val useNavigationRail = maxWidth >= 600.dp
        val isWideScreen = maxWidth >= 840.dp

        Scaffold(
            containerColor = SpiderBlack,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showShellNav && !useNavigationRail) {
                    NavigationBar(containerColor = SpiderCharcoal) {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(SpiderRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SpiderVioletSoft,
                                    selectedTextColor = SpiderVioletSoft,
                                    indicatorColor = SpiderViolet.copy(alpha = 0.2f),
                                    unselectedIconColor = SpiderTextSecondary,
                                    unselectedTextColor = SpiderTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showShellNav && useNavigationRail) {
                    NavigationRail(
                        containerColor = SpiderCharcoal,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(SpiderRoutes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = SpiderVioletSoft,
                                    selectedTextColor = SpiderVioletSoft,
                                    indicatorColor = SpiderViolet.copy(alpha = 0.2f),
                                    unselectedIconColor = SpiderTextSecondary,
                                    unselectedTextColor = SpiderTextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (showShellNav) Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            else Modifier
                        )
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = SpiderRoutes.WELCOME
                    ) {
                        composable(SpiderRoutes.WELCOME) {
                            WelcomeScreen(
                                lastProjectId = preferences.lastOpenedProjectId?.takeIf { lastId ->
                                    projects.any { it.id == lastId }
                                },
                                onEnterStudio = {
                                    navController.navigate(SpiderRoutes.HOME) {
                                        popUpTo(SpiderRoutes.WELCOME) { inclusive = true }
                                    }
                                },
                                onCreateDocument = {
                                    navController.navigate(SpiderRoutes.NEW_DOCUMENT)
                                },
                                onResumeLastProject = { id ->
                                    navController.navigate(SpiderRoutes.editorRoute(id))
                                }
                            )
                        }

                        composable(SpiderRoutes.HOME) {
                            HomeScreen(
                                projects = projects,
                                isWideScreen = isWideScreen,
                                onNewDocument = { navController.navigate(SpiderRoutes.NEW_DOCUMENT) },
                                onOpenTemplates = { navController.navigate(SpiderRoutes.TEMPLATES) },
                                onImportFile = {
                                    importProjectLauncher.launch(
                                        arrayOf(
                                            "image/svg+xml",
                                            "image/png",
                                            "image/jpeg",
                                            "application/pdf",
                                            "application/json",
                                            "*/*"
                                        )
                                    )
                                },
                                onQuickPreset = { w, h, title ->
                                    studioViewModel.createNewDocument(
                                        name = title,
                                        widthPx = w,
                                        heightPx = h,
                                        backgroundMode = DocumentBackgroundMode.SOLID,
                                        backgroundColorArgb = 0xFF14141C
                                    ) { id ->
                                        navController.navigate(SpiderRoutes.editorRoute(id))
                                    }
                                },
                                onOpenProject = { id ->
                                    navController.navigate(SpiderRoutes.editorRoute(id))
                                },
                                onDuplicateProject = { studioViewModel.duplicateProject(it) },
                                onDeleteProject = { studioViewModel.deleteProject(it) }
                            )
                        }

                        composable(SpiderRoutes.NEW_DOCUMENT) {
                            NewDocumentScreen(
                                colorHistory = preferences.colorHistory,
                                palettes = palettes,
                                onBack = { navController.popBackStack() },
                                onCreateDocument = { name, w, h, bgMode, bgColor ->
                                    studioViewModel.createNewDocument(name, w, h, bgMode, bgColor) { id ->
                                        navController.popBackStack()
                                        navController.navigate(SpiderRoutes.editorRoute(id))
                                    }
                                }
                            )
                        }

                        composable(SpiderRoutes.TEMPLATES) {
                            TemplatesScreen(
                                isWideScreen = isWideScreen,
                                onSelectTemplate = { tplId ->
                                    studioViewModel.createFromTemplate(tplId) { id ->
                                        navController.navigate(SpiderRoutes.editorRoute(id))
                                    }
                                }
                            )
                        }

                        composable(SpiderRoutes.ASSETS) {
                            AssetsScreen(
                                isWideScreen = isWideScreen,
                                palettes = palettes,
                                customFonts = customFonts,
                                onInsertAsset = { assetId ->
                                    pendingInsertAssetId = assetId
                                    val targetId = preferences.lastOpenedProjectId?.takeIf { lid ->
                                        projects.any { it.id == lid }
                                    } ?: projects.firstOrNull()?.id
                                    if (targetId != null) {
                                        navController.navigate(SpiderRoutes.editorRoute(targetId))
                                    } else {
                                        studioViewModel.createNewDocument(
                                            name = "Diseño con Recurso",
                                            widthPx = 1080,
                                            heightPx = 1080,
                                            backgroundMode = DocumentBackgroundMode.SOLID,
                                            backgroundColorArgb = 0xFF14141C
                                        ) { id ->
                                            navController.navigate(SpiderRoutes.editorRoute(id))
                                        }
                                    }
                                },
                                onCreatePalette = { name, colors -> studioViewModel.createPalette(name, colors) },
                                onDeletePalette = { studioViewModel.deletePalette(it) },
                                onImportFont = { uri -> studioViewModel.importCustomFont(uri) },
                                onDeleteFont = { studioViewModel.deleteCustomFont(it) }
                            )
                        }

                        composable(SpiderRoutes.PROJECTS) {
                            ProjectsScreen(
                                projects = projects,
                                onNewDocument = { navController.navigate(SpiderRoutes.NEW_DOCUMENT) },
                                onImportFile = {
                                    importProjectLauncher.launch(
                                        arrayOf(
                                            "image/svg+xml",
                                            "image/png",
                                            "image/jpeg",
                                            "application/pdf",
                                            "application/json",
                                            "*/*"
                                        )
                                    )
                                },
                                onOpenProject = { id -> navController.navigate(SpiderRoutes.editorRoute(id)) },
                                onDuplicateProject = { studioViewModel.duplicateProject(it) },
                                onRenameProject = { id, newName -> studioViewModel.renameProject(id, newName) },
                                onExportProject = { id -> navController.navigate(SpiderRoutes.exportRoute(id)) },
                                onDeleteProject = { studioViewModel.deleteProject(it) }
                            )
                        }

                        composable(SpiderRoutes.SETTINGS) {
                            SettingsScreen(
                                preferences = preferences,
                                onUpdateShowWelcome = { studioViewModel.updateShowWelcome(it) },
                                onUpdateAutoSave = { en, sec -> studioViewModel.updateAutoSave(en, sec) },
                                onUpdateCanvasDefaults = { g, gs, r, s, u ->
                                    studioViewModel.updateCanvasDefaults(g, gs, r, s, u)
                                },
                                onUpdateStylusPressure = { studioViewModel.updateStylusPressure(it) },
                                onUpdateExportScale = { studioViewModel.updateDefaultExportScale(it) }
                            )
                        }

                        composable(
                            route = SpiderRoutes.EDITOR,
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                        ) { entry ->
                            val projectId = entry.arguments?.getString("projectId") ?: return@composable
                            val editorVm: EditorViewModel = viewModel()
                            EditorScreen(
                                projectId = projectId,
                                isWideScreen = isWideScreen,
                                preferences = preferences,
                                palettes = palettes,
                                customFonts = customFonts,
                                chromeStyles = chromeStyles,
                                pendingInsertAssetId = pendingInsertAssetId,
                                onClearPendingAsset = { pendingInsertAssetId = null },
                                onSavePaletteColor = { n, cols -> studioViewModel.createPalette(n, cols) },
                                onSaveChromeStyle = { name, config -> studioViewModel.saveChromeStyle(name, config) },
                                onDeleteChromeStyle = { id -> studioViewModel.deleteChromeStyle(id) },
                                onBack = {
                                    val popped = navController.popBackStack()
                                    if (!popped) {
                                        navController.navigate(SpiderRoutes.HOME)
                                    }
                                },
                                onOpenExport = { pid, selIds ->
                                    navController.navigate(SpiderRoutes.exportRoute(pid, selIds))
                                },
                                editorViewModel = editorVm
                            )
                        }

                        composable(
                            route = SpiderRoutes.EXPORT,
                            arguments = listOf(
                                navArgument("projectId") { type = NavType.StringType },
                                navArgument("selected") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { entry ->
                            val projectId = entry.arguments?.getString("projectId") ?: return@composable
                            val selCsv = entry.arguments?.getString("selected").orEmpty()
                            val selSet = selCsv.split(",").filter { it.isNotBlank() }.toSet()
                            ExportScreen(
                                projectId = projectId,
                                selectedIds = selSet,
                                defaultScale = preferences.defaultExportScale,
                                projectRepository = studioViewModel.projectRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
