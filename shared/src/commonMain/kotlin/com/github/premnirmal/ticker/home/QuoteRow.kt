package com.github.premnirmal.ticker.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.ic_drag_handle
import com.github.premnirmal.shared.resources.ic_remove_circle
import com.github.premnirmal.shared.resources.remove
import com.github.premnirmal.ticker.components.AppNumberFormat
import com.github.premnirmal.ticker.model.ChartData
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.tickerwidget.ui.theme.SharedColours
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Footprint of the sparkline between the name and price columns. */
internal val SparklineWidth = 64.dp
internal val SparklineHeight = 32.dp

/**
 * What every row's change pill displays; tapping any pill cycles the whole list through the modes
 * (like Apple Stocks' price/percent/market-cap pill). [HOLDINGS_GAIN] shows the position's
 * gain/loss and falls back to the day change percent for quotes without positions. Not persisted:
 * the list opens in [CHANGE_AMOUNT] each launch.
 */
enum class ChangeDisplayMode {
    CHANGE_AMOUNT,
    CHANGE_PERCENT,
    HOLDINGS_GAIN;

    fun next(): ChangeDisplayMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Apple-Stocks-style watchlist row (see reference/stocks.png and reference/plan.md), shared by
 * Android and iOS: bold symbol over the greyed company name on the left, the 1-day [Sparkline]
 * (from [sparkData], blank until it loads) in the middle, and the price over a filled green/red
 * change pill on the right. Rows are full-width with a thin inset divider instead of card chrome.
 *
 * In edit mode ([isEditing], entered by long-pressing a row or via the watchlist dropdown's
 * "Edit List"), the row gains a leading red remove button ([onRemoveClick]) and a trailing drag
 * handle; [dragHandleModifier] (the reorder library's handle modifier, created by the caller
 * inside its 'ReorderableItem' scope) makes the handle the only draggable surface.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuoteRow(
    quote: Quote,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    isEditing: Boolean = false,
    displayMode: ChangeDisplayMode = ChangeDisplayMode.CHANGE_AMOUNT,
    onClick: (Quote) -> Unit,
    onLongClick: () -> Unit = {},
    onRemoveClick: (Quote) -> Unit = {},
    onPillClick: () -> Unit = {},
    sparkData: ChartData? = null,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    // Inert while editing: rows must not navigate to the detail screen (the
                    // remove/drag affordances and swipe-to-delete are separate surfaces).
                    enabled = !isEditing,
                    interactionSource = interactionSource ?: remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onLongClick = onLongClick,
                ) { onClick(quote) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (isEditing) {
                Icon(
                    painter = painterResource(Res.drawable.ic_remove_circle),
                    contentDescription = stringResource(Res.string.remove),
                    tint = SharedColours.PillNegative,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(22.dp)
                        .clickable { onRemoveClick(quote) },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.symbol,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = quote.name,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(width = SparklineWidth, height = SparklineHeight),
                contentAlignment = Alignment.Center,
            ) {
                if (sparkData != null && sparkData.dataPoints.size >= 2) {
                    Sparkline(chartData = sparkData)
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    // Inset by a quarter character so the price sits just left of the pill's edge.
                    modifier = Modifier.padding(
                        end = with(LocalDensity.current) { (17.sp * 0.22f).toDp() }
                    ),
                    // Large prices drop the decimals so the column stays compact (e.g. "21,128").
                    text = if (quote.lastTradePrice >= 1000f) {
                        quote.priceFormat.format(quote.lastTradePrice, AppNumberFormat.WHOLE)
                    } else {
                        quote.priceFormat.format(quote.lastTradePrice)
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                val showHoldingsGain = displayMode == ChangeDisplayMode.HOLDINGS_GAIN && quote.hasPositions()
                ChangePill(
                    modifier = Modifier.padding(top = 4.dp),
                    text = when {
                        showHoldingsGain -> quote.gainLossString()
                        displayMode == ChangeDisplayMode.CHANGE_AMOUNT -> quote.changeStringWithSign()
                        else -> quote.changePercentStringWithSign()
                    },
                    up = if (showHoldingsGain) quote.gainLoss() > 0f else quote.isUp,
                    down = if (showHoldingsGain) quote.gainLoss() < 0f else quote.isDown,
                    enabled = !isEditing,
                    onClick = onPillClick,
                )
            }
            if (isEditing) {
                Icon(
                    painter = painterResource(Res.drawable.ic_drag_handle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier
                        .padding(start = 12.dp)
                        .size(24.dp),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun ChangePill(
    text: String,
    up: Boolean,
    down: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // The pill's own clickable wins over the row's, so tapping it cycles the display mode
        // instead of opening the quote.
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color = SharedColours.pillColour(up, down))
            .clickable(enabled = enabled, onClick = onClick)
            .widthIn(min = 76.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}