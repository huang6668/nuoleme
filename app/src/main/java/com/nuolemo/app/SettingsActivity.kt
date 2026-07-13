package com.nuolemo.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePaddingRelative
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
    private lateinit var scrollView: View
    private lateinit var bottomActionBar: View
    private lateinit var textHealthSummary: TextView
    private lateinit var editKeywords: TextInputEditText
    private lateinit var editPlates: TextInputEditText
    private lateinit var inputDuration: MaterialAutoCompleteTextView
    private lateinit var switchVibrate: SwitchMaterial
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
    private lateinit var buttonBack: MaterialButton
    private var settingsPopulated = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermissionStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindViews()
        applySystemBarInsets()
        setupDurationMenu()
        populateSettings(SettingsStore.load(this))
        settingsPopulated = true
        bindActions()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onPause() {
        if (settingsPopulated) {
            persistSettings()
        }
        super.onPause()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.settingsRootContent)
        scrollView = findViewById(R.id.settingsScrollView)
        bottomActionBar = findViewById(R.id.bottomActionBar)
        textHealthSummary = findViewById(R.id.textHealthSummary)
        editKeywords = findViewById(R.id.editKeywords)
        editPlates = findViewById(R.id.editPlates)
        inputDuration = findViewById(R.id.inputAlarmDuration)
        switchVibrate = findViewById(R.id.switchVibrate)
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
        buttonBack = findViewById(R.id.buttonBack)
    }

    private fun applySystemBarInsets() {
        val scrollBaseStart = scrollView.paddingStart
        val scrollBaseTop = scrollView.paddingTop
        val scrollBaseEnd = scrollView.paddingEnd
        val scrollBaseBottom = scrollView.paddingBottom
        val barBaseStart = bottomActionBar.paddingStart
        val barBaseTop = bottomActionBar.paddingTop
        val barBaseEnd = bottomActionBar.paddingEnd
        val barBaseBottom = bottomActionBar.paddingBottom

        bottomActionBar.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val requiredBottomPadding = scrollBaseBottom + view.height
            if (scrollView.paddingBottom != requiredBottomPadding) {
                scrollView.updatePaddingRelative(bottom = requiredBottomPadding)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scrollView.updatePaddingRelative(
                start = scrollBaseStart + systemBars.left,
                top = scrollBaseTop + systemBars.top,
                end = scrollBaseEnd + systemBars.right,
            )
            bottomActionBar.updatePaddingRelative(
                start = barBaseStart + systemBars.left,
                top = barBaseTop,
                end = barBaseEnd + systemBars.right,
                bottom = barBaseBottom + systemBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun setupDurationMenu() {
        inputDuration.setSimpleItems(durationOptions.map { it.label }.toTypedArray())
        inputDuration.keyListener = null
    }

    private fun bindActions() {
        buttonBack.setOnClickListener { saveAndFinish() }

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
            saveAndFinish()
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
    }

    private fun collectSettings(): AppSettings {
        val currentSettings = SettingsStore.load(this)
        return currentSettings.copy(
            keywords = SettingsStore.sanitizeKeywords(editKeywords.text?.toString().orEmpty()),
            plateNumbers = SettingsStore.sanitizePlateNumbers(editPlates.text?.toString().orEmpty()),
            alarmDurationSeconds = durationSecondsFor(inputDuration.text?.toString().orEmpty()),
            vibrate = switchVibrate.isChecked,
        )
    }

    private fun persistSettings() {
        SettingsStore.save(this, collectSettings())
    }

    private fun saveAndFinish() {
        persistSettings()
        setResult(RESULT_OK)
        finish()
    }

    private fun updatePermissionStatus() {
        val smsGranted = SystemSettingsNavigator.hasSmsPermission(this)
        val notificationsEnabled = AlarmNotifier.areNotificationsEnabled(this)
        val fullScreenAllowed = notificationsEnabled && AlarmNotifier.canUseFullScreenIntent(this)
        val readyCount = listOf(smsGranted, notificationsEnabled, fullScreenAllowed).count { it }

        textHealthSummary.text =
            if (readyCount == TOTAL_HEALTH_CHECKS) {
                getString(R.string.settings_health_all_ready)
            } else {
                getString(R.string.settings_health_summary, readyCount, TOTAL_HEALTH_CHECKS)
            }

        styleChip(
            chip = chipSmsPermission,
            text = getString(if (smsGranted) R.string.status_ready_compact else R.string.status_action_compact),
            tone = if (smsGranted) Tone.GOOD else Tone.DANGER,
        )

        styleChip(
            chip = chipNotificationPermission,
            text = getString(if (notificationsEnabled) R.string.status_ready_compact else R.string.status_action_compact),
            tone = if (notificationsEnabled) Tone.GOOD else Tone.DANGER,
        )

        styleChip(
            chip = chipFullScreenPermission,
            text = getString(if (fullScreenAllowed) R.string.status_ready_compact else R.string.status_recommended_compact),
            tone = if (fullScreenAllowed) Tone.GOOD else Tone.WARNING,
        )

        buttonRequestSms.text =
            if (smsGranted) {
                getString(R.string.button_manage)
            } else {
                getString(R.string.button_allow)
            }

        buttonRequestNotifications.text =
            if (notificationsEnabled) {
                getString(R.string.button_manage)
            } else {
                getString(R.string.button_allow)
            }

        buttonOpenFullScreenSettings.text =
            if (fullScreenAllowed) {
                getString(R.string.button_manage)
            } else {
                getString(R.string.button_go_set)
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

    companion object {
        private const val TOTAL_HEALTH_CHECKS = 3
    }
}
