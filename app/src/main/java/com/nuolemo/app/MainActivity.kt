package com.nuolemo.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePaddingRelative
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var scrollView: View
    private lateinit var cardGuardStatus: MaterialCardView
    private lateinit var imageGuardStatus: ImageView
    private lateinit var switchGuardEnabled: SwitchMaterial
    private lateinit var textGuardStateKicker: TextView
    private lateinit var textGuardStateTitle: TextView
    private lateinit var textGuardStateSummary: TextView
    private lateinit var textReadinessSummary: TextView
    private lateinit var textCloseNote: TextView
    private lateinit var textRuleSummary: TextView
    private lateinit var textAlarmSummary: TextView
    private lateinit var chipSmsPermission: Chip
    private lateinit var chipNotificationPermission: Chip
    private lateinit var chipFullScreenPermission: Chip
    private lateinit var buttonPrimaryAction: MaterialButton
    private lateinit var buttonOpenSettings: MaterialButton

    private var renderingState = false
    private var primaryAction = PrimaryAction.OPEN_SETTINGS

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            refreshUi()
            if (result.resultCode == RESULT_OK) {
                Snackbar.make(rootView, R.string.settings_result_applied, Snackbar.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        applySystemBarInsets()
        bindActions()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.rootContent)
        scrollView = findViewById(R.id.mainScrollView)
        cardGuardStatus = findViewById(R.id.cardGuardStatus)
        imageGuardStatus = findViewById(R.id.imageGuardStatus)
        switchGuardEnabled = findViewById(R.id.switchGuardEnabled)
        textGuardStateKicker = findViewById(R.id.textGuardStateKicker)
        textGuardStateTitle = findViewById(R.id.textGuardStateTitle)
        textGuardStateSummary = findViewById(R.id.textGuardStateSummary)
        textReadinessSummary = findViewById(R.id.textReadinessSummary)
        textCloseNote = findViewById(R.id.textCloseNote)
        textRuleSummary = findViewById(R.id.textRuleSummary)
        textAlarmSummary = findViewById(R.id.textAlarmSummary)
        chipSmsPermission = findViewById(R.id.chipSmsPermission)
        chipNotificationPermission = findViewById(R.id.chipNotificationPermission)
        chipFullScreenPermission = findViewById(R.id.chipFullScreenPermission)
        buttonPrimaryAction = findViewById(R.id.buttonPrimaryAction)
        buttonOpenSettings = findViewById(R.id.buttonOpenSettings)
    }

    private fun applySystemBarInsets() {
        val baseStart = scrollView.paddingStart
        val baseTop = scrollView.paddingTop
        val baseEnd = scrollView.paddingEnd
        val baseBottom = scrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePaddingRelative(
                start = baseStart + systemBars.left,
                top = baseTop + systemBars.top,
                end = baseEnd + systemBars.right,
                bottom = baseBottom + systemBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(scrollView)
    }

    private fun bindActions() {
        switchGuardEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (renderingState) {
                return@setOnCheckedChangeListener
            }

            setGuardEnabled(isChecked)
            Snackbar.make(
                rootView,
                if (isChecked) R.string.guard_enabled_toast else R.string.guard_paused_toast,
                Snackbar.LENGTH_SHORT,
            ).show()
        }

        buttonOpenSettings.setOnClickListener { openSettings() }
        buttonPrimaryAction.setOnClickListener {
            when (primaryAction) {
                PrimaryAction.OPEN_SETTINGS -> openSettings()
                PrimaryAction.EXIT_AND_KEEP_GUARD -> finishAndRemoveTask()
                PrimaryAction.RESUME_GUARD -> switchGuardEnabled.isChecked = true
            }
        }
    }

    private fun setGuardEnabled(enabled: Boolean) {
        val currentSettings = SettingsStore.load(this)
        SettingsStore.save(this, currentSettings.copy(enabled = enabled))
        refreshUi()
    }

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun refreshUi() {
        val settings = SettingsStore.load(this)
        val health = GuardHealth.read(this)
        renderDashboard(settings, health)
        renderPermissionChips(health)
    }

    private fun renderDashboard(settings: AppSettings, health: GuardHealth) {
        renderingState = true
        switchGuardEnabled.isChecked = settings.enabled
        renderingState = false

        textRuleSummary.text = SettingsPresentation.rulesOverview(this, settings)
        textAlarmSummary.text = SettingsPresentation.alarmOverview(this, settings)
        textReadinessSummary.text =
            if (health.readyCount == GuardHealth.TOTAL_CHECKS) {
                getString(R.string.home_readiness_ready, GuardHealth.TOTAL_CHECKS)
            } else {
                getString(R.string.home_readiness_count, health.readyCount, GuardHealth.TOTAL_CHECKS)
            }

        when {
            !settings.enabled -> renderPausedState()
            health.essentialMissingCount > 0 -> renderAttentionState(health.essentialMissingCount)
            !health.fullScreenAllowed -> renderLimitedState()
            else -> renderReadyState()
        }
    }

    private fun renderReadyState() {
        renderHero(
            backgroundColor = R.color.hero_ready,
            kicker = R.string.home_status_ready_kicker,
            title = R.string.home_ready_title,
            summary = getString(R.string.home_ready_summary),
            buttonText = getString(R.string.button_exit_keep_guard),
            action = PrimaryAction.EXIT_AND_KEEP_GUARD,
            icon = R.drawable.ic_shield_check,
            showCloseNote = true,
        )
    }

    private fun renderLimitedState() {
        renderHero(
            backgroundColor = R.color.hero_limited,
            kicker = R.string.home_status_limited_kicker,
            title = R.string.home_limited_title,
            summary = getString(R.string.home_limited_summary),
            buttonText = getString(R.string.button_improve_lock_screen),
            action = PrimaryAction.OPEN_SETTINGS,
            icon = R.drawable.ic_shield_alert,
            showCloseNote = true,
        )
    }

    private fun renderAttentionState(missingCount: Int) {
        renderHero(
            backgroundColor = R.color.hero_attention,
            kicker = R.string.home_status_attention_kicker,
            title = R.string.home_attention_title,
            summary = getString(R.string.home_attention_summary, missingCount),
            buttonText = getString(R.string.button_fix_settings, missingCount),
            action = PrimaryAction.OPEN_SETTINGS,
            icon = R.drawable.ic_shield_alert,
            showCloseNote = false,
        )
    }

    private fun renderPausedState() {
        renderHero(
            backgroundColor = R.color.hero_paused,
            kicker = R.string.home_status_paused_kicker,
            title = R.string.home_paused_title,
            summary = getString(R.string.home_paused_summary),
            buttonText = getString(R.string.button_resume_guard),
            action = PrimaryAction.RESUME_GUARD,
            icon = R.drawable.ic_shield_paused,
            showCloseNote = false,
        )
    }

    private fun renderHero(
        backgroundColor: Int,
        kicker: Int,
        title: Int,
        summary: String,
        buttonText: String,
        action: PrimaryAction,
        icon: Int,
        showCloseNote: Boolean,
    ) {
        val resolvedColor = ContextCompat.getColor(this, backgroundColor)
        cardGuardStatus.setCardBackgroundColor(resolvedColor)
        textGuardStateKicker.setText(kicker)
        textGuardStateTitle.setText(title)
        textGuardStateSummary.text = summary
        buttonPrimaryAction.text = buttonText
        buttonPrimaryAction.setTextColor(resolvedColor)
        textCloseNote.visibility = if (showCloseNote) View.VISIBLE else View.GONE
        imageGuardStatus.setImageResource(icon)
        imageGuardStatus.alpha = 1f
        primaryAction = action
    }

    private fun renderPermissionChips(health: GuardHealth) {
        styleChip(
            chip = chipSmsPermission,
            text = getString(if (health.smsGranted) R.string.status_sms_ready_short else R.string.status_sms_missing_short),
            tone = if (health.smsGranted) Tone.GOOD else Tone.DANGER,
        )
        styleChip(
            chip = chipNotificationPermission,
            text =
                getString(
                    if (health.notificationsEnabled) {
                        R.string.status_notifications_ready_short
                    } else {
                        R.string.status_notifications_missing_short
                    },
                ),
            tone = if (health.notificationsEnabled) Tone.GOOD else Tone.DANGER,
        )
        styleChip(
            chip = chipFullScreenPermission,
            text =
                getString(
                    if (health.fullScreenAllowed) {
                        R.string.status_full_screen_ready_short
                    } else {
                        R.string.status_full_screen_missing_short
                    },
                ),
            tone = if (health.fullScreenAllowed) Tone.GOOD else Tone.WARNING,
        )
    }

    private fun styleChip(chip: Chip, text: String, tone: Tone) {
        chip.text = text
        val backgroundColor = ContextCompat.getColor(this, tone.backgroundColorRes)
        val textColor = ContextCompat.getColor(this, tone.textColorRes)
        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(backgroundColor)
        chip.setTextColor(textColor)
    }

    private data class GuardHealth(
        val smsGranted: Boolean,
        val notificationsEnabled: Boolean,
        val fullScreenAllowed: Boolean,
    ) {
        val readyCount: Int
            get() = listOf(smsGranted, notificationsEnabled, fullScreenAllowed).count { it }

        val essentialMissingCount: Int
            get() = listOf(smsGranted, notificationsEnabled).count { !it }

        companion object {
            const val TOTAL_CHECKS = 3

            fun read(activity: MainActivity): GuardHealth {
                val notificationsEnabled = AlarmNotifier.areNotificationsEnabled(activity)
                return GuardHealth(
                    smsGranted = SystemSettingsNavigator.hasSmsPermission(activity),
                    notificationsEnabled = notificationsEnabled,
                    fullScreenAllowed =
                        notificationsEnabled && AlarmNotifier.canUseFullScreenIntent(activity),
                )
            }
        }
    }

    private enum class PrimaryAction {
        RESUME_GUARD,
        OPEN_SETTINGS,
        EXIT_AND_KEEP_GUARD,
    }

    private enum class Tone(val backgroundColorRes: Int, val textColorRes: Int) {
        GOOD(R.color.status_good_bg, R.color.status_good_fg),
        WARNING(R.color.status_warning_bg, R.color.status_warning_fg),
        DANGER(R.color.status_danger_bg, R.color.status_danger_fg),
    }
}
