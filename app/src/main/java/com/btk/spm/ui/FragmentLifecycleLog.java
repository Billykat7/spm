package com.btk.spm.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * Logs each lifecycle callback of every Fragment {@link MainActivity} hosts, through {@link LifecycleLog}.
 *
 * <p>Switching tabs in a debug build shows the whole sequence: {@code onAttach}, {@code onCreate},
 * {@code onViewCreated}, {@code onStart}, {@code onResume} for the tab that appears, and
 * {@code onPause}, {@code onStop}, {@code onDestroyView}, {@code onDestroy}, {@code onDetach} for the
 * one that goes. A Fragment's view can be destroyed and created again while the Fragment itself lives
 * on, which is why the view callbacks are logged apart.
 *
 * <p>It watches the {@link FragmentManager} rather than being a base class, because the Settings tab
 * already extends {@code PreferenceFragmentCompat} (Issue 28) and Java has one superclass: this way
 * all three tabs are logged the same way, whatever they extend. Android calls each method right after
 * the Fragment's own callback.
 */
public final class FragmentLifecycleLog extends FragmentManager.FragmentLifecycleCallbacks {

    @Override
    public void onFragmentAttached(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Context context) {
        LifecycleLog.log(f, "onAttach");
    }

    @Override
    public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle savedInstanceState) {
        LifecycleLog.log(f, "onCreate", savedInstanceState == null ? "new" : "restored");
    }

    @Override
    public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull View v,
                                      @Nullable Bundle savedInstanceState) {
        LifecycleLog.log(f, "onViewCreated");
    }

    @Override
    public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment f) {
        LifecycleLog.log(f, "onStart");
    }

    @Override
    public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
        LifecycleLog.log(f, "onResume");
    }

    @Override
    public void onFragmentPaused(@NonNull FragmentManager fm, @NonNull Fragment f) {
        LifecycleLog.log(f, "onPause");
    }

    @Override
    public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
        LifecycleLog.log(f, "onStop");
    }

    @Override
    public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
        LifecycleLog.log(f, "onDestroyView");
    }

    @Override
    public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
        LifecycleLog.log(f, "onDestroy");
    }

    @Override
    public void onFragmentDetached(@NonNull FragmentManager fm, @NonNull Fragment f) {
        LifecycleLog.log(f, "onDetach");
    }
}
