package com.nuolemo.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var switchGuardEnabled: SwitchMaterial
    private lateinit var textGuardStateTitle: TextView
    private lateinit var textGuardStateSummary: TextView
    private lateinit var textRuleSummary: TextView
    private lateinit var textAlarmSummary: TextView
    private lateinit var chipEnabledStatus: Chip
    private lateinit var chipSmsPermission: Chip
    private lateinit var chipNotificationPermission: Chip
    private lateinit var chipFullScreenPermission: Chip
    private lateinit var buttonOpenSettings: MaterialButton

    private var renderingState = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshUi()
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            refreshUi()
            if (result.resultCode == RESULT_OK) {
                Snackbar.make(rootView, R.string.settings_result_applied, Snackbar.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        bindActions()
        maybeRequestEssentialPermissions()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.rootContent)
        switchGuardEnabled = findViewById(R.id.switchGuardEnabled)
        textGuardStateTitle = findViewById(R.id.textGuardStateTitle)
        textGuardStateSummary = findViewById(R.id.textGuardStateSummary)
        textRuleSummary = findViewById(R.id.textRuleSummary)
        textAlarmSummary = findViewById(R.id.textAlarmSummary)
        chipEnabledStatus = findViewById(R.id.chipEnabledStatus)
        chipSmsPermission = findViewById(R.id.chipSmsPermission)
        chipNotificationPermission = findViewById(R.id.chipNotificationPermission)
        chipFullScreenPermission = findViewById(R.id.chipFullScreenPermission)
        buttonOpenSettings = findViewById(R.id.buttonOpenSettings)
    }

    private fun bindActions() {
        switchGuardEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (renderingState) {
                return@setOnCheckedChangeListener
            }

            val currentSettings = SettingsStore.load(this)
            SettingsStore.save(this, currentSettings.copy(enabled = isChecked))
            refreshUi()
            Snackbar.make(
                rootView,
                if (isChecked) {
                    R.string.guard_enabled_toast
                } else {
                    R.string.guard_paused_toast
                },
                Snackbar.LENGTH_SHORT,
            ).show()
        }

        buttonOpenSettings.setOnClickListener {
            settingsLauncher.launch(android.content.Intent(this, SettingsActivity::class.java))
        }
    }

    private fun refreshUi() {
        val settings = SettingsStore.load(this)
        renderGuardState(settings)
        updatePermissionStatus()
    }

    private fun renderGuardState(settings: AppSettings) {
        renderingState = true
        switchGuardEnabled.isChecked = settings.enabled
        renderingState = false

        textGuardStateTitle.text =
            if (settings.enabled) {
                getString(R.string.guard_state_on_title)
            } else {
                getString(R.string.guard_state_off_title)
            }
        textGuardStateSummary.text =
            if (settings.enabled) {
                getString(R.string.guard_state_on_summary)
            } else {
                getString(R.string.guard_state_off_summary)
            }
        textRuleSummary.text = SettingsPresentation.rulesOverview(this, settings)
        textAlarmSummary.text = SettingsPresentation.alarmOverview(this, settings)

        styleChip(
            chip = chipEnabledStatus,
            text =
                if (settings.enabled) {
                    getString(R.string.status_guard_active)
                } else {
                    getString(R.string.status_guard_paused)
                },
            tone = if (settings.enabled) Tone.GOOD else Tone.WARNING,
        )
    }

    private fun updatePermissionStatus() {
        val smsGranted = SystemSettingsNavigator.hasSmsPermission(this)
        styleChip(
            chip = chipSmsPermission,
            text = if (smsGranted) getString(R.string.status_sms_granted) else getString(R.string.status_sms_missing),
            tone = if (smsGranted) Tone.GOOD else Tone.DANGER,
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

        buttonOpenSettings.text = getString(R.string.button_open_settings)
    }

    private fun maybeRequestEssentialPermissions() {
        val missingPermissions = mutableListOf<String>()
        if (!SystemSettingsNavigator.hasSmsPermission(this)) {
            missingPermissions += Manifest.permission.RECEIVE_SMS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlarmNotifier.areNotificationsEnabled(this)) {
            missingPermissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
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
