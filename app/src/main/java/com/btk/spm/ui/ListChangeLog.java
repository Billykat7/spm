package com.btk.spm.ui;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.BuildConfig;

/**
 * Writes one logcat line for each change a list adapter announces, in debug builds only.
 *
 * <p>This is the evidence that {@code DiffUtil} redraws only what changed: run
 * {@code adb logcat -s ListChange}, edit one item's quantity (in the app or the Database Inspector)
 * and the log shows {@code changed 1 row at 2} rather than the whole list. Insertions, removals and
 * moves are logged the same way. Only positions and counts are written, never an item, so no pantry
 * data reaches the log.
 */
public final class ListChangeLog extends RecyclerView.AdapterDataObserver {

    /** The logcat tag every line uses: {@code adb logcat -s ListChange}. */
    public static final String TAG = "ListChange";

    private final String listName;

    private ListChangeLog(@NonNull String listName) {
        this.listName = listName;
    }

    /**
     * Logs every change {@code adapter} announces from now on, in a debug build; does nothing in a
     * release build.
     *
     * @param adapter  the adapter to watch
     * @param listName how the lines name the list, such as {@code "Pantry"}
     */
    public static void attach(@NonNull RecyclerView.Adapter<?> adapter, @NonNull String listName) {
        if (BuildConfig.DEBUG) {
            adapter.registerAdapterDataObserver(new ListChangeLog(listName));
        }
    }

    @Override
    public void onChanged() {
        log("changed everything");
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount) {
        log("changed", itemCount, "at " + positionStart);
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
        onItemRangeChanged(positionStart, itemCount);
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
        log("inserted", itemCount, "at " + positionStart);
    }

    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
        log("removed", itemCount, "at " + positionStart);
    }

    @Override
    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
        log("moved", itemCount, "from " + fromPosition + " to " + toPosition);
    }

    private void log(String change) {
        Log.d(TAG, listName + ": " + change);
    }

    /**
     * Logs a ranged change unless it covers no rows: {@code ListAdapter} announces its first list,
     * even an empty one, as an insertion, and "inserted 0 rows" would only be noise in the demo.
     */
    private void log(String change, int count, String where) {
        if (count > 0) {
            log(change + " " + rows(count) + " " + where);
        }
    }

    private static String rows(int count) {
        return count + (count == 1 ? " row" : " rows");
    }
}
