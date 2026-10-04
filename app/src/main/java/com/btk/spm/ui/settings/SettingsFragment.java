package com.btk.spm.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.btk.spm.BuildConfig;
import com.btk.spm.R;
import com.btk.spm.settings.AppPreferences;
import com.btk.spm.settings.PrefKey;

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
 *   <li><i>Expiring-soon alerts</i>: the daily check (Issue 29);</li>
 *   <li><i>Expiring soon</i>, 1–14 days: the pantry badges and the alert (decision 6);</li>
 *   <li><i>Units</i>: amounts on the pantry list and the detail screen, display only (decision 5);</li>
 *   <li><i>Count expired items</i>: {@code MatchOptions.includeExpired} on the Recipes tab and the
 *       detail screen (decision 6).</li>
 * </ul>
 * Every summary shows the current value, so the screen reads as a statement of what the app does.
 * Its lifecycle callbacks are logged in debug builds ({@code FragmentLifecycleLog}).
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // "Items expiring within 3 days are badged and reported": a plural, so set from here. Not a
        // SummaryProvider: SeekBarPreference stores a dragged value without notifying, so a provider
        // would keep saying "3 days" next to a bar at 7. The change listener sees every new value.
        SeekBarPreference threshold = requirePreference(PrefKey.EXPIRY_THRESHOLD_DAYS);
        threshold.setSummary(thresholdSummary(threshold.getValue()));
        threshold.setOnPreferenceChangeListener((preference, newValue) -> {
            preference.setSummary(thresholdSummary((Integer) newValue));
            return true;
        });

        requirePreference(PrefKey.ABOUT_VERSION).setSummary(BuildConfig.VERSION_NAME);
        requirePreference(PrefKey.ABOUT_REPOSITORY).setOnPreferenceClickListener(preference -> {
            openRepository();
            return true;
        });
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
