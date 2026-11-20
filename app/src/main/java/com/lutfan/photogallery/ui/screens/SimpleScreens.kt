package com.lutfan.photogallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.data.Photo
import com.lutfan.photogallery.ui.components.CircleThumb
import com.lutfan.photogallery.ui.components.EmptyState
import com.lutfan.photogallery.ui.components.PhotoTile
import com.lutfan.photogallery.ui.theme.Palette

/* ---------- Onboarding (no permission yet) ---------- */

@Composable
fun OnboardingScreen(onAllow: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Cream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
    ) {
        Spacer(Modifier.weight(1f))
        // Collage of soft tiles as a simple illustration.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(26.dp)).background(Palette.Charcoal))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp)).background(Palette.Orange))
                Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(26.dp)).background(Palette.Line))
            }
        }
        Spacer(Modifier.height(40.dp))
        Text("Your photos,\nbeautifully kept.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Allow access to your photos to browse, favorite and edit them. Nothing leaves your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Orange)
                .clickable(onClick = onAllow),
            contentAlignment = Alignment.Center,
        ) {
            Text("Allow access", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/* ---------- Shared header with back arrow ---------- */

@Composable
fun BackHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
    }
}

@Composable
private fun PhotoStagger(
    photos: List<Photo>,
    vm: GalleryViewModel,
    header: (@Composable () -> Unit)? = null,
    empty: @Composable () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
    ) {
        if (header != null) item(span = StaggeredGridItemSpan.FullLine) { header() }
        if (photos.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) { empty() }
        items(photos, key = { it.id }) { p ->
            PhotoTile(
                p,
                favorite = vm.isFavorite(p.id),
                selectionMode = vm.selecting,
                selected = p.id in vm.selection,
                onLongClick = { vm.toggleSelect(p.id) },
            ) { if (vm.selecting) vm.toggleSelect(p.id) else onPhoto(p.id) }
        }
    }
}

/* ---------- Favorites ---------- */

@Composable
fun FavoritesScreen(vm: GalleryViewModel, onPhoto: (Long) -> Unit) {
    val favs = vm.photos.filter { it.id in vm.favorites }
    Box(Modifier.fillMaxSize().background(Palette.Paper)) {
        PhotoStagger(
            favs, vm,
            header = {
                Column(Modifier.statusBarsPadding().padding(top = 28.dp, bottom = 8.dp)) {
                    Text("Favorites", style = MaterialTheme.typography.displaySmall)
                    Text("${favs.size} photos", color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            },
            empty = {
                EmptyState(Icons.Outlined.FavoriteBorder, "No favorites yet", "Tap the heart on any photo to keep it here.")
            },
            onPhoto = onPhoto,
        )
    }
}

/* ---------- Album detail ---------- */

@Composable
fun AlbumScreen(vm: GalleryViewModel, bucketId: Long, onBack: () -> Unit, onPhoto: (Long) -> Unit) {
    val photos = vm.photos.filter { it.bucketId == bucketId }
    val name = photos.firstOrNull()?.bucketName ?: "Album"
    Column(Modifier.fillMaxSize().background(Palette.Paper)) {
        BackHeader(name, "${photos.size} photos", onBack)
        PhotoStagger(
            photos, vm,
            empty = { EmptyState(Icons.Outlined.PhotoLibrary, "Empty album", "Nothing here anymore.") },
            onPhoto = onPhoto,
        )
    }
}

/* ---------- All albums ---------- */

@Composable
fun AlbumsScreen(vm: GalleryViewModel, onBack: () -> Unit, onAlbum: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().background(Palette.Paper)) {
        BackHeader("Albums", "${vm.albums.size} albums", onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(vm.albums, key = { it.id }) { album ->
                Column(Modifier.clickable { onAlbum(album.id) }) {
                    AsyncImage(
                        model = album.cover,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Palette.Line),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${album.count} photos", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
            }
        }
    }
}

/* ---------- Search ---------- */

@Composable
fun SearchScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onPhoto: (Long) -> Unit,
    onAlbum: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim()
    val results = if (q.isEmpty()) emptyList() else vm.photos.filter {
        it.name.contains(q, ignoreCase = true) || it.bucketName.contains(q, ignoreCase = true)
    }
    val albums = if (q.isEmpty()) vm.albums else vm.albums.filter { it.name.contains(q, ignoreCase = true) }

    Column(Modifier.fillMaxSize().background(Palette.Paper)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search photos & albums") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Palette.Orange,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        if (albums.isNotEmpty()) {
            Text(
                "Albums",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowItems(albums, key = { it.id }) { a -> CircleThumb(a.name, a.cover) { onAlbum(a.id) } }
            }
        }
        if (q.isNotEmpty()) {
            Text(
                "${results.size} photos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 12.dp),
            )
            PhotoStagger(
                results, vm,
                empty = { EmptyState(Icons.Outlined.Search, "No matches", "Try a file or album name.") },
                onPhoto = onPhoto,
            )
        }
    }
}

/* ---------- Profile ---------- */

@Composable
fun ProfileScreen(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onManageAccess: () -> Unit,
    onAlbums: () -> Unit,
) {
    val totalBytes = vm.photos.sumOf { it.size }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(Palette.Paper),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val cover = vm.photos.firstOrNull()?.uri
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(Palette.OrangeSoft),
                )
                Spacer(Modifier.height(14.dp))
                Text("My Gallery", style = MaterialTheme.typography.headlineSmall)
                Text("On this device", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
        item { StatCard("Photos", vm.photos.size.toString(), Icons.Outlined.PhotoLibrary) }
        item { StatCard("Albums", vm.albums.size.toString(), Icons.Outlined.Collections) }
        item { StatCard("Favorites", vm.favorites.count { id -> vm.photos.any { it.id == id } }.toString(), Icons.Outlined.FavoriteBorder) }
        item { StatCard("Storage", formatBytes(totalBytes), Icons.Outlined.Storage) }
        item(span = { GridItemSpan(2) }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(vertical = 6.dp)
            ) {
                SettingRow(Icons.Outlined.Collections, "Browse albums", onAlbums)
                SettingRow(
                    if (partialAccess) Icons.Outlined.Tune else Icons.Outlined.VerifiedUser,
                    if (partialAccess) "Select more photos" else "Full photo access granted",
                    onManageAccess,
                )
                SettingRow(Icons.Outlined.Info, "Version 1.0.0") {}
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Palette.OrangeSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Palette.Orange, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Charcoal)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.Muted)
    }
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        mb >= 1 -> "%.0f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}
