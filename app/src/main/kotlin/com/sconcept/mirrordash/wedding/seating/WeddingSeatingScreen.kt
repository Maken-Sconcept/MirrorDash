package com.sconcept.mirrordash.wedding.seating

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sconcept.mirrordash.wedding.WeddingBackground
import com.sconcept.mirrordash.wedding.WeddingIvory
import com.sconcept.mirrordash.wedding.WeddingMuted
import com.sconcept.mirrordash.wedding.WeddingRose
import com.sconcept.mirrordash.wedding.WeddingSurface
import com.sconcept.mirrordash.wedding.WeddingViewModel
import kotlinx.coroutines.delay

@Composable
fun WeddingSeatingScreen(
    viewModel: WeddingViewModel,
    isActive: Boolean,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = state.selectedGuest != null) {
        viewModel.clearGuestSession()
        onInteraction()
    }

    LaunchedEffect(isActive, state.selectedGuest?.id) {
        if (isActive && state.selectedGuest == null && state.seating.data.guests.isNotEmpty()) {
            delay(300)
            focusRequester.requestFocus()
            keyboardController?.show()
        } else if (!isActive) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    if (state.selectedGuest != null && state.selectedTable != null) {
        WeddingTableReveal(
            guest = state.selectedGuest!!,
            table = state.selectedTable!!,
            onDone = {
                viewModel.clearGuestSession()
                onInteraction()
            },
            modifier = modifier,
        )
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WeddingBackground),
    ) {
        val compactHeight = maxHeight < 600.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (compactHeight) 78.dp else 96.dp,
                    bottom = if (compactHeight) 14.dp else 30.dp,
                    start = if (compactHeight) 28.dp else 56.dp,
                    end = if (compactHeight) 28.dp else 56.dp,
                ),
        ) {
            Text(
                "Find Your Seat",
                color = WeddingIvory,
                fontSize = if (compactHeight) 30.sp else 44.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
            if (!compactHeight) {
                Spacer(Modifier.height(8.dp))
                Text("Search for your name", color = WeddingMuted, fontSize = 19.sp)
            }
            Spacer(Modifier.height(if (compactHeight) 12.dp else 22.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = { value ->
                    viewModel.onQueryChange(value)
                    onInteraction()
                },
                enabled = !state.seating.isLoading && state.seating.errorMessage == null && state.seating.data.guests.isNotEmpty(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, color = WeddingIvory),
                label = { Text("Your name") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onQueryChange("")
                            onInteraction()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(if (compactHeight) 0.86f else 0.72f)
                    .focusRequester(focusRequester),
            )
            Spacer(Modifier.height(if (compactHeight) 8.dp else 16.dp))
            SeatingSearchContent(
                state = state,
                onSelect = { match ->
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    viewModel.selectGuest(match)
                    onInteraction()
                },
                compactHeight = compactHeight,
                modifier = Modifier
                    .fillMaxWidth(if (compactHeight) 0.9f else 0.76f)
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun SeatingSearchContent(
    state: com.sconcept.mirrordash.wedding.WeddingUiState,
    onSelect: (WeddingGuestMatch) -> Unit,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        when {
            state.seating.isLoading -> CircularProgressIndicator(color = WeddingRose, modifier = Modifier.padding(top = 24.dp))
            state.seating.errorMessage != null -> SeatingMessage(
                title = "Seat finder unavailable",
                body = "Please ask a host for help. The guest list could not be opened.",
            )
            state.seating.data.guests.none(WeddingGuest::isActive) -> SeatingMessage(
                title = "Guest list not ready",
                body = "Please ask a host for help finding your table.",
            )
            state.query.isBlank() -> SeatingMessage(
                title = "Start with a few letters",
                body = "You can type any part of your first or last name.",
            )
            GuestSearchEngine.normalize(state.query).length < GuestSearchEngine.MIN_QUERY_LENGTH -> SeatingMessage(
                title = "Keep typing",
                body = "Enter at least two letters so we can narrow the guest list.",
            )
            state.searchResults.isEmpty() -> SeatingMessage(
                title = "No match yet",
                body = "Check the spelling, try a shorter name, or ask a host for help.",
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.searchResults, key = { match -> match.guest.id }) { match ->
                    GuestResultRow(match = match, compact = compactHeight, onClick = { onSelect(match) })
                    HorizontalDivider(color = WeddingMuted.copy(alpha = 0.18f))
                }
                if (state.totalResultCount > state.searchResults.size) {
                    item {
                        Text(
                            "${state.totalResultCount - state.searchResults.size} more matches - keep typing to narrow the list.",
                            color = WeddingMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestResultRow(
    match: WeddingGuestMatch,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (compact) 12.dp else 18.dp),
    ) {
        Icon(Icons.Filled.EventSeat, contentDescription = null, tint = WeddingRose, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(
                match.guest.displayName,
                color = WeddingIvory,
                fontSize = if (compact) 18.sp else 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(match.guest.partyName, match.guest.groupName)
                .firstOrNull(String::isNotBlank)
                ?.let { detail ->
                    Text(detail, color = WeddingMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
        }
        Text("View table", color = WeddingRose, fontSize = 14.sp)
    }
}

@Composable
private fun SeatingMessage(title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp),
    ) {
        Text(title, color = WeddingIvory, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text(body, color = WeddingMuted, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WeddingTableReveal(
    guest: WeddingGuest,
    table: WeddingTable,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WeddingSurface, WeddingBackground))),
        contentAlignment = Alignment.Center,
    ) {
        val compactHeight = maxHeight < 600.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .padding(top = 72.dp, bottom = 24.dp),
        ) {
            Text(
                "Welcome, ${guest.displayName}",
                color = WeddingMuted,
                fontSize = if (compactHeight) 20.sp else 26.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compactHeight) 10.dp else 22.dp))
            Text("YOUR TABLE", color = WeddingRose, fontSize = 15.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                table.name,
                color = WeddingIvory,
                fontSize = if (compactHeight) 46.sp else 68.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                lineHeight = if (compactHeight) 50.sp else 72.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            guest.seatNumber?.let { seat ->
                Spacer(Modifier.height(8.dp))
                Text("Seat $seat", color = WeddingMuted, fontSize = 20.sp)
            }
            table.location?.let { location ->
                Spacer(Modifier.height(8.dp))
                Text(location, color = WeddingMuted, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(if (compactHeight) 18.dp else 34.dp))
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(54.dp),
            ) {
                Text("Done")
            }
        }
    }
}
