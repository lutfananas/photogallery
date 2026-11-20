package com.lutfan.photogallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lutfan.photogallery.ui.sharedPhoto
import com.lutfan.photogallery.ui.thumbCacheKey
import com.lutfan.photogallery.data.Photo
import com.lutfan.photogallery.ui.theme.Palette

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Favorites("Favorites", Icons.Outlined.FavoriteBorder),
    Camera("Camera", Icons.Outlined.CameraAlt),
    Profile("Profile", Icons.Outlined.PersonOutline),
}

/** Floating charcoal bar with an orange pill for the selected tab. */
@Composable
fun FloatingNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(.25f))
            .clip(RoundedCornerShape(30.dp))
            .background(Palette.Charcoal)
            .height(76.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Tab.entries.forEach { tab ->
            val isSel = tab == selected
            val bg by animateColorAsState(if (isSel) Palette.Orange else Color.Transparent, label = "tabBg")
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable { onSelect(tab) }
                    .height(50.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, tab.label, tint = Color.White, modifier = Modifier.size(26.dp))
                AnimatedVisibility(
                    isSel,
                    enter = fadeIn() + expandHorizontally(spring()),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Text(
                        tab.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Rounded image tile used across the grids. */
@Composable
fun PhotoTile(
    photo: Photo,
    modifier: Modifier = Modifier,
    aspect: Float = photo.aspect.coerceIn(0.72f, 1.35f),
    corner: Dp = 22.dp,
    elevated: Boolean = false,
    favorite: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val haptics = LocalHapticFeedback.current
    // Selected tiles sink in a little, like Google Photos.
    val inset by animateFloatAsState(
        if (selected) 0.86f else 1f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "selectInset",
    )
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .graphicsLayer { scaleX = inset; scaleY = inset }
            .then(if (elevated && !selected) Modifier.shadow(14.dp, shape) else Modifier)
            .clip(shape)
            .background(Palette.Line)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { lc ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        lc()
                    }
                },
            )
    ) {
        val context = LocalContext.current
        val request = remember(photo.id) {
            ImageRequest.Builder(context)
                .data(photo.uri)
                .memoryCacheKey(thumbCacheKey(photo.id))
                .size(720)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().sharedPhoto(photo.id),
        )
        if (selectionMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (selected) Palette.Orange.copy(alpha = 0.18f) else Color.Transparent)
            )
            SelectionCheck(selected, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        if (favorite) {
            Icon(
                Icons.Rounded.Favorite, null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) Palette.Orange else Color.Black.copy(alpha = 0.18f), label = "checkBg")
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(selected, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** Charcoal bar that replaces the nav bar while photos are selected. */
@Composable
fun SelectionBar(
    count: Int,
    allFavorite: Boolean,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(.25f))
            .clip(RoundedCornerShape(30.dp))
            .background(Palette.Charcoal)
            .height(76.dp)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel selection", tint = Color.White) }
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically { if (targetState > initialState) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (targetState > initialState) -it else it } + fadeOut())
            },
            label = "count",
            modifier = Modifier.weight(1f),
        ) { n ->
            Text("$n selected", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        SelectionAction(Icons.Outlined.Share, "Share", onClick = onShare)
        SelectionAction(
            if (allFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            if (allFavorite) "Unfavorite" else "Favorite",
            highlight = true,
            onClick = onFavorite,
        )
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) Palette.Orange else Color.Transparent)
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Circular thumbnail with a caption (the category row on Discover). */
@Composable
fun CircleThumb(
    label: String,
    model: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .width(size + 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = model,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Palette.Line),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Palette.OrangeSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Palette.Orange, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
    }
}
