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
import com.google.android.material.button.MaterialButton

class AlarmActivity : AppCompatActivity() {
    private val handoffHandler = Handler(Looper.getMainLooper())
    private var handoffChecks = 0
    private var sender: String? = null
    private var body: String = ""

    private lateinit var statusText: TextView
    private lateinit var senderText: TextView
    private lateinit var bodyText: TextView
    private lateinit var stopButton: MaterialButton

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
        stopButton = findViewById(R.id.buttonStopAlarm)

        sender = intent.getStringExtra(AlarmService.EXTRA_SENDER)
        body =
            intent.getStringExtra(AlarmService.EXTRA_SMS_BODY)
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.test_sms_body)

        senderText.text = sender ?: getString(R.string.test_sender)
        bodyText.text = body

        stopButton.setOnClickListener {
            AlarmStopReceiver.stopAlarm(this)
            finish()
        }

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
        if (isFinishing) {
            AlarmPlaybackController.stopIfOwnedBy(
                owner = AlarmPlaybackController.Owner.ACTIVITY,
                notifyListener = false,
            )
            if (!AlarmService.isRunning()) {
                AlarmNotifier.cancelAlarmNotification(this)
            }
        }
        super.onDestroy()
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
        AlarmPlaybackController.start(
            context = this,
            settings = SettingsStore.load(this),
            sender = sender,
            body = body,
            owner = AlarmPlaybackController.Owner.ACTIVITY,
            onStopped = {
                AlarmNotifier.cancelAlarmNotification(this)
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            },
        )
        AlarmNotifier.showUrgentAlarmNotification(this, sender, body)
    }

    private fun configureAlarmWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }
}
