package com.lutfan.photogallery.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.data.PhotoSource
import com.lutfan.photogallery.ui.components.FloatingNavBar
import com.lutfan.photogallery.ui.components.MediaAccess
import com.lutfan.photogallery.ui.components.Tab
import com.lutfan.photogallery.ui.components.mediaAccess
import com.lutfan.photogallery.ui.components.mediaPermissions
import com.lutfan.photogallery.ui.editor.EditorScreen
import com.lutfan.photogallery.ui.screens.AlbumScreen
import com.lutfan.photogallery.ui.screens.AlbumsScreen
import com.lutfan.photogallery.ui.screens.FavoritesScreen
import com.lutfan.photogallery.ui.screens.HomeScreen
import com.lutfan.photogallery.ui.screens.OnboardingScreen
import com.lutfan.photogallery.ui.screens.ProfileScreen
import com.lutfan.photogallery.ui.screens.SearchScreen
import com.lutfan.photogallery.ui.screens.ViewerScreen
import com.lutfan.photogallery.ui.theme.Palette

object Routes {
    const val HOME = "home"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"
    const val ALBUMS = "albums"
    const val SEARCH = "search"
    fun album(id: Long) = "album/$id"
    fun viewer(source: PhotoSource, id: Long) = "viewer/${source.encode()}/$id"
    fun editor(id: Long) = "editor/$id"
}

@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.mediaAccess()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        access = context.mediaAccess()
        vm.refresh()
    }
    val requestAccess = { permissionLauncher.launch(mediaPermissions) }

    LifecycleResumeEffect(Unit) {
        val now = context.mediaAccess()
        if (now != access) access = now
        if (now != MediaAccess.None) vm.refresh(debounce = false)
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(Palette.Paper)) {
        if (access == MediaAccess.None) {
            OnboardingScreen(onAllow = requestAccess)
        } else {
            MainNav(vm, access, requestAccess)
        }
    }
}

@Composable
private fun MainNav(vm: GalleryViewModel, access: MediaAccess, requestAccess: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (!ok && uri != null) vm.repo.deleteOwned(uri)
        pendingCameraUri = null
        vm.refresh()
    }
    val openCamera = {
        val uri = vm.repo.createCameraUri()
        if (uri == null) {
            Toast.makeText(context, "Couldn't create photo file", Toast.LENGTH_SHORT).show()
        } else {
            pendingCameraUri = uri
            try {
                cameraLauncher.launch(uri)
            } catch (e: ActivityNotFoundException) {
                vm.repo.deleteOwned(uri)
                Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val selectedTab = when (route) {
        Routes.HOME -> Tab.Home
        Routes.FAVORITES -> Tab.Favorites
        Routes.PROFILE -> Tab.Profile
        else -> null
    }

    // Dark screens (viewer/editor) want light status-bar icons.
    val darkScreen = route?.startsWith("viewer") == true || route?.startsWith("editor") == true
    StatusBarIcons(darkIcons = !darkScreen)

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    partialAccess = access == MediaAccess.Partial,
                    onManageAccess = requestAccess,
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onAlbum = { nav.navigate(Routes.album(it)) },
                    onSeeMore = { nav.navigate(Routes.ALBUMS) },
                    onPhoto = { nav.navigate(Routes.viewer(PhotoSource.All, it)) },
                )
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(vm) { nav.navigate(Routes.viewer(PhotoSource.Favorites, it)) }
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    vm = vm,
                    partialAccess = access == MediaAccess.Partial,
                    onManageAccess = requestAccess,
                    onAlbums = { nav.navigate(Routes.ALBUMS) },
                )
            }
            composable(Routes.ALBUMS) {
                AlbumsScreen(vm, onBack = { nav.popBackStack() }, onAlbum = { nav.navigate(Routes.album(it)) })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onPhoto = { nav.navigate(Routes.viewer(PhotoSource.All, it)) },
                    onAlbum = { nav.navigate(Routes.album(it)) },
                )
            }
            composable(
                "album/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                val id = e.arguments!!.getLong("id")
                AlbumScreen(
                    vm, id,
                    onBack = { nav.popBackStack() },
                    onPhoto = { nav.navigate(Routes.viewer(PhotoSource.InAlbum(id), it)) },
                )
            }
            composable(
                "viewer/{source}/{id}",
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType },
                ),
            ) { e ->
                ViewerScreen(
                    vm = vm,
                    source = PhotoSource.decode(e.arguments!!.getString("source")!!),
                    startId = e.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editor(it)) },
                )
            }
            composable(
                "editor/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                EditorScreen(
                    vm = vm,
                    photoId = e.arguments!!.getLong("id"),
                    onClose = { nav.popBackStack() },
                    onSaved = { newId ->
                        nav.popBackStack()
                        if (newId != null) nav.navigate(Routes.viewer(PhotoSource.All, newId))
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = selectedTab != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            FloatingNavBar(
                selected = selectedTab ?: Tab.Home,
                onSelect = { tab ->
                    val target = when (tab) {
                        Tab.Home -> Routes.HOME
                        Tab.Favorites -> Routes.FAVORITES
                        Tab.Profile -> Routes.PROFILE
                        Tab.Camera -> null
                    }
                    if (target == null) openCamera()
                    else if (target != route) nav.navigate(target) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

@Composable
private fun StatusBarIcons(darkIcons: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(darkIcons) {
        val window = (activity as Activity).window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
        onDispose { }
    }
}
