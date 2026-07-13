package com.nuolemo.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class AlarmActivity : AppCompatActivity() {
    private val handoffHandler = Handler(Looper.getMainLooper())
    private var handoffChecks = 0
    private var sender: String? = null
    private var body: String = ""

    private lateinit var statusText: TextView
    private lateinit var senderText: TextView
    private lateinit var bodyText: TextView
    private lateinit var slideToStopView: SlideToStopView

    private val handoffRunnable =
        object : Runnable {
            override fun run() {
                if (AlarmService.isRunning()) {
                    statusText.text = getString(R.string.alarm_activity_service_handoff)
                    return
                }

                startLocalFallbackIfNeeded()
                handoffChecks += 1
                if (handoffChecks < 4) {
                    handoffHandler.postDelayed(this, 500L)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()
        setContentView(R.layout.activity_alarm)

        statusText = findViewById(R.id.textAlarmStatus)
        senderText = findViewById(R.id.textAlarmSender)
        bodyText = findViewById(R.id.textAlarmBody)
        slideToStopView = findViewById(R.id.slideToStopAlarm)

        applyAlarmIntent(intent)

        slideToStopView.setOnSlideCompleteListener {
            AlarmStopReceiver.stopAlarm(this)
            finish()
        }

        tryStartAlarmService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyAlarmIntent(intent)
        tryStartAlarmService()
    }

    override fun onResume() {
        super.onResume()
        handoffChecks = 0
        handoffHandler.postDelayed(handoffRunnable, 350L)
    }

    override fun onPause() {
        handoffHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        handoffHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun applyAlarmIntent(intent: Intent) {
        sender = intent.getStringExtra(AlarmService.EXTRA_SENDER)
        body =
            intent.getStringExtra(AlarmService.EXTRA_SMS_BODY)
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.test_sms_body)

        senderText.text = sender ?: getString(R.string.test_sender)
        bodyText.text = body
    }

    private fun tryStartAlarmService() {
        if (AlarmService.isRunning()) {
            statusText.text = getString(R.string.alarm_activity_service_handoff)
            return
        }

        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_START
                    putExtra(AlarmService.EXTRA_SENDER, sender)
                    putExtra(AlarmService.EXTRA_SMS_BODY, body)
                },
            )
        }.onFailure {
            statusText.text = getString(R.string.alarm_activity_fallback)
            startLocalFallbackIfNeeded()
        }
    }

    private fun startLocalFallbackIfNeeded() {
        if (AlarmService.isRunning()) {
            statusText.text = getString(R.string.alarm_activity_service_handoff)
            return
        }

        statusText.text = getString(R.string.alarm_activity_fallback)
        val appContext = applicationContext
        val activityReference = WeakReference(this)
        runCatching {
            AlarmPlaybackController.start(
                context = this,
                settings = SettingsStore.load(this),
                sender = sender,
                body = body,
                owner = AlarmPlaybackController.Owner.ACTIVITY,
                onStopped = {
                    AlarmNotifier.cancelAlarmNotification(appContext)
                    activityReference.get()?.let { activity ->
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            activity.finish()
                        }
                    }
                },
            )
        }
        AlarmNotifier.showUrgentAlarmNotification(this, sender, body)
    }

    private fun configureAlarmWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}
