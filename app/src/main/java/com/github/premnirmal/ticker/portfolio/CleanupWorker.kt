package com.github.premnirmal.ticker.portfolio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.premnirmal.ticker.model.StocksProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    companion object {
        const val TAG = "CleanupWorker"
        const val TAG_PERIODIC = "CleanupWorker_Periodic"
    }

    private val stocksProvider: StocksProvider by inject()

    override suspend fun doWork(): Result {
        // The fetch set (tickerSet) is derived from the All Symbols master list, so there are no
        // orphaned tracked symbols to prune. This just drops stored quotes whose symbol is no longer
        // tracked (storage hygiene).
        stocksProvider.cleanup()
        Timber.d("Cleanup success")
        return Result.success()
    }
}
