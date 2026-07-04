package com.github.premnirmal.ticker.network.data

import com.github.premnirmal.ticker.components.AppNumberFormat
import com.github.premnirmal.ticker.components.DecimalFormatter

class PriceFormat(
    val currencyCode: String,
    val symbol: String,
    val prefix: Boolean = true
) {
    fun format(
        price: Float,
        formatter: DecimalFormatter = AppNumberFormat.selected
    ): String {
        val priceString = formatter.format(price)
        return if (prefix) {
            "$symbol$priceString"
        } else {
            "$priceString$symbol"
        }
    }
}
