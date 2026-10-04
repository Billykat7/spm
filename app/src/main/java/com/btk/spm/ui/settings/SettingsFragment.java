package com.btk.spm.ui.settings;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.btk.spm.BuildConfig;
import com.btk.spm.R;
import com.btk.spm.domain.validation.Validators;
import com.btk.spm.notifications.ExpiryAlertScheduler;
import com.btk.spm.notifications.NotificationAccess;
import com.btk.spm.settings.AppPreferences;
import com.btk.spm.settings.PrefKey;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The Settings tab, the fifth screen the brief asks for (§2.2, §3.1): expiring-soon alerts and their
 * threshold, the units amounts are shown in, whether expired items count when matching, and the app's
 * version.
 *
 * <p>A {@link PreferenceFragmentCompat} over {@code res/xml/preferences.xml}, so the library stores
 * each choice in the default {@code SharedPreferences} file, keeps it across restarts and draws the
 * screen in the app's theme, light or dark. The keys in that XML are {@link PrefKey} strings, and the
 * code that acts on a setting reads it through {@link AppPreferences}, never from here: this class
 * only wires what the XML cannot say, the threshold's plural summary, the version name and the link
 * to the repository. Nothing on the screen is decorative; each setting has a reader:
 * <ul>
 *   <li><i>Expiring-soon alerts</i>: the daily check, {@code ExpiryCheckWorker} (Issue 29);</li>
 *   <li><i>Expiring soon</i>, 1–14 days: the pantry badges and the alert (decision 6);</li>
 *   <li><i>Units</i>: amounts on the pantry list and the detail screen, display only (decision 5);</li>
 *   <li><i>Count expired items</i>: {@code MatchOptions.includeExpired} on the Recipes tab and the
 *       detail screen (decision 6).</li>
 * </ul>
 * Every summary shows the current value, so the screen reads as a statement of what the app does.
 *
 * <p><b>The alert and its permission (Issue 29).</b> Turning the alert on schedules the daily check
 * and turning it off cancels it ({@link ExpiryAlertScheduler}). On Android 13 and later, turning it on
 * without {@code POST_NOTIFICATIONS} asks for it through {@link ActivityResultContracts.RequestPermission},
 * after a short explanation if Android says one is due. If the user says no, the switch goes back off,
 * its summary says why, and a <i>Notification settings</i> entry opens the system page where it can
 * be allowed later; below Android 13 there is no prompt. "Send a test alert now" runs the same check
 * at once. Its lifecycle callbacks are logged in debug builds ({@code FragmentLifecycleLog}).
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    /**
     * Asks for {@code POST_NOTIFICATIONS} and hears the answer. Registered when the Fragment is
     * created, as the Activity Result API requires, so an answer that arrives after a rotation is
     * still heard.
     */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onPermissionAnswer);

    /** The alert's switch; set in {@link #onCreatePreferences}. */
    private SwitchPreferenceCompat alerts;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // "Items expiring within 3 days are badged and reported": a plural, so set from here. Not a
        // SummaryProvider: SeekBarPreference stores a dragged value without notifying, so a provider
        // would keep saying "3 days" next to a bar at 7. The change listener sees every new value.
        SeekBarPreference threshold = requirePreference(PrefKey.EXPIRY_THRESHOLD_DAYS);
        threshold.setSummary(thresholdSummary(threshold.getValue()));
        threshold.setOnPreferenceChangeListener((preference, newValue) -> {
            // The seek bar's own range is 1–14 already; the validator is the rule it must agree with,
            // and a value it refuses is not stored (Issue 31)
            int days = (Integer) newValue;
            if (!Validators.validateThresholdDays(days).isOk()) {
                return false;
            }
            preference.setSummary(thresholdSummary(days));
            return true;
        });

        alerts = requirePreference(PrefKey.EXPIRY_ALERTS_ENABLED);
        alerts.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean on = (Boolean) newValue;
            ExpiryAlertScheduler.sync(requireContext(), on);
            // The SDK check is also inside needsRuntimePermission; repeated so Lint sees the API 33 guard
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && NotificationAccess.needsRuntimePermission(requireContext())) {
                askForNotificationPermission();
            }
            return true;
        });
        requirePreference(PrefKey.SEND_TEST_ALERT).setOnPreferenceClickListener(preference -> {
            ExpiryAlertScheduler.runOnce(requireContext());
            return true;
        });
        requirePreference(PrefKey.NOTIFICATION_SETTINGS).setOnPreferenceClickListener(preference -> {
            startActivity(NotificationAccess.settingsIntent(requireContext()));
            return true;
        });

        requirePreference(PrefKey.ABOUT_VERSION).setSummary(BuildConfig.VERSION_NAME);
        requirePreference(PrefKey.ABOUT_REPOSITORY).setOnPreferenceClickListener(preference -> {
            openRepository();
            return true;
        });
    }

    /** Back from the system settings, the user may have allowed or blocked the app: say so. */
    @Override
    public void onResume() {
        super.onResume();
        showWhetherTheAlertCanPost();
    }

    /**
     * Asks for the permission, after the explanation Android asks for when the user has already said
     * no once. "Not now" counts as a no. Android 13 and later only: below it there is no prompt.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void askForNotificationPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.notification_rationale_title)
                    .setMessage(R.string.notification_rationale_message)
                    .setPositiveButton(R.string.action_continue,
                            (dialog, which) -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton(R.string.action_not_now, (dialog, which) -> onPermissionAnswer(false))
                    .show();
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /**
     * Allowed: the alert stays on. Refused: the switch goes back off, the daily check is cancelled,
     * and the summary and the shortcut say why and where to change it.
     */
    private void onPermissionAnswer(boolean granted) {
        if (!granted) {
            // setChecked stores the value without calling the change listener, so cancel here
            alerts.setChecked(false);
            ExpiryAlertScheduler.cancel(requireContext());
        }
        showWhetherTheAlertCanPost();
    }

    /**
     * Shows the switch's summary and the settings shortcut for whether the app may post now, so a
     * switch that is on but cannot post never reads as if it works.
     */
    private void showWhetherTheAlertCanPost() {
        boolean blocked = !NotificationAccess.canPost(requireContext());
        alerts.setSummaryOn(blocked ? R.string.settings_expiry_alerts_on_blocked : R.string.settings_expiry_alerts_on);
        alerts.setSummaryOff(blocked ? R.string.settings_expiry_alerts_off_blocked : R.string.settings_expiry_alerts_off);
        requirePreference(PrefKey.NOTIFICATION_SETTINGS).setVisible(blocked);
    }

    /** The threshold's summary for {@code days}, with the right plural. */
    @NonNull
    private String thresholdSummary(int days) {
        return getResources().getQuantityString(R.plurals.settings_threshold_summary, days, days);
    }

    /**
     * Opens the project's GitHub page in whatever app handles web links. A device with no browser
     * says so instead of crashing, which a plain {@code <intent>} in the XML would do.
     */
    private void openRepository() {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.repository_url)));
        try {
            startActivity(view);
        } catch (ActivityNotFoundException noBrowser) {
            Toast.makeText(requireContext(), R.string.settings_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Finds the preference stored under {@code key} in the inflated XML.
     *
     * @throws IllegalStateException if the XML has no preference with that key, which
     *     {@code PreferencesXmlTest} catches first
     */
    @NonNull
    private <T extends Preference> T requirePreference(@NonNull PrefKey key) {
        T preference = findPreference(key.key());
        if (preference == null) {
            throw new IllegalStateException("preferences.xml has no " + key.key());
        }
        return preference;
    }
}
