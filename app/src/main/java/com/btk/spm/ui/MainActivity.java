package com.btk.spm.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

import com.btk.spm.R;
import com.btk.spm.databinding.ActivityMainBinding;
import com.btk.spm.util.IntentKeys;

/**
 * The host of the app's three top-level screens (decision 3).
 *
 * <p>A {@code BottomNavigationView} swaps the Pantry, Recipes and Settings Fragments in one
 * container through the {@link FragmentManager}, and the toolbar title follows the selected
 * {@link Tab}. The selected tab survives rotation, Back from another tab returns to Pantry, and
 * {@link #intentFor(Context, Tab)} opens the host on a chosen tab from another screen.
 *
 * <p>Each lifecycle callback is logged in debug builds ({@link LifecycleLog}), which is how the video
 * shows the Activity lifecycle: {@code adb logcat -s Lifecycle}.
 */
public class MainActivity extends AppCompatActivity {

    /** Saved-state key of the selected tab; private to this Activity, so not an Intent extra. */
    private static final String STATE_SELECTED_TAB = "com.btk.spm.state.SELECTED_TAB";

    private ActivityMainBinding binding;
    private Tab selectedTab = Tab.PANTRY;

    /** Enabled on every tab but the first, so Back returns to Pantry before it leaves the app. */
    private final OnBackPressedCallback backToFirstTab = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            selectTab(Tab.PANTRY);
        }
    };

    /**
     * Returns the Intent that opens this screen on {@code tab}. If the host is already running it is
     * brought to the front and switched to the tab ({@link #onNewIntent(Intent)}) rather than opened
     * a second time; this is how a later screen, such as the empty suggestions' "add ingredients"
     * button (Issue 24), lands the user on the Pantry tab.
     *
     * @param context the screen starting the Intent
     * @param tab     the tab to show
     * @return an explicit Intent for {@code MainActivity}, carrying the tab in
     *     {@link IntentKeys#EXTRA_TAB}
     */
    @NonNull
    public static Intent intentFor(@NonNull Context context, @NonNull Tab tab) {
        return new Intent(context, MainActivity.class)
                .putExtra(IntentKeys.EXTRA_TAB, tab.name())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LifecycleLog.log(this, "onCreate", savedInstanceState == null ? "new" : "restored");
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge to edge draws under the system bars. Pad the top and sides here; the bottom
        // navigation pads itself for the gesture or button bar, so the bottom is left to it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, backToFirstTab);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(Tab.fromMenuItemId(item.getItemId()));
            return true;
        });
        // Tapping the tab that is already shown does nothing, rather than rebuilding its Fragment
        binding.bottomNav.setOnItemReselectedListener(item -> { });

        // After a rotation the saved tab wins; on a fresh start, the tab the Intent asks for
        Tab initial = savedInstanceState != null
                ? Tab.fromName(savedInstanceState.getString(STATE_SELECTED_TAB), Tab.PANTRY)
                : tabFrom(getIntent());
        selectTab(initial);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        LifecycleLog.log(this, "onNewIntent");
        setIntent(intent);
        selectTab(tabFrom(intent));
    }

    @Override
    protected void onStart() {
        super.onStart();
        LifecycleLog.log(this, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        LifecycleLog.log(this, "onResume", "showing " + selectedTab);
    }

    @Override
    protected void onPause() {
        LifecycleLog.log(this, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        LifecycleLog.log(this, "onStop");
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_TAB, selectedTab.name());
        LifecycleLog.log(this, "onSaveInstanceState", "saved " + selectedTab);
    }

    @Override
    protected void onDestroy() {
        LifecycleLog.log(this, "onDestroy", isChangingConfigurations() ? "configuration change" : "finishing");
        super.onDestroy();
    }

    /** Reads the tab an Intent asks for; Pantry when it asks for none or for one that does not exist. */
    private static Tab tabFrom(@Nullable Intent intent) {
        return Tab.fromName(intent == null ? null : intent.getStringExtra(IntentKeys.EXTRA_TAB), Tab.PANTRY);
    }

    /**
     * Selects {@code tab} from code: marks its item in the bottom navigation and shows it. Checking
     * the item directly, instead of {@code setSelectedItemId}, avoids the reselection path the
     * bottom navigation takes when the requested item is already its default.
     */
    private void selectTab(Tab tab) {
        binding.bottomNav.getMenu().findItem(tab.menuItemId()).setChecked(true);
        showTab(tab);
    }

    /**
     * Shows {@code tab}: the toolbar title, the Back behaviour and the Fragment. The Fragment is
     * tagged with the tab's name and only replaced when it is not already the one in the container,
     * so after a rotation the Fragment the {@link FragmentManager} restored is kept.
     */
    private void showTab(Tab tab) {
        selectedTab = tab;
        binding.toolbar.setTitle(tab.titleRes());
        backToFirstTab.setEnabled(tab != Tab.PANTRY);

        FragmentManager fragments = getSupportFragmentManager();
        if (fragments.findFragmentByTag(tab.name()) == null) {
            fragments.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.fragment_container, tab.newFragment(), tab.name())
                    .commit();
        }
    }
}
