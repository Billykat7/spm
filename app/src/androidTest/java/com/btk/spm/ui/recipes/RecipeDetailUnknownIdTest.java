package com.btk.spm.ui.recipes;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The recipe detail screen opened with an id that leads nowhere (Issue 31): an id no recipe has, no
 * id at all, or one that cannot be a row id. Each shows the shared error state, "Recipe not found",
 * in place of the list, and the toolbar's up arrow still closes the screen. Nothing throws.
 */
@RunWith(AndroidJUnit4.class)
public class RecipeDetailUnknownIdTest {

    private static final long TIMEOUT_S = 5;

    /** No recipe has this id: the seed inserts twenty. */
    private static final long UNKNOWN_ID = 999_999L;

    private final SpmApplication app = ApplicationProvider.getApplicationContext();

    @Test
    public void anIdNoRecipeHas_showsRecipeNotFound_andUpCloses() throws InterruptedException {
        assertNotFound_thenUpCloses(RecipeDetailActivity.intentFor(app, UNKNOWN_ID));
    }

    @Test
    public void noIdAtAll_showsRecipeNotFound_andUpCloses() throws InterruptedException {
        assertNotFound_thenUpCloses(new Intent(app, RecipeDetailActivity.class));
    }

    @Test
    public void aNegativeId_showsRecipeNotFound_andUpCloses() throws InterruptedException {
        assertNotFound_thenUpCloses(RecipeDetailActivity.intentFor(app, -1L));
    }

    private void assertNotFound_thenUpCloses(Intent intent) throws InterruptedException {
        CountDownLatch destroyed = new CountDownLatch(1);
        ActivityLifecycleCallback callback = (activity, stage) -> {
            if (activity instanceof RecipeDetailActivity && stage == Stage.DESTROYED) {
                destroyed.countDown();
            }
        };
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback);
        try (ActivityScenario<RecipeDetailActivity> ignored = ActivityScenario.launch(intent)) {
            onView(withId(R.id.state_headline)).check(matches(withText(R.string.recipe_not_found)));
            onView(withId(R.id.state_body)).check(matches(withText(R.string.recipe_not_found_body)));
            onView(withId(R.id.detail_list)).check(matches(not(isDisplayed())));

            onView(withContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description)).perform(click());

            assertTrue("Up did not close the screen", destroyed.await(TIMEOUT_S, TimeUnit.SECONDS));
        } finally {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback);
        }
    }
}
