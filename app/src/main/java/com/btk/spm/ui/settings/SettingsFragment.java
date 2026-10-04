package com.btk.spm.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.btk.spm.databinding.FragmentSettingsBinding;

/**
 * The Settings tab: expiring-soon alerts, units and the matching options.
 *
 * <p>A placeholder until Issue 28 fills it: it shows what the tab will hold, so the navigation shell
 * can be built, run and tested first. Its lifecycle callbacks are logged in debug builds
 * ({@code FragmentLifecycleLog}).
 */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return FragmentSettingsBinding.inflate(inflater, container, false).getRoot();
    }
}
