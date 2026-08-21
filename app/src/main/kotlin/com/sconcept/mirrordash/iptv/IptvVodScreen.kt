package com.sconcept.mirrordash.iptv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.sconcept.mirrordash.ui.theme.MDTheme
import kotlinx.coroutines.delay

/** Top-level Live/Movies/Series switcher for the IPTV tab - see [IptvContentTab]. Same visual
 * language as [IptvGuideScreen]'s genre tabs (bold+accent when selected) but one level up, for
 * content type rather than a genre within live TV. */
@Composable
fun IptvContentTabsRow(selected: IptvContentTab, onSelect: (IptvContentTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        listOf(
            IptvContentTab.LIVE to "Live TV",
            IptvContentTab.MOVIES to "Movies",
            IptvContentTab.SERIES to "Series",
        ).forEach { (tab, label) ->
            val isSelected = tab == selected
            Text(
                label,
                style = MDTheme.type.settingSubtitle,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MDTheme.colors.accent else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.clickable { onSelect(tab) },
            )
        }
    }
}

/** Movies/Series browser - category tabs, then a search/view-mode row, then a grid or list of
 * whatever's loaded so far (see [VodPage]) plus a "Load more" footer. Shared between both tabs
 * via [contentType] rather than forked into two near-identical composables, since the only real
 * difference is which slice of [IptvUiState] and which [IptvViewModel]/[IptvRecordingEngine]
 * calls to make. */
