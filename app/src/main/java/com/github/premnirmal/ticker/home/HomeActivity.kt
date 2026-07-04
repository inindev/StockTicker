package com.github.premnirmal.ticker.home

import android.Manifest
import android.os.Build.VERSION
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.github.premnirmal.ticker.base.BaseActivity
import com.github.premnirmal.ticker.hasNotificationPermission
import com.github.premnirmal.ticker.navigation.Graph
import com.github.premnirmal.ticker.navigation.RootNavigationGraphHost
import com.github.premnirmal.tickerwidget.R
import com.google.accompanist.adaptive.calculateDisplayFeatures
import kotlinx.coroutines.delay
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeActivity : BaseActivity() {
    override val simpleName = "HomeActivity"

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private val viewModel: HomeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (VERSION.SDK_INT >= 33) {
            requestPermissionLauncher = registerForActivityResult(RequestPermission()) { granted ->
                if (granted) {
                    viewModel.initNotifications()
                } else {
                    appMessaging.sendSnackbar(R.string.notification_alerts_required_message)
                }
            }
        }
        if (VERSION.SDK_INT >= 33 && appPreferences.notificationAlerts() && !hasNotificationPermission()) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) {
            stocksProvider.schedule()
        }
    }

    @Composable
    override fun ShowContent() {
        HomeScreen()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun HomeScreen() {
        val windowSizeClass = calculateWindowSizeClass(this)
        val navHostController = rememberNavController()
        RootNavigationGraphHost(
            windowWidthSizeClass = windowSizeClass.widthSizeClass,
            windowHeightSizeClass = windowSizeClass.heightSizeClass,
            displayFeatures = calculateDisplayFeatures(this),
            navHostController = navHostController
        )
        LaunchedEffect(Unit) {
            intent.getStringExtra(EXTRA_SYMBOL)?.let {
                navHostController.navigate(route = Graph.quoteDetail(it))
            }
        }
        LaunchedEffect(appPreferences.getLastSavedVersionCode(), appPreferences.tutorialShown()) {
            delay(1000L) // delay to ensure splash screen is shown
            viewModel.checkShowWhatsNew()
            viewModel.checkShowTutorial()
        }
        DisposableEffect(Unit) {
            viewModel.fetchPortfolioInRealTime()
            onDispose {
                viewModel.stopRealTimeFetch()
            }
        }
    }

    companion object {
        const val EXTRA_SYMBOL: String = "EXTRA_SYMBOL"
    }
}
