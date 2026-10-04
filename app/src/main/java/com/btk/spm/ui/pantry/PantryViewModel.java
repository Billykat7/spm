package com.btk.spm.ui.pantry;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.btk.spm.SpmApplication;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.repo.PantryRepository;

import java.util.List;

/**
 * The state behind the Pantry tab: the pantry, live from Room, in the chosen {@link SortOrder}.
 *
 * <p>This is where non-negotiable 6 meets the screen. {@link #getItems()} is derived from the
 * repository's {@code LiveData} query, so when any screen (or the Database Inspector) writes to
 * {@code pantry_items}, Room re-runs the query, the list re-emits and {@code PantryFragment} hands it
 * to the adapter; nothing here or in the Fragment keeps its own copy or asks for a refresh. The
 * Fragment never sees the repository, let alone the DAO behind it.
 *
 * <p>The ViewModel outlives a rotation, so the observed list (and the {@code RecyclerView}'s scroll
 * position, which the adapter keeps because the same items come back) survives it without a query.
 */
public class PantryViewModel extends AndroidViewModel {

    /** The order the list is shown in. Fixed at {@link SortOrder#NAME} until Issue 17 adds the menu. */
    private final MutableLiveData<SortOrder> sortOrder = new MutableLiveData<>(SortOrder.NAME);

    private final LiveData<List<PantryItem>> items;

    /**
     * Creates the ViewModel over the app's one {@link PantryRepository}. Called by the default
     * {@code ViewModelProvider} factory, which passes the application.
     *
     * @param application the running app, whose {@link SpmApplication} holds the repository
     */
    public PantryViewModel(@NonNull Application application) {
        this(application, SpmApplication.from(application).getPantryRepository());
    }

    /**
     * Creates the ViewModel over {@code repository}; a test passes one built on an in-memory database.
     *
     * @param application the running app
     * @param repository  where the pantry is read from
     */
    @VisibleForTesting
    PantryViewModel(@NonNull Application application, @NonNull PantryRepository repository) {
        super(application);
        LiveData<List<PantryItem>> pantry = repository.observeAll();
        // switchMap: a new order swaps in a new mapping of the same Room query, so changing the order
        // never starts a second query. map: each emission of the table is sorted by the current order.
        // Sorting runs on the main thread, which is fine for a household pantry of tens of rows.
        items = Transformations.switchMap(sortOrder,
                order -> Transformations.map(pantry, order::sort));
    }

    /**
     * Returns the pantry in the current order, re-emitted whenever {@code pantry_items} changes or the
     * order does. Observe it with the view's lifecycle owner and pass each list to
     * {@code ListAdapter.submitList}; keep no other reference to it.
     *
     * @return the observed, sorted pantry; an empty list when the pantry is empty
     */
    @NonNull
    public LiveData<List<PantryItem>> getItems() {
        return items;
    }

    /**
     * Shows the list in {@code order}. The sort menu of Issue 17 calls this; setting the order the
     * list already has does nothing.
     *
     * @param order the order to show
     */
    @MainThread
    public void setSortOrder(@NonNull SortOrder order) {
        if (order != sortOrder.getValue()) {
            sortOrder.setValue(order);
        }
    }
}
