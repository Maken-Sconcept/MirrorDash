package com.sconcept.mirrordash.clock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sconcept.mirrordash.news.NewsUiState
import com.sconcept.mirrordash.ui.theme.MDTheme
import kotlin.math.roundToInt

@Composable
internal fun NewsTickerWidgetSurface(widget: NewsWidget, news: NewsUiState) {
    val textColor = Color(widget.colorArgb)
    val headlines = news.headlinesByFeedUrl[widget.feedUrl.trim()].orEmpty().take(widget.itemCount.coerceAtLeast(1))
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 420.dp)
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, textColor.copy(alpha = 0.12f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TickerGlyphIcon(size = 18.dp, color = textColor.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "News",
                    style = MDTheme.type.settingSubtitle.copy(fontSize = 14.sp, letterSpacing = 0.2.sp),
                    color = textColor.copy(alpha = 0.7f),
                )
                if (headlines.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    LivePulseDot(color = textColor.copy(alpha = 0.6f), size = 6.dp)
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                widget.feedUrl.isBlank() -> Text(
                    "No feed configured",
                    style = MDTheme.type.caption.copy(fontSize = widget.fontSizeSp.sp * 0.78f),
                    color = textColor.copy(alpha = 0.55f),
                )
                headlines.isEmpty() -> Text(
                    "No headlines yet",
                    style = MDTheme.type.caption.copy(fontSize = widget.fontSizeSp.sp * 0.78f),
                    color = textColor.copy(alpha = 0.55f),
                )
                else -> MarqueeHeadlines(headlines = headlines, fontSizeSp = widget.fontSizeSp, textColor = textColor)
            }
        }
    }
}

/** A real crawl, not a crossfade - news tickers are defined by continuous motion, so this scrolls
 * every configured headline past in one unbroken lane rather than fading between them one at a
 * time. Two back-to-back copies of the joined string are laid side by side and translated by
 * exactly one copy's width per loop, so the wrap is invisible. */
@Composable
private fun MarqueeHeadlines(headlines: List<com.sconcept.mirrordash.news.NewsHeadline>, fontSizeSp: Int, textColor: Color) {
    val style = MDTheme.type.settingSubtitle.copy(fontSize = fontSizeSp.sp)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val content = headlines.joinToString("        •        ") { it.title } + "        •        "

    val measured = remember(content, fontSizeSp) { textMeasurer.measure(content, style) }
    val contentWidthPx = measured.size.width.toFloat().coerceAtLeast(1f)
    val pixelsPerSecond = 46f
    val durationMillis = ((contentWidthPx / pixelsPerSecond) * 1000).roundToInt().coerceIn(4000, 40000)

    val transition = rememberInfiniteTransition(label = "newsMarquee")
    val offsetPx by transition.animateFloat(
        initialValue = 0f,
        targetValue = -contentWidthPx,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "marqueeOffset",
    )

    val heightDp = with(density) { measured.size.height.toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .clipToBounds(),
    ) {
        Row(
            modifier = Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) },
        ) {
            Text(content, style = style, color = textColor, maxLines = 1, softWrap = false)
            Text(content, style = style, color = textColor, maxLines = 1, softWrap = false)
        }
    }
}