@Composable
fun IptvVodBrowser(
    uiState: IptvUiState,
    contentType: VodContentType,
    onSelectCategory: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSetViewMode: (VodViewMode) -> Unit,
    onDeepSearch: (String) -> Unit,
    onClearDeepSearch: () -> Unit,
    onCheckHealth: (StalkerVodItem) -> Unit,
    onPlay: (StalkerVodItem) -> Unit,
    onDownload: (StalkerVodItem) -> Unit,
    /** Non-null (and always [RecordingTrigger.DOWNLOAD]) while a Movies/Series download is in
     * flight - see [VodItemCard]/[VodItemRow]'s doc comment for how each card uses it. */
    activeDownload: ActiveRecording?,
    modifier: Modifier = Modifier,
) {
    val categories = if (contentType == VodContentType.MOVIES) uiState.vodCategories else uiState.seriesCategories
    val selectedCategoryId = if (contentType == VodContentType.MOVIES) uiState.selectedVodCategoryId else uiState.selectedSeriesCategoryId
    val items = if (contentType == VodContentType.MOVIES) uiState.vodItems else uiState.seriesItems
    val loading = if (contentType == VodContentType.MOVIES) uiState.vodLoading else uiState.seriesLoading
    val loadingMore = if (contentType == VodContentType.MOVIES) uiState.vodLoadingMore else uiState.seriesLoadingMore
    val hasMore = if (contentType == VodContentType.MOVIES) uiState.vodHasMore else uiState.seriesHasMore
    val totalItems = if (contentType == VodContentType.MOVIES) uiState.vodTotalItems else uiState.seriesTotalItems
    val error = if (contentType == VodContentType.MOVIES) uiState.vodError else uiState.seriesError
    val viewMode = if (contentType == VodContentType.MOVIES) uiState.moviesViewMode else uiState.seriesViewMode
    val activeDeepQuery = if (contentType == VodContentType.MOVIES) uiState.vodActiveSearchQuery else uiState.seriesActiveSearchQuery
    val label = if (contentType == VodContentType.MOVIES) "movies" else "series"

    // Picking a result is the far more common way out of a search than pressing the IME's own
    // search/enter key (SearchBox handles that path) - tapping a grid/list item while the
    // keyboard is still up otherwise leaves it lingering over the newly-opened detail screen,
    // since a composable disposing (the search row scrolls out of view under nothing, in FILTER
    // mode; the whole browser gets covered either way) doesn't itself tell Android to hide the IME.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Tapping a card/row opens the info screen rather than playing immediately (brief: "the
    // detailed page should have description... both in the list and in the detailed page" - there
    // was no detail page at all before this) - scoped to contentType exactly like `query` below,
    // so switching Movies/Series tabs doesn't leave a stale detail screen open underneath.
    var detailItem by remember(contentType) { mutableStateOf<StalkerVodItem?>(null) }
    val handleOpenDetail: (StalkerVodItem) -> Unit = { item ->
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        detailItem = item
    }

    // Keyed on contentType, not remembered anywhere in IptvUiState - unlike the view mode toggle
    // below, a search query is scoped to "whatever I'm looking at right now", so switching tabs
    // (which recomposes this at the same call site with a different contentType) starts clean.
    var query by remember(contentType) { mutableStateOf("") }
    var searchMode by remember(contentType) { mutableStateOf(IptvSearchMode.FILTER) }
    val filteredItems = remember(items, query, searchMode) {
        // In DEEP mode, `items` already *is* the portal's own search results (see
        // IptvViewModel.searchVodDeep) - re-filtering them locally would only hide results the
        // portal matched by some rule matchesSearch doesn't replicate (stemming, etc.).
        if (searchMode == IptvSearchMode.DEEP) items else items.filter { matchesSearch(it.name, query) }
    }

    // DEEP mode fires a real portal request per query, so it's debounced rather than searching on
    // every keystroke - FILTER mode needs nothing here, it's already just re-filtering `items` above.
    LaunchedEffect(query, searchMode) {
        if (searchMode != IptvSearchMode.DEEP) return@LaunchedEffect
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            if (activeDeepQuery != null) onClearDeepSearch()
            return@LaunchedEffect
        }
        delay(500)
        onDeepSearch(trimmed)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (categories.isNotEmpty()) {
                VodCategoryTabsRow(categories = categories, selectedCategoryId = selectedCategoryId, onSelect = onSelectCategory)
                SearchAndViewModeRow(
                    query = query,
                    onQueryChange = { query = it },
                    searchMode = searchMode,
                    onSetSearchMode = { mode ->
                        searchMode = mode
                        if (mode == IptvSearchMode.FILTER && activeDeepQuery != null) onClearDeepSearch()
                    },
                    viewMode = viewMode,
                    onSetViewMode = onSetViewMode,
                )
            }
            if (items.isNotEmpty()) {
                VodListStatusRow(
                    label = label,
                    loadedCount = items.size,
                    totalItems = totalItems,
                    hasMore = hasMore,
                    filteredCount = if (searchMode == IptvSearchMode.FILTER) {
                        query.trim().takeIf { it.isNotBlank() }?.let { filteredItems.size }
                    } else {
                        null
                    },
                    isDeepSearch = searchMode == IptvSearchMode.DEEP && activeDeepQuery != null,
                )
            }
            when {
                error != null && items.isEmpty() -> CenteredVodMessage(
                    title = if (searchMode == IptvSearchMode.DEEP) "Deep search failed" else "Can't load $label",
                    subtitle = error,
                )
                (loading && items.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MDTheme.colors.accent)
                        if (searchMode == IptvSearchMode.DEEP) {
                            Spacer(Modifier.height(12.dp))
                            Text("Searching the full catalog…", style = MDTheme.type.settingSubtitle, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
                categories.isEmpty() -> CenteredVodMessage(
                    title = "No $label",
                    subtitle = "This provider doesn't seem to offer $label through this portal.",
                )
                searchMode == IptvSearchMode.DEEP && activeDeepQuery != null && items.isEmpty() -> CenteredVodMessage(
                    title = "No matches",
                    subtitle = "Nothing in the whole $label catalog matches \"$activeDeepQuery\".",
                )
                items.isEmpty() -> CenteredVodMessage(title = "Nothing here", subtitle = "This category came back empty.")
                filteredItems.isEmpty() -> EmptyWithLoadMore(
                    title = "No matches",
                    subtitle = if (hasMore) {
                        "No matches in what's loaded so far - load more to search further, or switch to Deep search."
                    } else {
                        "Nothing in this category matches \"$query\"."
                    },
                    hasMore = hasMore,
                    loading = loadingMore,
                    onLoadMore = onLoadMore,
                )
                viewMode == VodViewMode.LIST -> VodItemList(
                    items = filteredItems,
                    health = uiState.streamHealth,
                    onCheckHealth = onCheckHealth,
                    onPlay = handleOpenDetail,
                    onDownload = onDownload,
                    activeDownload = activeDownload,
                    footer = { LoadMoreFooter(hasMore = hasMore, loading = loadingMore, onLoadMore = onLoadMore) },
                )
                else -> VodItemGrid(
                    items = filteredItems,
                    health = uiState.streamHealth,
                    onCheckHealth = onCheckHealth,
                    onPlay = handleOpenDetail,
                    onDownload = onDownload,
                    activeDownload = activeDownload,
                    footer = { LoadMoreFooter(hasMore = hasMore, loading = loadingMore, onLoadMore = onLoadMore) },
                )
            }
        }

        detailItem?.let { item ->
            VodDetailScreen(
                item = item,
                health = uiState.streamHealth[item.id] ?: StreamHealth.UNKNOWN,
                activeDownload = activeDownload,
                onCheckHealth = onCheckHealth,
                onPlay = { detailItem = null; onPlay(item) },
                onDownload = { onDownload(item) },
                onDismiss = { detailItem = null },
            )
        }
    }
}

