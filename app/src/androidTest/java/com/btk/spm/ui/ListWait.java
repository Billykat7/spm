package com.btk.spm.ui;

import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.espresso.util.TreeIterables;

import org.hamcrest.Matcher;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Waits, on the main thread, until a view matching a condition is on screen or until none is.
 *
 * <p>Espresso's idling resources cover the app's threads, but not {@code ListAdapter}'s: it works out
 * each new list on a background thread of its own and posts the result to the main thread afterwards,
 * so for a moment the main thread is idle while the row is not drawn yet. Rather than sleep, this
 * action lets Espresso run the main thread's queue a few milliseconds at a time
 * ({@link UiController#loopMainThreadForAtLeast}) and looks again, failing after a timeout.
 */
public final class ListWait {

    /** Long enough for a diff on an emulator under load; a passing test returns far sooner. */
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

    /** How long the main thread runs between two looks. */
    private static final long STEP_MS = 20;

    private ListWait() {
        // Static factories only; never instantiated
    }

    /**
     * Waits until a displayed view matches {@code matcher}.
     *
     * @param matcher what the view must match, such as a row's name
     * @return an action to perform on {@code onView(isRoot())}
     */
    @NonNull
    public static ViewAction until(@NonNull Matcher<View> matcher) {
        return waitFor(matcher, true);
    }

    /**
     * Waits until no displayed view matches {@code matcher}, such as a deleted row.
     *
     * @param matcher what no view may match any more
     * @return an action to perform on {@code onView(isRoot())}
     */
    @NonNull
    public static ViewAction untilGone(@NonNull Matcher<View> matcher) {
        return waitFor(matcher, false);
    }

    private static ViewAction waitFor(Matcher<View> matcher, boolean present) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @Override
            public String getDescription() {
                return "wait until " + (present ? "" : "no ") + "view " + matcher + " is on screen";
            }

            @Override
            public void perform(UiController uiController, View root) {
                long deadline = System.currentTimeMillis() + TIMEOUT_MS;
                while (System.currentTimeMillis() < deadline) {
                    if (anyMatches(root) == present) {
                        return;
                    }
                    uiController.loopMainThreadForAtLeast(STEP_MS);
                }
                throw new PerformException.Builder()
                        .withActionDescription(getDescription())
                        .withViewDescription(HumanReadables.describe(root))
                        .withCause(new TimeoutException("after " + TIMEOUT_MS + " ms"))
                        .build();
            }

            private boolean anyMatches(View root) {
                for (View view : TreeIterables.breadthFirstViewTraversal(root)) {
                    if (matcher.matches(view)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
