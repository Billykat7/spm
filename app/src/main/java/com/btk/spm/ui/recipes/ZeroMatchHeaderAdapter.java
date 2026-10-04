package com.btk.spm.ui.recipes;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.databinding.ItemZeroMatchHeaderBinding;

/**
 * The first row of the Recipes tab when nothing can be made but something is one ingredient away
 * (Issue 27): the brief's "No recipes match your pantry yet, add more ingredients" (§2.2, Issue 24),
 * above the "Almost there" section. With nothing almost there either, the tab shows the full-screen
 * empty state instead, and this adapter holds no item.
 */
public class ZeroMatchHeaderAdapter extends RecyclerView.Adapter<ZeroMatchHeaderAdapter.ViewHolder> {

    private boolean shown;

    /** Creates the header hidden. */
    public ZeroMatchHeaderAdapter() {
        // Like the other adapters: hold a saved scroll position until the screen has content
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    /**
     * Shows or hides the sentence; does nothing when it is already in that state.
     *
     * @param show whether the sentence is the first row
     */
    public void setShown(boolean show) {
        if (show == shown) {
            return;
        }
        shown = show;
        if (show) {
            notifyItemInserted(0);
        } else {
            notifyItemRemoved(0);
        }
    }

    @Override
    public int getItemCount() {
        return shown ? 1 : 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemZeroMatchHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // The text is the layout's own: the one sentence this row ever says
    }

    /** Holds the sentence's one view. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(@NonNull ItemZeroMatchHeaderBinding binding) {
            super(binding.getRoot());
        }
    }
}