/** The "detailed page" the brief asks for - previously there was no stop between the grid/list
 * and playback at all, so a title's synopsis/ratings were never visible except as the cramped
 * one-line/two-line snippets on [VodItemRow]/[VodItemCard]. A wide backdrop (not the grid's
 * cropped 2:3 poster) with the full, untruncated description underneath, Play and Download as
 * two equally-weighted actions rather than one implicit tap-to-play - closer to how a streaming
 * app's own info screen reads than to a file browser's context menu. [onCheckHealth] runs again
 * here (already requested once when the card/row composed) since a stream can go from
 * unknown/offline to online between browsing and opening this, and there's plenty of room here
 * to show it clearly ([HealthDot] alone, unlabeled, is easy to miss at grid-card size). */
@Composable
private fun VodDetailScreen(
    item: StalkerVodItem,
    health: StreamHealth,
    activeDownload: ActiveRecording?,
    onCheckHealth: (StalkerVodItem) -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(item.id) { onCheckHealth(item) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (item.logoUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = item.logoUrl),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    }
                }
                // A flat scrim would fight the artwork everywhere; fading it in only over the
                // bottom third keeps the backdrop legible while still guaranteeing the title/close
                // button below/above it never sit on top of a bright, low-contrast frame.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(item.name, style = MDTheme.type.sectionTitle, color = Color.White)

                if (item.ratingImdb != null || item.ratingTomatoes != null || health != StreamHealth.UNKNOWN) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        item.ratingImdb?.let { RatingBadge("IMDb", it) }
                        item.ratingTomatoes?.let { RatingBadge("RT", it) }
                        if (health != StreamHealth.UNKNOWN) {
                            HealthDot(health = health)
                            Text(
                                when (health) {
                                    StreamHealth.CHECKING -> "Checking stream…"
                                    StreamHealth.ONLINE -> "Stream online"
                                    StreamHealth.OFFLINE -> "Stream offline"
                                    StreamHealth.UNKNOWN -> ""
                                },
                                style = MDTheme.type.caption,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    val isDownloading = activeDownload?.channelId == item.id
                    val downloadFraction = downloadProgressFraction(activeDownload, item.id)
                    OutlinedButton(
                        onClick = onDownload,
                        enabled = activeDownload == null || isDownloading,
                    ) {
                        if (isDownloading) {
                            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                if (downloadFraction != null) {
                                    CircularProgressIndicator(
                                        progress = { downloadFraction },
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(downloadFraction?.let { "${(it * 100).toInt()}%" } ?: "Downloading…")
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    item.description?.takeIf { it.isNotBlank() } ?: "No description available for this title.",
                    style = MDTheme.type.body,
                    color = Color.White.copy(alpha = if (item.description.isNullOrBlank()) 0.5f else 0.85f),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VodCategoryTabsRow(categories: List<StalkerVodCategory>, selectedCategoryId: String?, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        categories.forEach { category ->
            val selected = category.id == selectedCategoryId
            Text(
                category.title,
                style = MDTheme.type.settingSubtitle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MDTheme.colors.accent else Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                modifier = Modifier.clickable { onSelect(category.id) },
            )
        }
    }
}

/** Search box, [IptvSearchMode] toggle, and the grid/list view-mode toggle, side by side per the
 * brief ("a view as a list button or view as a card next to the search bar" / "allow the user to
 * select between search filtering or deep search"). [viewMode] is whatever [IptvViewModel]'s
 * settings-backed state currently holds for this tab - this row never keeps its own copy, it just
 * renders that and reports taps back through [onSetViewMode] (see [IptvViewModel.setViewMode]'s
 * doc comment for where the "remembered per tab" part actually lives). [searchMode]/[query], by
 * contrast, are owned by the caller as plain local state - neither is remembered across tabs. */
@Composable
private fun SearchAndViewModeRow(
    query: String,
    onQueryChange: (String) -> Unit,
    searchMode: IptvSearchMode,
    onSetSearchMode: (IptvSearchMode) -> Unit,
    viewMode: VodViewMode,
    onSetViewMode: (VodViewMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchBox(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { onSetViewMode(if (viewMode == VodViewMode.GRID) VodViewMode.LIST else VodViewMode.GRID) },
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
            ) {
                Icon(
                    if (viewMode == VodViewMode.GRID) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                    contentDescription = if (viewMode == VodViewMode.GRID) "Switch to list view" else "Switch to card view",
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SearchModeToggle(mode = searchMode, onSelect = onSetSearchMode)
    }
}

/** The search field itself - factored out of [SearchAndViewModeRow] so the live-channel list
 * (`ChannelListPanel` in `IptvScreen.kt`) can use the exact same box rather than a near-identical
 * copy, since it has no view-mode toggle to pair it with. */
@Composable
fun SearchBox(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search", style = MDTheme.type.body, color = Color.White.copy(alpha = 0.4f))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MDTheme.type.body.copy(color = Color.White),
                cursorBrush = SolidColor(MDTheme.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The IME's own search/enter key is the one path selecting a result never covers -
                // tapping a result closes the keyboard from the *caller* side (see IptvVodBrowser's
                // and ChannelListPanel's onSelect/onPlay wrappers), but someone who just wants to
                // dismiss the keyboard without picking anything needs this too.
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** [IptvSearchMode.FILTER] vs [IptvSearchMode.DEEP] - a small segmented toggle next to whichever
 * [SearchBox] it's paired with (Movies/Series' [SearchAndViewModeRow], or live TV's
 * `ChannelListPanel`). Deliberately understated (caption-sized text chips, not full buttons) -
 * this is a secondary control most searches never need to touch, the default (Filter) already
 * covers the common case. */
@Composable
fun SearchModeToggle(mode: IptvSearchMode, onSelect: (IptvSearchMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            IptvSearchMode.FILTER to "Filter",
            IptvSearchMode.DEEP to "Deep search",
        ).forEach { (value, label) ->
            val selected = mode == value
            Text(
                label,
                style = MDTheme.type.caption,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MDTheme.colors.accent else Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MDTheme.colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** "How many videos are in the list, and where pagination is at" - a plain count once a category
 * is fully loaded, "X of Y" while [totalItems] is known and more remains (see
 * [VodPage.totalItems]), or just "X loaded" when the portal never reported a total.
 * [filteredCount] is non-null only while a [IptvSearchMode.FILTER] query is active, showing how
 * many of what's loaded actually match rather than silently replacing the loaded count.
 * [isDeepSearch] swaps the wording to make clear these are catalog-wide portal results, not a
 * locally-filtered chunk. */
@Composable
private fun VodListStatusRow(label: String, loadedCount: Int, totalItems: Int, hasMore: Boolean, filteredCount: Int?, isDeepSearch: Boolean) {
    val loadedLabel = when {
        isDeepSearch && totalItems > 0 && hasMore -> "Found $loadedCount of $totalItems matching $label"
        isDeepSearch && totalItems > 0 -> "$totalItems matching $label"
        isDeepSearch -> "$loadedCount matching $label found"
        totalItems > 0 && hasMore -> "Showing $loadedCount of $totalItems $label"
        totalItems > 0 -> "$totalItems $label"
        hasMore -> "$loadedCount $label loaded"
        else -> "$loadedCount $label"
    }
    val text = if (filteredCount != null && filteredCount != loadedCount) "$filteredCount matches ($loadedLabel)" else loadedLabel
    Text(
        text,
        style = MDTheme.type.caption,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
    )
}

@Composable
private fun VodItemGrid(
    items: List<StalkerVodItem>,
    health: Map<String, StreamHealth>,
    onCheckHealth: (StalkerVodItem) -> Unit,
    onPlay: (StalkerVodItem) -> Unit,
    onDownload: (StalkerVodItem) -> Unit,
    activeDownload: ActiveRecording?,
    footer: @Composable () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            // Only requested once this card is actually composed - LazyVerticalGrid only
            // composes items near the visible viewport, so this is "check what's on screen"
            // without any hand-rolled viewport tracking (see StreamHealthChecker's doc comment).
            LaunchedEffect(item.id) { onCheckHealth(item) }
            VodItemCard(
                item = item,
                health = health[item.id] ?: StreamHealth.UNKNOWN,
                onPlay = { onPlay(item) },
                onDownload = { onDownload(item) },
                activeDownload = activeDownload,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { footer() }
    }
}

/** [activeDownload] is whichever item is downloading right now, app-wide (only one runs at a
 * time - see [IptvRecordingEngine]'s doc comment) - so this card only cares whether it's *this*
 * item (shows progress instead of the download icon) or some *other* item (download icon stays
 * but disabled, so tapping it during someone else's download doesn't just silently do nothing -
 * see [IptvRecordingEngine.downloadVodItem]'s no-op guard). */
@Composable
private fun VodItemCard(
    item: StalkerVodItem,
    health: StreamHealth,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    activeDownload: ActiveRecording?,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (item.logoUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = item.logoUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                )
            }

            HealthDot(health = health, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))

            VodDownloadButton(
                isDownloading = activeDownload?.channelId == item.id,
                progressFraction = downloadProgressFraction(activeDownload, item.id),
                enabled = activeDownload == null,
                onClick = onDownload,
                contentDescription = "Download ${item.name}",
                iconSize = 16.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            )

            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play ${item.name}",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            style = MDTheme.type.caption,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Brief: description "both in the list and in the detailed page" - VodItemRow (the list
        // layout) already showed one; the grid card didn't. One line only, unlike the row's two -
        // a card is already tight on vertical space between artwork and the next row of cards.
        item.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                description,
                style = MDTheme.type.caption,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact row-per-item alternative to [VodItemGrid] - same data/actions, denser layout, for
 * scanning many titles quickly rather than browsing artwork. */
@Composable
private fun VodItemList(
    items: List<StalkerVodItem>,
    health: Map<String, StreamHealth>,
    onCheckHealth: (StalkerVodItem) -> Unit,
    onPlay: (StalkerVodItem) -> Unit,
    onDownload: (StalkerVodItem) -> Unit,
    activeDownload: ActiveRecording?,
    footer: @Composable () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(items, key = { it.id }) { item ->
            LaunchedEffect(item.id) { onCheckHealth(item) }
            VodItemRow(
                item = item,
                health = health[item.id] ?: StreamHealth.UNKNOWN,
                onPlay = { onPlay(item) },
                onDownload = { onDownload(item) },
                activeDownload = activeDownload,
            )
        }
        item { footer() }
    }
}

@Composable
private fun VodItemRow(
    item: StalkerVodItem,
    health: StreamHealth,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    activeDownload: ActiveRecording?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 80.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (item.logoUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = item.logoUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }
            HealthDot(health = health, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MDTheme.type.body,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.ratingImdb != null || item.ratingTomatoes != null) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item.ratingImdb?.let { RatingBadge(label = "IMDb", value = it) }
                    item.ratingTomatoes?.let { RatingBadge(label = "RT", value = it) }
                }
            }
            item.description?.let { description ->
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MDTheme.type.caption,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        VodDownloadButton(
            isDownloading = activeDownload?.channelId == item.id,
            progressFraction = downloadProgressFraction(activeDownload, item.id),
            enabled = activeDownload == null,
            onClick = onDownload,
            contentDescription = "Download ${item.name}",
            iconSize = 24.dp,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** `null` if [activeDownload] isn't [id] (irrelevant to this card) or hasn't reported a
 * [ActiveRecording.totalBytes] yet (server sent no `Content-Length`, or the very first tick
 * hasn't landed) - either way [VodDownloadButton] falls back to an indeterminate spinner. */
private fun downloadProgressFraction(activeDownload: ActiveRecording?, id: String): Float? {
    val recording = activeDownload?.takeIf { it.channelId == id } ?: return null
    val total = recording.totalBytes?.takeIf { it > 0 } ?: return null
    return (recording.bytesWritten.toFloat() / total).coerceIn(0f, 1f)
}

/** The download affordance for a single VOD card/row - a plain icon button normally, a
 * determinate (or, briefly before the first byte-count tick, indeterminate) circular progress
 * indicator while [isDownloading], and disabled rather than hidden when [enabled] is false (some
 * *other* item is downloading right now - only one recording/download runs at a time, see
 * [IptvRecordingEngine]) so tapping it still gives feedback instead of silently doing nothing. */
@Composable
private fun VodDownloadButton(
    isDownloading: Boolean,
    progressFraction: Float?,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isDownloading) {
            if (progressFraction != null) {
                CircularProgressIndicator(
                    progress = { progressFraction },
                    color = MDTheme.colors.accent,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "${(progressFraction * 100).toInt()}%",
                    style = MDTheme.type.caption.copy(fontSize = 8.sp),
                    color = Color.White,
                )
            } else {
                CircularProgressIndicator(
                    color = MDTheme.colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = contentDescription,
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun RatingBadge(label: String, value: String) {
    Text(
        "$label $value",
        style = MDTheme.type.caption,
        color = MDTheme.colors.accent,
    )
}

/** Trailing row/grid-item shown once a category's loaded chunk isn't the whole catalog - see
 * [VodPage]. Renders nothing once there's genuinely nothing left to fetch, so a fully-loaded
 * category doesn't end in dead space. */
@Composable
private fun LoadMoreFooter(hasMore: Boolean, loading: Boolean, onLoadMore: () -> Unit) {
    if (!hasMore && !loading) return
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = MDTheme.colors.accent, modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onLoadMore) { Text("Load more", color = MDTheme.colors.accent) }
        }
    }
}

/** Grey while [StreamHealth.UNKNOWN]/[StreamHealth.CHECKING] (not checked yet, or in flight),
 * green once confirmed reachable, red once confirmed not - see [StreamHealthChecker]. */
@Composable
private fun HealthDot(health: StreamHealth, modifier: Modifier = Modifier) {
    val color = when (health) {
        StreamHealth.ONLINE -> Color(0xFF4CAF50)
        StreamHealth.OFFLINE -> Color(0xFFE0433D)
        StreamHealth.UNKNOWN, StreamHealth.CHECKING -> Color.White.copy(alpha = 0.4f)
    }
    Box(modifier = modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun CenteredVodMessage(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MDTheme.type.settingTitle, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MDTheme.type.settingSubtitle,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
}

/** Same centered message as [CenteredVodMessage], but with a [LoadMoreFooter] pinned below it -
 * used when a search has no matches in what's loaded so far, so "load more" is one tap away
 * instead of just a suggestion in the subtitle text. */
@Composable
private fun EmptyWithLoadMore(title: String, subtitle: String, hasMore: Boolean, loading: Boolean, onLoadMore: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MDTheme.type.settingTitle, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MDTheme.type.settingSubtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
        LoadMoreFooter(hasMore = hasMore, loading = loading, onLoadMore = onLoadMore)
    }
}
