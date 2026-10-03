package com.btk.spm.ui.recipes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.btk.spm.databinding.FragmentSuggestedRecipesBinding;
import com.btk.spm.ui.LifecycleLoggingFragment;

/**
 * The Recipes tab: the recipes the pantry can make right now.
 *
 * <p>A placeholder until Issue 23 fills it: it shows what the tab will hold, so the navigation shell
 * can be built, run and tested first. Its lifecycle callbacks are logged in debug builds.
 */
public class SuggestedRecipesFragment extends LifecycleLoggingFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return FragmentSuggestedRecipesBinding.inflate(inflater, container, false).getRoot();
    }
}
