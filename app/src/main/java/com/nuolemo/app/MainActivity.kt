package com.nuolemo.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

class MainActivity : AppCompatActivity() {
    private data class DurationOption(val seconds: Int, val label: String)

    private val durationOptions by lazy {
        listOf(
            DurationOption(30, getString(R.string.duration_30)),
            DurationOption(60, getString(R.string.duration_60)),
            DurationOption(120, getString(R.string.duration_120)),
            DurationOption(0, getString(R.string.duration_manual)),
        )
    }

    private lateinit var rootView: View
    private lateinit var switchEnabled: SwitchMaterial
    private lateinit var switchVibrate: SwitchMaterial
    private lateinit var switchMaximizeVolume: SwitchMaterial
    private lateinit var editKeywords: TextInputEditText
    private lateinit var editPlates: TextInputEditText
    private lateinit var inputDuration: MaterialAutoCompleteTextView
    private lateinit var chipSmsPermission: Chip
    private lateinit var chipNotificationPermission: Chip
    private lateinit var chipFullScreenPermission: Chip
    private lateinit var chipEnabledStatus: Chip
    private lateinit var buttonRequestSms: MaterialButton
    private lateinit var buttonRequestNotifications: MaterialButton
    private lateinit var buttonOpenFullScreenSettings: MaterialButton
    private lateinit var buttonOpenAppSettings: MaterialButton
    private lateinit var buttonOpenBatterySettings: MaterialButton
    private lateinit var buttonSave: MaterialButton
    private lateinit var buttonTest: MaterialButton

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermissionStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupDurationMenu()
        populateSettings(SettingsStore.load(this))
        bindActions()
        maybeRequestEssentialPermissions()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onPause() {
        saveSettings(showFeedback = false)
        super.onPause()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.rootContent)
        switchEnabled = findViewById(R.id.switchEnabled)
        switchVibrate = findViewById(R.id.switchVibrate)
        switchMaximizeVolume = findViewById(R.id.switchMaximizeVolume)
        editKeywords = findViewById(R.id.editKeywords)
        editPlates = findViewById(R.id.editPlates)
        inputDuration = findViewById(R.id.inputAlarmDuration)
        chipSmsPermission = findViewById(R.id.chipSmsPermission)
        chipNotificationPermission = findViewById(R.id.chipNotificationPermission)
        chipFullScreenPermission = findViewById(R.id.chipFullScreenPermission)
        chipEnabledStatus = findViewById(R.id.chipEnabledStatus)
        buttonRequestSms = findViewById(R.id.buttonRequestSms)
        buttonRequestNotifications = findViewById(R.id.buttonRequestNotifications)
        buttonOpenFullScreenSettings = findViewById(R.id.buttonFullScreenSettings)
        buttonOpenAppSettings = findViewById(R.id.buttonOpenAppSettings)
        buttonOpenBatterySettings = findViewById(R.id.buttonOpenBatterySettings)
        buttonSave = findViewById(R.id.buttonSave)
        buttonTest = findViewById(R.id.buttonTestAlarm)
    }

    private fun setupDurationMenu() {
        inputDuration.setSimpleItems(durationOptions.map { it.label }.toTypedArray())
        inputDuration.keyListener = null
    }

    private fun bindActions() {
        switchEnabled.setOnCheckedChangeListener { _, _ ->
            updateEnabledChip()
            saveSettings(showFeedback = false)
        }
        switchVibrate.setOnCheckedChangeListener { _, _ -> saveSettings(showFeedback = false) }
        switchMaximizeVolume.setOnCheckedChangeListener { _, _ -> saveSettings(showFeedback = false) }

        editKeywords.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveSettings(showFeedback = false)
            }
        }

        editPlates.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveSettings(showFeedback = false)
            }
        }

        inputDuration.setOnItemClickListener { _, _, _, _ ->
            saveSettings(showFeedback = false)
        }

        buttonRequestSms.setOnClickListener {
            if (hasSmsPermission()) {
                openAppDetailsSettings()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
            }
        }

        buttonRequestNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || AlarmNotifier.areNotificationsEnabled(this)) {
                openNotificationSettings()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        buttonOpenFullScreenSettings.setOnClickListener {
            openFullScreenIntentSettings()
        }

        buttonOpenAppSettings.setOnClickListener {
            openAppDetailsSettings()
        }

        buttonOpenBatterySettings.setOnClickListener {
            openBatteryOptimizationSettings()
        }

        buttonSave.setOnClickListener {
            saveSettings(showFeedback = true)
        }

        buttonTest.setOnClickListener {
            saveSettings(showFeedback = false)
            triggerTestAlarm()
        }
    }

    private fun populateSettings(settings: AppSettings) {
        switchEnabled.isChecked = settings.enabled
        switchVibrate.isChecked = settings.vibrate
        switchMaximizeVolume.isChecked = settings.maximizeVolume
        editKeywords.setText(SettingsStore.formatEditorInput(settings.keywords))
        editPlates.setText(SettingsStore.formatEditorInput(settings.plateNumbers))
        inputDuration.setText(durationLabelFor(settings.alarmDurationSeconds), false)
        updateEnabledChip()
    }

    private fun collectSettings(): AppSettings {
        return AppSettings(
            enabled = switchEnabled.isChecked,
            keywords = SettingsStore.sanitizeKeywords(editKeywords.text?.toString().orEmpty()),
            plateNumbers = SettingsStore.sanitizePlateNumbers(editPlates.text?.toString().orEmpty()),
            alarmDurationSeconds = durationSecondsFor(inputDuration.text?.toString().orEmpty()),
            vibrate = switchVibrate.isChecked,
            maximizeVolume = switchMaximizeVolume.isChecked,
        )
    }

    private fun saveSettings(showFeedback: Boolean) {
        SettingsStore.save(this, collectSettings())
        if (showFeedback) {
            Snackbar.make(rootView, R.string.save_success, Snackbar.LENGTH_SHORT).show()
        }
        updateEnabledChip()
    }

    private fun updatePermissionStatus() {
        styleChip(
            chip = chipSmsPermission,
            text = if (hasSmsPermission()) getString(R.string.status_sms_granted) else getString(R.string.status_sms_missing),
            tone = if (hasSmsPermission()) Tone.GOOD else Tone.DANGER,
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
            if (hasSmsPermission()) {
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

    private fun maybeRequestEssentialPermissions() {
        val missingPermissions = mutableListOf<String>()
        if (!hasSmsPermission()) {
            missingPermissions += Manifest.permission.RECEIVE_SMS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlarmNotifier.areNotificationsEnabled(this)) {
            missingPermissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun triggerTestAlarm() {
        val sender = getString(R.string.test_sender)
        val body = getString(R.string.test_sms_body)
        val serviceIntent =
            Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_START
                putExtra(AlarmService.EXTRA_SENDER, sender)
                putExtra(AlarmService.EXTRA_SMS_BODY, body)
            }
        val alarmPageIntent = AlarmNotifier.createAlarmActivityIntent(this, sender, body)

        runCatching {
            ContextCompat.startForegroundService(this, serviceIntent)
        }.onSuccess {
            startActivity(alarmPageIntent)
            Snackbar.make(rootView, R.string.test_alarm_started, Snackbar.LENGTH_SHORT).show()
        }.onFailure {
            AlarmNotifier.showUrgentAlarmNotification(this, sender, body)
            startActivity(alarmPageIntent)
            Snackbar.make(rootView, R.string.test_alarm_fallback, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun openFullScreenIntentSettings() {
        val primaryIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = packageUri()
                }
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            }

        launchIntentOrFallback(primaryIntent, appDetailsIntent())
    }

    private fun openNotificationSettings() {
        launchIntentOrFallback(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            },
            appDetailsIntent(),
        )
    }

    private fun openAppDetailsSettings() {
        launchIntentOrFallback(appDetailsIntent(), Intent(Settings.ACTION_SETTINGS))
    }

    private fun openBatteryOptimizationSettings() {
        launchIntentOrFallback(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetailsIntent(),
        )
    }

    private fun launchIntentOrFallback(primary: Intent, fallback: Intent) {
        try {
            startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            startActivity(fallback)
        }
    }

    private fun appDetailsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = packageUri()
        }
    }

    private fun packageUri(): Uri = Uri.parse("package:$packageName")

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun durationLabelFor(seconds: Int): String {
        return durationOptions.firstOrNull { it.seconds == seconds }?.label
            ?: durationOptions.first { it.seconds == 60 }.label
    }

    private fun durationSecondsFor(label: String): Int {
        return durationOptions.firstOrNull { it.label == label }?.seconds ?: 60
    }

    private fun updateEnabledChip() {
        styleChip(
            chip = chipEnabledStatus,
            text = if (switchEnabled.isChecked) getString(R.string.status_enabled) else getString(R.string.status_paused),
            tone = if (switchEnabled.isChecked) Tone.GOOD else Tone.WARNING,
        )
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
