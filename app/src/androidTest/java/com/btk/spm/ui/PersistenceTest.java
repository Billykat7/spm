package com.btk.spm.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.domain.Unit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * An item added through the form is still listed after the Activity is re-created and after a brand
 * new {@code ActivityScenario} (brief §3.2, Issue 32). Both run in this test process against the same
 * in-memory database ({@link FreshAppRule}), so what this proves is that the screens read the pantry
 * back from Room each time instead of keeping a list of their own. Surviving process death, the app
 * closed and reopened on its database file, is {@code PersistenceAcrossRestartTest}'s (Issue 12).
 */
@RunWith(AndroidJUnit4.class)
public class PersistenceTest {

    @Rule
    public final FreshAppRule freshApp = new FreshAppRule();

    @Test
    public void anAddedItem_isListedAfterRecreate_andInANewScenario() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            PantryFlows.addThroughTheForm("Basil", "2", Unit.PCS);

            scenario.recreate();
            onView(isRoot()).perform(ListWait.until(PantryFlows.row("Basil", "2", Unit.PCS)));
        }

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(isRoot()).perform(ListWait.until(PantryFlows.row("Basil", "2", Unit.PCS)));
        }
    }
}
