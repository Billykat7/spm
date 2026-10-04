package com.btk.spm.ui.recipes;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.databinding.ItemRecipeStepBinding;

/**
 * The method of the recipe detail screen: one {@code item_recipe_step} row per step, numbered
 * {@code 1.}, {@code 2.}, ... in order.
 *
 * <p>The steps are stored unnumbered (Issue 8), so a row's number is its position plus one. Recipes are
 * read-only seed data (decision 7), so the list is submitted once and never changes while the screen
 * is open.
 */
public class RecipeStepAdapter extends ListAdapter<String, RecipeStepAdapter.ViewHolder> {

    /** Creates an empty adapter; the steps appear when the recipe has loaded. */
    public RecipeStepAdapter() {
        super(new StepDiff());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemRecipeStepBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.number.setText(holder.binding.getRoot().getContext()
                .getString(R.string.recipe_step_number, position + 1));
        holder.binding.text.setText(getItem(position));
    }

    /** Holds one step's views, bound through {@link ItemRecipeStepBinding}. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemRecipeStepBinding binding;

        ViewHolder(@NonNull ItemRecipeStepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    /** A step is the same row when its text is the same; the method never changes on screen. */
    static final class StepDiff extends DiffUtil.ItemCallback<String> {

        @Override
        public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }
    }
}
