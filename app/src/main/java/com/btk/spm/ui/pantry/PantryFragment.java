package com.btk.spm.ui.pantry;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.databinding.FragmentPantryBinding;
import com.btk.spm.ui.LifecycleLoggingFragment;

/**
 * The Pantry tab: the list of ingredients in the user's pantry.
 *
 * <p>A placeholder until Issue 13 fills it: it shows what the tab will hold, so the navigation shell
 * can be built, run and tested first. Its lifecycle callbacks are logged in debug builds.
 */
public class PantryFragment extends LifecycleLoggingFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return FragmentPantryBinding.inflate(inflater, container, false).getRoot();
    }
}
