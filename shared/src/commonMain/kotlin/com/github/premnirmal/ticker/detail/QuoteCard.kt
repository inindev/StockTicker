package com.github.premnirmal.ticker.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.premnirmal.shared.resources.Res
import com.github.premnirmal.shared.resources.change_amount
import com.github.premnirmal.shared.resources.change_percent
import com.github.premnirmal.shared.resources.day_change_amount
import com.github.premnirmal.shared.resources.gain
import com.github.premnirmal.shared.resources.holdings
import com.github.premnirmal.shared.resources.loss
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.tickerwidget.ui.AppCard
import com.github.premnirmal.tickerwidget.ui.theme.SharedColours
import org.jetbrains.compose.resources.stringResource

private const val QUOTE_MAX_LINES = 1

/**
 * Shared (Compose Multiplatform) quote card rendered identically on Android and iOS (used by the
 * Search/Trending/NewsFeed screens; the watchlist uses the row-style
 * [com.github.premnirmal.ticker.home.QuoteRow]). It shows the symbol, name, last trade price and
 * the change amount/percent. Quotes that have holdings render the richer [PositionCard] layout.
 * All localized labels come from the shared string resources so the card looks and reads the same
 * on every platform.
 */
@Composable
fun QuoteCard(
    quote: Quote,
    modifier: Modifier = Modifier,
    quoteNameMaxLines: Int = QUOTE_MAX_LINES,
    onClick: (Quote) -> Unit,
) {
    AppCard(
        modifier = modifier,
        onClick = { onClick(quote) }
    ) {
        if (quote.hasPositions()) {
            PositionCard(quote)
        } else {
            InstrumentCard(quote, quoteNameMaxLines)
        }
    }
}

@Composable
private fun InstrumentCard(
    quote: Quote,
    quoteNameMaxLines: Int,
) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        QuoteSymbolText(
            modifier = Modifier.fillMaxWidth(),
            text = quote.symbol
        )
        QuoteNameText(modifier = Modifier.padding(top = 4.dp), text = quote.name, maxLines = quoteNameMaxLines)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                QuoteValueText(text = quote.priceFormat.format(quote.lastTradePrice))
            }
            Column(
                modifier = Modifier.weight(1f, fill = true),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                QuoteChangeText(text = quote.changePercentStringWithSign(), up = quote.isUp, down = quote.isDown)
                QuoteChangeText(text = quote.changeStringWithSign(), up = quote.isUp, down = quote.isDown)
            }
        }
    }
}

@Composable
private fun PositionCard(
    quote: Quote,
) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuoteSymbolText(modifier = Modifier.weight(1f), text = quote.symbol)
            QuoteValueText(
                modifier = Modifier.weight(1f),
                text = quote.priceFormat.format(quote.lastTradePrice),
                textAlign = TextAlign.End
            )
        }
        QuoteNameText(modifier = Modifier.padding(top = 4.dp), text = quote.name, maxLines = 1)
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            AnnotatedQuoteValue(
                modifier = Modifier.weight(1f, fill = true),
                text = quote.priceFormat.format(quote.holdings()),
                up = quote.holdings() > 0,
                down = quote.holdings() < 0,
                annotation = stringResource(Res.string.holdings)
            )
            AnnotatedQuoteValue(
                modifier = Modifier.weight(1f, fill = true),
                textAlign = TextAlign.Center,
                text = quote.dayChangeString(),
                up = quote.isUp,
                down = quote.isDown,
                annotation = stringResource(Res.string.day_change_amount)
            )
            AnnotatedQuoteValue(
                modifier = Modifier.weight(1f, fill = true),
                textAlign = TextAlign.End,
                text = quote.changePercentStringWithSign(),
                up = quote.isUp,
                down = quote.isDown,
                annotation = stringResource(Res.string.change_percent)
            )
        }
        val gainOrLoss = if (quote.gainLoss() >= 0) Res.string.gain else Res.string.loss
        Row(
            verticalAlignment = Alignment.Bottom,
        ) {
            AnnotatedQuoteValue(
                modifier = Modifier.weight(1f, fill = true),
                text = quote.gainLossString(),
                up = quote.gainLoss() > 0,
                down = quote.gainLoss() < 0,
                annotation = stringResource(gainOrLoss)
            )
            val gainPercentAnnotation = stringResource(gainOrLoss) + " %"
            AnnotatedQuoteValue(
                modifier = Modifier.weight(1f, fill = true),
                textAlign = TextAlign.Center,
                text = quote.gainLossPercentStringNoPercentSign(),
                up = quote.gainLoss() > 0,
                down = quote.gainLoss() < 0,
                annotation = gainPercentAnnotation
            )
            AnnotatedQuoteValue(
                modifier = Modifier.weight(1f, fill = true),
                textAlign = TextAlign.End,
                text = quote.changeStringWithSign(),
                up = quote.isUp,
                down = quote.isDown,
                annotation = stringResource(Res.string.change_amount)
            )
        }
    }
}

@Composable
fun AnnotatedQuoteValue(
    modifier: Modifier = Modifier,
    text: String,
    up: Boolean,
    down: Boolean,
    textAlign: TextAlign? = null,
    annotation: String
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = annotation,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            textAlign = textAlign,
            maxLines = 1
        )
        SmallQuoteChangeText(
            modifier = Modifier.fillMaxWidth(),
            text = text,
            textAlign = textAlign,
            up = up,
            down = down
        )
    }
}

@Composable
fun QuoteSymbolText(
    modifier: Modifier = Modifier,
    text: String
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
fun QuoteNameText(
    modifier: Modifier = Modifier,
    text: String,
    maxLines: Int = 2
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun QuoteValueText(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null
) {
    Text(
        modifier = modifier,
        text = text,
        textAlign = textAlign,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun QuoteChangeText(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    up: Boolean,
    down: Boolean
) {
    Text(
        modifier = modifier,
        text = text,
        textAlign = textAlign,
        style = MaterialTheme.typography.bodySmall,
        color = SharedColours.changeColour(up, down)
    )
}

@Composable
fun SmallQuoteChangeText(
    modifier: Modifier = Modifier,
    text: String,
    textAlign: TextAlign? = null,
    up: Boolean,
    down: Boolean
) {
    Text(
        modifier = modifier,
        text = text,
        textAlign = textAlign,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = SharedColours.changeColour(up, down)
    )
}

