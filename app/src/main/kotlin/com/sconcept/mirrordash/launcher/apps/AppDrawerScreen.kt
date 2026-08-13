package com.sconcept.mirrordash.launcher.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Swipe-up-from-bottom app drawer (brief section 24-26). Deliberately minimal: search,
 * alphabetical grid, tap to launch, long-press for "App info" only - no favorites/hidden-apps
 * canvas, which the brief explicitly says not to build in v1 and which BerthierOptions itself
 * never had (only an ordered favorites list on the Home screen, not a drawer feature).
 */
@Composable
fun AppDrawerScreen(
    state: AppDrawerUiState,
    onQueryChange: (String) -> Unit,
    onLaunch: (LauncherAppInfo) -> Unit,
    onLongPress: (LauncherAppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MDTheme.colors.backgroundElevated)
            .padding(top = 28.dp, start = 40.dp, end = 40.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Apps, contentDescription = null, tint = MDTheme.colors.textSecondary)
            Spacer(Modifier.size(12.dp))
            Text("Apps", style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
        }

        Spacer(Modifier.height(20.dp))

        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            resultCount = state.visibleApps.size,
        )

        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.visibleApps, key = { it.componentKey }) { app ->
                AppTile(app = app, onLaunch = { onLaunch(app) }, onLongPress = { onLongPress(app) })
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, resultCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MDTheme.colors.surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MDTheme.colors.textTertiary)
        Spacer(Modifier.size(12.dp))
        Box(Modifier.fillMaxWidth()) {
            if (query.isEmpty()) {
                Text("Search apps", style = MDTheme.type.body, color = MDTheme.colors.textTertiary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MDTheme.type.body.copy(color = MDTheme.colors.textPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MDTheme.colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: LauncherAppInfo, onLaunch: () -> Unit, onLongPress: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onLaunch, onLongClick = onLongPress)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MDTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            DrawableIcon(app.icon, sizeDp = 40)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MDTheme.type.caption,
            color = MDTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawableIcon(drawable: Drawable, sizeDp: Int) {
    val bitmap = remember(drawable) {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
}
