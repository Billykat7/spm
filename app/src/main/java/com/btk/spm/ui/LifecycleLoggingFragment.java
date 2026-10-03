package com.btk.spm.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * A {@link Fragment} that logs each of its lifecycle callbacks through {@link LifecycleLog}.
 *
 * <p>The tab fragments extend it, so switching tabs in a debug build shows the whole sequence:
 * {@code onAttach}, {@code onCreate}, {@code onViewCreated}, {@code onStart}, {@code onResume} for
 * the tab that appears, and {@code onPause}, {@code onStop}, {@code onDestroyView},
 * {@code onDestroy}, {@code onDetach} for the one that goes. A fragment's view can be destroyed and
 * re-created while the fragment itself lives on, which is why the view callbacks are logged apart.
 */
public abstract class LifecycleLoggingFragment extends Fragment {

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        LifecycleLog.log(this, "onAttach");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LifecycleLog.log(this, "onCreate", savedInstanceState == null ? "new" : "restored");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LifecycleLog.log(this, "onViewCreated");
    }

    @Override
    public void onStart() {
        super.onStart();
        LifecycleLog.log(this, "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        LifecycleLog.log(this, "onResume");
    }

    @Override
    public void onPause() {
        LifecycleLog.log(this, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        LifecycleLog.log(this, "onStop");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        LifecycleLog.log(this, "onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        LifecycleLog.log(this, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        LifecycleLog.log(this, "onDetach");
        super.onDetach();
    }
}
