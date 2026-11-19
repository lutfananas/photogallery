package com.lutfan.photogallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lutfan.photogallery.GalleryViewModel
import com.lutfan.photogallery.ui.components.CircleThumb
import com.lutfan.photogallery.ui.components.EmptyState
import com.lutfan.photogallery.ui.components.PhotoTile
import com.lutfan.photogallery.ui.theme.Palette

@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onManageAccess: () -> Unit,
    onSearch: () -> Unit,
    onAlbum: (Long) -> Unit,
    onSeeMore: () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    val photos = vm.photos
    val full = StaggeredGridItemSpan.FullLine

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(Palette.Paper),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp,
    ) {
        item(span = full) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Discover", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, "Search", modifier = Modifier.size(28.dp))
                }
            }
        }

        if (vm.albums.isNotEmpty()) {
            item(span = full) {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 0.dp),
                ) {
                    items(vm.albums, key = { it.id }) { album ->
                        CircleThumb(album.name, album.cover) { onAlbum(album.id) }
                    }
                }
            }
        }

        if (partialAccess) {
            item(span = full) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.OrangeSoft)
                        .clickable(onClick = onManageAccess)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "You've given access to selected photos only.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Manage", color = Palette.Orange, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item(span = full) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "See more",
                    color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onSeeMore).padding(6.dp),
                )
            }
        }

        when {
            photos.isEmpty() && vm.loading -> item(span = full) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Orange)
                }
            }
            photos.isEmpty() -> item(span = full) {
                EmptyState(Icons.Outlined.PhotoLibrary, "No photos yet", "Take a photo with the camera button below.")
            }
            else -> itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
                PhotoTile(
                    photo = photo,
                    aspect = if (index == 0) 0.74f else photo.aspect.coerceIn(0.72f, 1.35f),
                    elevated = index == 0,
                    favorite = vm.isFavorite(photo.id),
                    onClick = { onPhoto(photo.id) },
                )
            }
        }
    }
}
