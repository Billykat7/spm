package com.btk.spm.ui.pantry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.PantryItemDao;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;
import com.btk.spm.util.IntentKeys;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * {@link AddEditIngredientActivity} on a device, against the app's own database (Issue 15): opened
 * with a row's id it is the edit screen, prefilled, and Save updates that row and adds none; opened
 * with no id it is the add screen; an id with no row closes it; an invalid edit writes nothing.
 *
 * <p>No Espresso: {@link ActivityScenario} starts the screen, the checks read its views through
 * {@code onActivity}, and Save is the toolbar's own action, so the result and the finish are the real
 * ones. Every wait is on a {@code LiveData} or the scenario, never a sleep. Each test deletes the row
 * it inserted. The tap-by-tap flow on screen is Issue 32.
 */
@RunWith(AndroidJUnit4.class)
public class AddEditIngredientActivityTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final long CREATED = 1_791_000_000_000L;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final PantryItemDao dao = ApplicationProvider.<SpmApplication>getApplicationContext()
            .getDatabase().pantryItemDao();
    private final List<Long> inserted = new ArrayList<>();

    @After
    public void removeTheRowsThisTestAdded() {
        for (long id : inserted) {
            dao.deleteById(id);
        }
    }

    @Test
    public void editMode_prefillsTheRow_andSaveUpdatesTheSameRow() throws InterruptedException {
        long id = insert(new PantryItem("tomatoes", 4, Unit.PCS, null, CREATED));
        int rowsBefore = dao.countSync();

        try (ActivityScenario<AddEditIngredientActivity> scenario =
                     ActivityScenario.launchActivityForResult(AddEditIngredientActivity.intentForEdit(context, id))) {
            awaitReady(scenario);
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.title_edit_ingredient), String.valueOf(activity.getTitle()));
                assertEquals("tomatoes", text(activity, R.id.name_input));
                assertEquals("4 shows as 4, not 4.0", "4", text(activity, R.id.quantity_input));
                assertEquals(activity.getString(Unit.PCS.symbolRes()), text(activity, R.id.unit_input));
                assertEquals("no date", "", text(activity, R.id.expiry_input));

                this.<EditText>view(activity, R.id.quantity_input).setText("6");
                tapSave(activity);
            });

            assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
        }

        PantryItem edited = awaitValue(dao.observeById(id), item -> item != null && item.getQuantity() == 6);
        assertEquals("no row added", rowsBefore, dao.countSync());
        assertEquals(id, edited.getId());
        assertEquals("tomatoes", edited.getName());
        assertEquals(Unit.PCS, edited.getUnit());
        assertEquals("the creation time is kept", CREATED, edited.getCreatedAt());
    }

    @Test
    public void anInvalidEdit_showsTheError_andWritesNothing() throws InterruptedException {
        PantryItem tomatoes = new PantryItem("tomatoes", 4, Unit.PCS, null, CREATED);
        long id = insert(tomatoes);

        try (ActivityScenario<AddEditIngredientActivity> scenario =
                     ActivityScenario.launch(AddEditIngredientActivity.intentForEdit(context, id))) {
            awaitReady(scenario);
            scenario.onActivity(activity -> {
                this.<EditText>view(activity, R.id.name_input).setText("");
                tapSave(activity);

                assertFalse("the screen stays open", activity.isFinishing());
                TextInputLayout name = view(activity, R.id.name_layout);
                assertEquals(activity.getString(R.string.error_name_required), String.valueOf(name.getError()));
            });
        }

        assertEquals("the row is untouched", new PantryItem(id, "tomatoes", 4, Unit.PCS, null, CREATED),
                dao.getByIdSync(id));
    }

    @Test
    public void anUnknownId_closesTheScreen_withNoResult() {
        Intent bogus = AddEditIngredientActivity.intentForEdit(context, 999_999_999L);

        try (ActivityScenario<AddEditIngredientActivity> scenario = ActivityScenario.launchActivityForResult(bogus)) {
            // getResult waits for the screen to finish; it shows "Ingredient not found" as it goes
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
        }
    }

    @Test
    public void noIdExtra_isTheAddScreen_withAnEmptyForm() {
        Intent add = AddEditIngredientActivity.intentForAdd(context);
        assertFalse(add.hasExtra(IntentKeys.EXTRA_PANTRY_ITEM_ID));

        try (ActivityScenario<AddEditIngredientActivity> scenario = ActivityScenario.launch(add)) {
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.title_add_ingredient), String.valueOf(activity.getTitle()));
                assertEquals("", text(activity, R.id.name_input));
                assertEquals("", text(activity, R.id.quantity_input));
            });
        }
    }

    @Test
    public void intentForEdit_carriesTheIdInTheOneExtraKey() {
        Intent edit = AddEditIngredientActivity.intentForEdit(context, 42L);

        assertEquals(42L, edit.getLongExtra(IntentKeys.EXTRA_PANTRY_ITEM_ID, 0L));
        assertEquals(AddEditIngredientActivity.class.getName(), edit.getComponent().getClassName());
    }

    private long insert(PantryItem item) {
        long id = dao.insert(item);
        inserted.add(id);
        return id;
    }

    /** Waits until the screen's ViewModel says the row has loaded and the form is shown. */
    private void awaitReady(ActivityScenario<AddEditIngredientActivity> scenario) throws InterruptedException {
        AtomicReference<AddEditIngredientViewModel> viewModel = new AtomicReference<>();
        scenario.onActivity(activity ->
                viewModel.set(new ViewModelProvider(activity).get(AddEditIngredientViewModel.class)));
        awaitValue(viewModel.get().isReady(), Boolean.TRUE::equals);
        // The prefill is delivered in the same main-thread pass; let it land before reading the fields
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Taps the toolbar's Save the way the user does: through the Activity's options menu. */
    private static void tapSave(AddEditIngredientActivity activity) {
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        MenuItem save = toolbar.getMenu().findItem(R.id.action_save);
        assertNotNull("no Save action", save);
        assertTrue("Save is off", save.isEnabled());
        activity.onOptionsItemSelected(save);
    }

    private static String text(Activity activity, int id) {
        return String.valueOf(((TextView) activity.findViewById(id)).getText());
    }

    @SuppressWarnings("unchecked")
    private <V> V view(Activity activity, int id) {
        return (V) activity.findViewById(id);
    }

    /**
     * Returns the first value of {@code liveData} that passes {@code accept}, observing it on the main
     * thread and waiting on a latch, not a sleep.
     */
    private static <T> T awaitValue(@NonNull LiveData<T> liveData, @NonNull Predicate<T> accept)
            throws InterruptedException {
        AtomicReference<T> value = new AtomicReference<>();
        CountDownLatch accepted = new CountDownLatch(1);
        Observer<T> observer = newValue -> {
            if (accept.test(newValue)) {
                value.set(newValue);
                accepted.countDown();
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> liveData.observeForever(observer));
        try {
            assertTrue("no accepted value within " + TIMEOUT_SECONDS + " s",
                    accepted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> liveData.removeObserver(observer));
        }
        return value.get();
    }
}
