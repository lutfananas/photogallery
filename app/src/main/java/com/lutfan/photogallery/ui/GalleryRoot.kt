package com.lutfan.photogallery.ui

import android.app.Activity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.lutfan.photogallery.ui.components.LocalGlassBackdrop
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import com.lutfan.photogallery.ui.components.SelectionBar
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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

    val glassBackdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        // Everything drawn here is what the floating glass bars refract/blur.
        SharedTransitionLayout(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
        CompositionLocalProvider(LocalSharedScope provides this) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            // Opening a photo keeps the grid underneath so the photo grows out of it;
            // going back reveals the grid immediately while the photo shrinks into place.
            enterTransition = {
                if (targetState.isViewer()) fadeIn(tween(260)) else fadeIn(tween(220))
            },
            exitTransition = {
                if (targetState.isViewer()) ExitTransition.KeepUntilTransitionsFinished else fadeOut(tween(180))
            },
            popEnterTransition = {
                if (initialState.isViewer()) EnterTransition.None else fadeIn(tween(220))
            },
            popExitTransition = {
                if (initialState.isViewer()) fadeOut(tween(260)) else fadeOut(tween(180))
            },
        ) {
            screen(Routes.HOME) {
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
            screen(Routes.FAVORITES) {
                FavoritesScreen(vm) { nav.navigate(Routes.viewer(PhotoSource.Favorites, it)) }
            }
            screen(Routes.PROFILE) {
                ProfileScreen(
                    vm = vm,
                    partialAccess = access == MediaAccess.Partial,
                    onManageAccess = requestAccess,
                    onAlbums = { nav.navigate(Routes.ALBUMS) },
                )
            }
            screen(Routes.ALBUMS) {
                AlbumsScreen(vm, onBack = { nav.popBackStack() }, onAlbum = { nav.navigate(Routes.album(it)) })
            }
            screen(Routes.SEARCH) {
                SearchScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onPhoto = { nav.navigate(Routes.viewer(PhotoSource.All, it)) },
                    onAlbum = { nav.navigate(Routes.album(it)) },
                )
            }
            screen(
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
            screen(
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
            screen(
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
        }
        }

        CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
        // Leaving a screen ends selection; system back cancels it first.
        LaunchedEffect(route) { vm.clearSelection() }
        BackHandler(enabled = vm.selecting) { vm.clearSelection() }

        AnimatedVisibility(
            visible = vm.selecting,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val selected = vm.selectedPhotos()
            val allFav = selected.isNotEmpty() && selected.all { vm.isFavorite(it.id) }
            SelectionBar(
                count = vm.selection.size,
                allFavorite = allFav,
                onClose = vm::clearSelection,
                onShare = {
                    sharePhotos(context, selected.map { it.uri })
                    vm.clearSelection()
                },
                onFavorite = {
                    vm.setFavorite(selected.map { it.id }, favorite = !allFav)
                    Toast.makeText(
                        context,
                        if (allFav) "Removed ${selected.size} from favorites" else "Added ${selected.size} to favorites",
                        Toast.LENGTH_SHORT,
                    ).show()
                    vm.clearSelection()
                },
            )
        }

        AnimatedVisibility(
            visible = selectedTab != null && !vm.selecting,
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
}

private fun sharePhotos(context: android.content.Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.apply {
        type = "image/*"
        // ClipData makes the read grant cover every shared URI.
        clipData = ClipData.newRawUri(null, uris.first()).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private fun NavBackStackEntry.isViewer() = destination.route?.startsWith("viewer") == true

/** `composable` that also exposes its AnimatedVisibilityScope for shared elements. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavScope provides this) { content(entry) }
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
