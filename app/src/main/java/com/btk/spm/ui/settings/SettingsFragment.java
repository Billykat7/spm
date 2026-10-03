package com.btk.spm.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.databinding.FragmentSettingsBinding;
import com.btk.spm.ui.LifecycleLoggingFragment;

/**
 * The Settings tab: expiring-soon alerts, units and the matching options.
 *
 * <p>A placeholder until Issue 28 fills it: it shows what the tab will hold, so the navigation shell
 * can be built, run and tested first. Its lifecycle callbacks are logged in debug builds.
 */
public class SettingsFragment extends LifecycleLoggingFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return FragmentSettingsBinding.inflate(inflater, container, false).getRoot();
    }
}
