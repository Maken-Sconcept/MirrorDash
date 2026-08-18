package com.sconcept.mirrordash.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sconcept.mirrordash.stocks.StockQuote
import com.sconcept.mirrordash.stocks.StocksUiState
import com.sconcept.mirrordash.ui.theme.MDTheme
import kotlin.math.roundToInt

private val PositiveColor = Color(0xFF6FCF97)
private val NegativeColor = Color(0xFFEB5757)

/** Tabular-figure OpenType feature so symbol/price columns actually line up - real numeric
 * alignment, not a monospace "technical" costume. Silently ignored by fonts that don't implement
 * it, so it's safe to request unconditionally regardless of which font is active. */
private const val TabularNums = "tnum"

@Composable
internal fun StocksTickerWidgetSurface(
    widget: StocksWidget,
    stocks: StocksUiState,
    fontFamily: FontFamily = FontFamily.Default,
) {
    val textColor = Color(widget.colorArgb)
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 400.dp)
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, textColor.copy(alpha = 0.12f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LivePulseDot(color = if (stocks.isLoading) textColor.copy(alpha = 0.5f) else PositiveColor)
                Spacer(Modifier.width(9.dp))
                Text(
                    "Stocks",
                    style = MDTheme.type.settingSubtitle.copy(fontSize = 14.sp, letterSpacing = 0.2.sp, fontFamily = fontFamily),
                    color = textColor.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(14.dp))

            if (widget.symbols.isEmpty()) {
                Text(
                    "No symbols yet",
                    style = MDTheme.type.caption.copy(fontSize = widget.fontSizeSp.sp * 0.78f, fontFamily = fontFamily),
                    color = textColor.copy(alpha = 0.55f),
                )
            } else {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    widget.symbols.forEach { symbol ->
                        val quote = stocks.quotesBySymbol[symbol.trim().uppercase()]
                        StockRow(
                            symbol = symbol.trim().uppercase(),
                            quote = quote,
                            fontSizeSp = widget.fontSizeSp,
                            textColor = textColor,
                            fontFamily = fontFamily,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockRow(symbol: String, quote: StockQuote?, fontSizeSp: Int, textColor: Color, fontFamily: FontFamily) {
    val change = quote?.changePercent
    val trendColor = when {
        change == null -> textColor.copy(alpha = 0.4f)
        change >= 0 -> PositiveColor
        else -> NegativeColor
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = symbol,
            style = MDTheme.type.settingSubtitle.copy(fontSize = fontSizeSp.sp, fontFeatureSettings = TabularNums, fontFamily = fontFamily),
            color = textColor,
            modifier = Modifier.widthIn(min = 60.dp),
        )
        Text(
            text = quote?.price?.let { formatPrice(it) } ?: "--",
            style = MDTheme.type.caption.copy(fontSize = fontSizeSp.sp, fontFeatureSettings = TabularNums, fontFamily = fontFamily),
            color = textColor.copy(alpha = 0.88f),
            modifier = Modifier.widthIn(min = 64.dp),
        )
        Spacer(Modifier.width(6.dp))
        if (change != null) {
            TrendGlyphIcon(size = (fontSizeSp * 0.7f).sp.value.dp, color = trendColor, positive = change >= 0)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = change?.let { "${if (it >= 0) "+" else ""}${(it * 100).roundToInt() / 100.0}%" } ?: "",
            style = MDTheme.type.caption.copy(fontSize = (fontSizeSp * 0.88f).sp, fontFeatureSettings = TabularNums, fontFamily = fontFamily),
            color = trendColor,
        )
    }
}

private fun formatPrice(price: Double): String = "%.2f".format(price)
