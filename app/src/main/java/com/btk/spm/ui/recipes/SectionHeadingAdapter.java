package com.btk.spm.ui.recipes;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.databinding.ItemSectionHeadingBinding;

/**
 * A heading inside a {@code ConcatAdapter}, such as "Method" between a recipe's ingredients and its
 * steps. It holds no item until {@link #setShown(boolean)} shows it, so the heading never appears
 * above a list that has not loaded.
 */
public class SectionHeadingAdapter extends RecyclerView.Adapter<SectionHeadingAdapter.ViewHolder> {

    @StringRes
    private final int textRes;
    private boolean shown;

    /**
     * Creates a hidden heading.
     *
     * @param textRes the heading's text
     */
    public SectionHeadingAdapter(@StringRes int textRes) {
        this.textRes = textRes;
        // Like the other adapters: hold a saved scroll position until the screen has content
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    /**
     * Shows or hides the heading; does nothing when it is already in that state.
     *
     * @param show whether the heading is in the list
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
        return new ViewHolder(ItemSectionHeadingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.heading.setText(textRes);
    }

    /** Holds the heading's one view. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemSectionHeadingBinding binding;

        ViewHolder(@NonNull ItemSectionHeadingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // TalkBack's heading navigation stops here; the XML attribute only works from API 28
            ViewCompat.setAccessibilityHeading(binding.heading, true);
        }
    }
}
