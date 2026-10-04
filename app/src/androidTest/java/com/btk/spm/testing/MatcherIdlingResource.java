package com.btk.spm.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.espresso.IdlingResource;

import java.util.function.IntSupplier;

/**
 * Tells Espresso the matcher is busy while a match is queued or running (Issue 26).
 *
 * <p>Espresso waits for the main thread on its own, but the Recipes tab matches on a thread of its
 * own, so a view check could run between a pantry change and its result. Registered with
 * {@code IdlingRegistry}, this resource makes every {@code onView(...)} wait until the ViewModel's
 * {@code pendingMatches()} is 0. Only tests register it; the app never knows it exists.
 */
public final class MatcherIdlingResource implements IdlingResource {

    private final IntSupplier pendingMatches;

    @Nullable
    private volatile ResourceCallback callback;

    /**
     * Creates the resource.
     *
     * @param pendingMatches how many matches are queued or running now, such as
     *                       {@code viewModel::pendingMatches}
     */
    public MatcherIdlingResource(@NonNull IntSupplier pendingMatches) {
        this.pendingMatches = pendingMatches;
    }

    @NonNull
    @Override
    public String getName() {
        return "Recipes matcher";
    }

    @Override
    public boolean isIdleNow() {
        boolean idle = pendingMatches.getAsInt() == 0;
        ResourceCallback waiting = callback;
        if (idle && waiting != null) {
            waiting.onTransitionToIdle();
        }
        return idle;
    }

    @Override
    public void registerIdleTransitionCallback(@NonNull ResourceCallback callback) {
        this.callback = callback;
    }
}
