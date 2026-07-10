package com.nuolemo.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object SystemSettingsNavigator {
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openFullScreenIntentSettings(activity: Activity) {
        val primaryIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = packageUri(activity)
                }
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
            }

        launchIntentOrFallback(activity, primaryIntent, appDetailsIntent(activity))
    }

    fun openNotificationSettings(activity: Activity) {
        launchIntentOrFallback(
            activity,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            },
            appDetailsIntent(activity),
        )
    }

    fun openAppDetailsSettings(activity: Activity) {
        launchIntentOrFallback(activity, appDetailsIntent(activity), Intent(Settings.ACTION_SETTINGS))
    }

    fun openBatteryOptimizationSettings(activity: Activity) {
        launchIntentOrFallback(
            activity,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetailsIntent(activity),
        )
    }

    private fun launchIntentOrFallback(
        activity: Activity,
        primary: Intent,
        fallback: Intent,
    ) {
        try {
            activity.startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(fallback)
        }
    }

    private fun appDetailsIntent(activity: Activity): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = packageUri(activity)
        }
    }

    private fun packageUri(activity: Activity): Uri = Uri.parse("package:${activity.packageName}")
}
