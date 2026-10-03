package com.btk.spm.ui.pantry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.btk.spm.R;
import com.btk.spm.SpmApplication;
import com.btk.spm.data.db.PantryItemDao;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.domain.Unit;
import com.btk.spm.ui.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The Pantry tab on a device, against the app's own database (Issue 13): a row written while the
 * list is on screen appears with no refresh, changing one row's quantity rebinds that row and no
 * other, and an empty table swaps the list for the empty state.
 *
 * <p>The writes go straight to the DAO from the test thread, as the Database Inspector's would: no
 * screen, no ViewModel, nothing that could tell the list to reload. Only Room's {@code LiveData} can
 * carry them to the adapter. The adapter's change notifications are recorded with an
 * {@link RecyclerView.AdapterDataObserver}, the same hook {@code ListChangeLog} logs in debug builds,
 * and every wait is for the next notification, never a fixed sleep. Each test deletes the rows it
 * inserted and leaves any others alone.
 */
@RunWith(AndroidJUnit4.class)
public class PantryListLiveUpdateTest {

    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

    private final PantryItemDao dao = ApplicationProvider.<SpmApplication>getApplicationContext()
            .getDatabase().pantryItemDao();
    private final List<Long> inserted = new ArrayList<>();

    private ActivityScenario<MainActivity> scenario;
    private RecyclerView list;
    private View emptyState;
    private AdapterEvents events;
    private int baseline;

    @Before
    public void openThePantryTab() {
        scenario = ActivityScenario.launch(MainActivity.class);
        events = new AdapterEvents();
        scenario.onActivity(activity -> {
            list = activity.findViewById(R.id.pantry_list);
            emptyState = activity.findViewById(R.id.empty_state);
            list.getAdapter().registerAdapterDataObserver(events);
        });
        baseline = dao.getAllSync().size();
        awaitItemCount(baseline);
        events.drain();
    }

    @After
    public void removeTheRowsThisTestAdded() {
        for (long id : inserted) {
            dao.deleteById(id);
        }
        scenario.close();
    }

    @Test
    public void rowsWrittenWhileTheListIsOpen_appearWithNoRefresh() {
        insert("Tomatoes", 4, Unit.PCS);
        insert("Plain flour", 1.5, Unit.KG);
        insert("Olive oil", 500, Unit.ML);

        awaitItemCount(baseline + 3);
        assertEquals(View.VISIBLE, onMain(list::getVisibility).intValue());
        assertEquals(View.GONE, onMain(emptyState::getVisibility).intValue());
    }

    @Test
    public void changingOneRowsQuantity_rebindsOnlyThatRow() {
        insert("Tomatoes", 4, Unit.PCS);
        long flour = insert("Plain flour", 1.5, Unit.KG);
        insert("Olive oil", 500, Unit.ML);
        awaitItemCount(baseline + 3);
        events.drain();

        PantryItem before = dao.getByIdSync(flour);
        int seen = events.count();
        dao.update(new PantryItem(flour, before.getName(), 2, before.getUnit(), before.getExpiryDate(),
                before.getCreatedAt()));
        assertTrue("the adapter announced no change",
                events.awaitAfter(seen, System.currentTimeMillis() + TIMEOUT_MS));

        // DiffUtil dispatches one diff in a single main-thread pass, so every notification it makes
        // is recorded once the first one has arrived and the main thread is idle again
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        int row = onMain(() -> positionOf(flour));
        assertEquals(List.of("changed 1 at " + row), events.drain());
    }

    @Test
    public void anEmptyPantry_showsTheEmptyState_andOneRowSwapsItBack() {
        assumeTrue("needs an empty pantry_items table", baseline == 0);
        assertEquals(View.VISIBLE, onMain(emptyState::getVisibility).intValue());
        assertEquals(View.GONE, onMain(list::getVisibility).intValue());

        long eggs = insert("Eggs", 6, Unit.PCS);
        awaitItemCount(1);
        assertEquals(View.VISIBLE, onMain(list::getVisibility).intValue());
        assertEquals(View.GONE, onMain(emptyState::getVisibility).intValue());

        dao.deleteById(eggs);
        awaitItemCount(0);
        assertEquals(View.VISIBLE, onMain(emptyState::getVisibility).intValue());
        assertEquals(View.GONE, onMain(list::getVisibility).intValue());
    }

    private long insert(String name, double quantity, Unit unit) {
        long id = dao.insert(new PantryItem(name, quantity, unit, null, System.currentTimeMillis()));
        inserted.add(id);
        return id;
    }

    /** Waits, one adapter notification at a time, until the list shows {@code expected} rows. */
    private void awaitItemCount(int expected) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (true) {
            // Read the event count first, so a notification that lands during the check is not missed
            int seen = events.count();
            if (onMain(() -> list.getAdapter().getItemCount()) == expected) {
                return;
            }
            assertTrue("the list never showed " + expected + " rows", events.awaitAfter(seen, deadline));
        }
    }

    private int positionOf(long id) {
        PantryAdapter adapter = (PantryAdapter) list.getAdapter();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.getCurrentList().get(i).getId() == id) {
                return i;
            }
        }
        throw new AssertionError("no row for id " + id);
    }

    private static <T> T onMain(Supplier<T> read) {
        AtomicReference<T> value = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> value.set(read.get()));
        return value.get();
    }

    /** Records every change the adapter announces, as {@code ListChangeLog} words it, and wakes waiters. */
    private static final class AdapterEvents extends RecyclerView.AdapterDataObserver {

        private final List<String> events = new ArrayList<>();
        private int count;

        @Override
        public void onChanged() {
            record("changed everything");
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            record("changed " + itemCount + " at " + positionStart);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            onItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            record("inserted " + itemCount + " at " + positionStart);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            record("removed " + itemCount + " at " + positionStart);
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            record("moved " + itemCount + " from " + fromPosition + " to " + toPosition);
        }

        private synchronized void record(String event) {
            events.add(event);
            count++;
            notifyAll();
        }

        /** Returns the events recorded since the last call, and forgets them. */
        @NonNull
        synchronized List<String> drain() {
            List<String> drained = new ArrayList<>(events);
            events.clear();
            return drained;
        }

        /** Returns how many events have been recorded so far, drained or not. */
        synchronized int count() {
            return count;
        }

        /** Waits until more than {@code seen} events have been recorded, or {@code deadline} passes. */
        synchronized boolean awaitAfter(int seen, long deadline) {
            try {
                while (count == seen) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) {
                        return false;
                    }
                    wait(left);
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
