package com.nuolemo.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    private data class DurationOption(val seconds: Int, val label: String)

    private val durationOptions by lazy {
        listOf(
            DurationOption(30, SettingsPresentation.durationLabel(this, 30)),
            DurationOption(60, SettingsPresentation.durationLabel(this, 60)),
            DurationOption(120, SettingsPresentation.durationLabel(this, 120)),
            DurationOption(0, SettingsPresentation.durationLabel(this, 0)),
        )
    }

    private lateinit var rootView: View
    private lateinit var editKeywords: TextInputEditText
    private lateinit var editPlates: TextInputEditText
    private lateinit var inputDuration: MaterialAutoCompleteTextView
    private lateinit var switchVibrate: SwitchMaterial
    private lateinit var switchMaximizeVolume: SwitchMaterial
    private lateinit var chipSmsPermission: Chip
    private lateinit var chipNotificationPermission: Chip
    private lateinit var chipFullScreenPermission: Chip
    private lateinit var buttonRequestSms: MaterialButton
    private lateinit var buttonRequestNotifications: MaterialButton
    private lateinit var buttonOpenFullScreenSettings: MaterialButton
    private lateinit var buttonOpenAppSettings: MaterialButton
    private lateinit var buttonOpenBatterySettings: MaterialButton
    private lateinit var buttonSaveAndClose: MaterialButton
    private lateinit var buttonTestAlarm: MaterialButton

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermissionStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindViews()
        setupDurationMenu()
        populateSettings(SettingsStore.load(this))
        bindActions()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.settingsRootContent)
        editKeywords = findViewById(R.id.editKeywords)
        editPlates = findViewById(R.id.editPlates)
        inputDuration = findViewById(R.id.inputAlarmDuration)
        switchVibrate = findViewById(R.id.switchVibrate)
        switchMaximizeVolume = findViewById(R.id.switchMaximizeVolume)
        chipSmsPermission = findViewById(R.id.chipSmsPermission)
        chipNotificationPermission = findViewById(R.id.chipNotificationPermission)
        chipFullScreenPermission = findViewById(R.id.chipFullScreenPermission)
        buttonRequestSms = findViewById(R.id.buttonRequestSms)
        buttonRequestNotifications = findViewById(R.id.buttonRequestNotifications)
        buttonOpenFullScreenSettings = findViewById(R.id.buttonFullScreenSettings)
        buttonOpenAppSettings = findViewById(R.id.buttonOpenAppSettings)
        buttonOpenBatterySettings = findViewById(R.id.buttonOpenBatterySettings)
        buttonSaveAndClose = findViewById(R.id.buttonSaveAndClose)
        buttonTestAlarm = findViewById(R.id.buttonTestAlarm)
    }

    private fun setupDurationMenu() {
        inputDuration.setSimpleItems(durationOptions.map { it.label }.toTypedArray())
        inputDuration.keyListener = null
    }

    private fun bindActions() {
        buttonRequestSms.setOnClickListener {
            if (SystemSettingsNavigator.hasSmsPermission(this)) {
                SystemSettingsNavigator.openAppDetailsSettings(this)
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
            }
        }

        buttonRequestNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || AlarmNotifier.areNotificationsEnabled(this)) {
                SystemSettingsNavigator.openNotificationSettings(this)
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        buttonOpenFullScreenSettings.setOnClickListener {
            SystemSettingsNavigator.openFullScreenIntentSettings(this)
        }

        buttonOpenAppSettings.setOnClickListener {
            SystemSettingsNavigator.openAppDetailsSettings(this)
        }

        buttonOpenBatterySettings.setOnClickListener {
            SystemSettingsNavigator.openBatteryOptimizationSettings(this)
        }

        buttonSaveAndClose.setOnClickListener {
            persistSettings()
            setResult(RESULT_OK)
            finish()
        }

        buttonTestAlarm.setOnClickListener {
            persistSettings()
            triggerTestAlarm()
        }
    }

    private fun populateSettings(settings: AppSettings) {
        editKeywords.setText(SettingsStore.formatEditorInput(settings.keywords))
        editPlates.setText(SettingsStore.formatEditorInput(settings.plateNumbers))
        inputDuration.setText(SettingsPresentation.durationLabel(this, settings.alarmDurationSeconds), false)
        switchVibrate.isChecked = settings.vibrate
        switchMaximizeVolume.isChecked = settings.maximizeVolume
    }

    private fun collectSettings(): AppSettings {
        val currentSettings = SettingsStore.load(this)
        return currentSettings.copy(
            keywords = SettingsStore.sanitizeKeywords(editKeywords.text?.toString().orEmpty()),
            plateNumbers = SettingsStore.sanitizePlateNumbers(editPlates.text?.toString().orEmpty()),
            alarmDurationSeconds = durationSecondsFor(inputDuration.text?.toString().orEmpty()),
            vibrate = switchVibrate.isChecked,
            maximizeVolume = switchMaximizeVolume.isChecked,
        )
    }

    private fun persistSettings() {
        SettingsStore.save(this, collectSettings())
    }

    private fun updatePermissionStatus() {
        styleChip(
            chip = chipSmsPermission,
            text =
                if (SystemSettingsNavigator.hasSmsPermission(this)) {
                    getString(R.string.status_sms_granted)
                } else {
                    getString(R.string.status_sms_missing)
                },
            tone = if (SystemSettingsNavigator.hasSmsPermission(this)) Tone.GOOD else Tone.DANGER,
        )

        val notificationsEnabled = AlarmNotifier.areNotificationsEnabled(this)
        styleChip(
            chip = chipNotificationPermission,
            text =
                if (notificationsEnabled) {
                    getString(R.string.status_notifications_granted)
                } else {
                    getString(R.string.status_notifications_missing)
                },
            tone = if (notificationsEnabled) Tone.GOOD else Tone.WARNING,
        )

        val fullScreenAllowed = AlarmNotifier.canUseFullScreenIntent(this)
        styleChip(
            chip = chipFullScreenPermission,
            text =
                if (fullScreenAllowed) {
                    getString(R.string.status_full_screen_ready)
                } else {
                    getString(R.string.status_full_screen_missing)
                },
            tone = if (fullScreenAllowed) Tone.GOOD else Tone.WARNING,
        )

        buttonRequestSms.text =
            if (SystemSettingsNavigator.hasSmsPermission(this)) {
                getString(R.string.button_open_sms_settings)
            } else {
                getString(R.string.button_request_sms)
            }

        buttonRequestNotifications.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        buttonRequestNotifications.text =
            if (notificationsEnabled) {
                getString(R.string.button_notification_settings)
            } else {
                getString(R.string.button_request_notifications)
            }

        buttonOpenFullScreenSettings.text =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                getString(R.string.button_full_screen_settings)
            } else {
                getString(R.string.button_notification_settings)
            }
    }

    private fun triggerTestAlarm() {
        val sender = getString(R.string.test_sender)
        val body = getString(R.string.test_sms_body)
        val launchResult = AlarmLaunchHelper.startAlarm(this, sender, body)

        startActivity(AlarmNotifier.createAlarmActivityIntent(this, sender, body))
        Snackbar.make(
            rootView,
            if (launchResult == AlarmLaunchResult.FOREGROUND_SERVICE_STARTED) {
                R.string.test_alarm_started
            } else {
                R.string.test_alarm_fallback
            },
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    private fun durationSecondsFor(label: String): Int {
        return durationOptions.firstOrNull { it.label == label }?.seconds ?: 60
    }

    private fun styleChip(chip: Chip, text: String, tone: Tone) {
        chip.text = text
        val backgroundColor = ContextCompat.getColor(this, tone.backgroundColorRes)
        val textColor = ContextCompat.getColor(this, tone.textColorRes)
        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(backgroundColor)
        chip.setTextColor(textColor)
    }

    private enum class Tone(val backgroundColorRes: Int, val textColorRes: Int) {
        GOOD(R.color.status_good_bg, R.color.status_good_fg),
        WARNING(R.color.status_warning_bg, R.color.status_warning_fg),
        DANGER(R.color.status_danger_bg, R.color.status_danger_fg),
    }
}
