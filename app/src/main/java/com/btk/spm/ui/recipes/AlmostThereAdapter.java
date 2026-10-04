package com.btk.spm.ui.recipes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.databinding.ItemRecipeAlmostBinding;
import com.btk.spm.util.QuantityFormatter;

import java.util.List;

/**
 * The "Almost there" section of the Recipes tab (brief §2.3 bonus, Issue 27): one outlined
 * {@code item_recipe_almost} card per recipe that is exactly one ingredient short, saying which one,
 * "Missing: 2 pcs egg" or "Short: need 250 g flour, have 200 g".
 *
 * <p>It is a different adapter from {@link RecipeAdapter}, with a different row, so the two lists
 * cannot be confused on screen or in the code. {@link #submitList} refuses any recipe that is not one
 * ingredient short ({@link RecipeSections#requireAlmostThere}), and the line comes from the matcher's
 * own {@code missingOne()} through {@link RecipeSections#missingLine}: this class compares nothing. A
 * tap opens the recipe in full (Issue 25), where the one missing line has the only cross.
 */
public class AlmostThereAdapter extends ListAdapter<MatchedRecipe, AlmostThereAdapter.ViewHolder> {

    private final RecipeAdapter.OnRecipeClickListener listener;

    /**
     * Creates an empty section; rows appear when a list is submitted.
     *
     * @param listener receives the row taps, the same listener as the suggestions'
     */
    public AlmostThereAdapter(@NonNull RecipeAdapter.OnRecipeClickListener listener) {
        super(new ItemDiff());
        this.listener = listener;
    }

    /**
     * Shows {@code list} under the "Almost there" heading.
     *
     * @param list recipes one ingredient short, or {@code null} to clear the section
     * @throws IllegalArgumentException if any recipe in it is not exactly one ingredient short
     */
    @Override
    public void submitList(@Nullable List<MatchedRecipe> list) {
        super.submitList(RecipeSections.requireAlmostThere(list));
    }

    /**
     * Shows {@code list} under the "Almost there" heading, then runs {@code commitCallback}.
     *
     * @throws IllegalArgumentException if any recipe in {@code list} is not exactly one ingredient short
     */
    @Override
    public void submitList(@Nullable List<MatchedRecipe> list, @Nullable Runnable commitCallback) {
        super.submitList(RecipeSections.requireAlmostThere(list), commitCallback);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecipeAlmostBinding binding =
                ItemRecipeAlmostBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewHolder holder = new ViewHolder(binding);
        // Set once per card and the recipe looked up when tapped, as in RecipeAdapter
        binding.getRoot().setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onRecipeClick(getItem(position).recipeId());
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /** Holds one card's views, bound through {@link ItemRecipeAlmostBinding}. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemRecipeAlmostBinding binding;

        ViewHolder(@NonNull ItemRecipeAlmostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /** Shows the recipe's name and its one missing or short ingredient. */
        void bind(@NonNull MatchedRecipe row) {
            Context context = binding.getRoot().getContext();
            binding.name.setText(row.recipe().getRecipe().getName());
            RecipeSections.MissingLine line = RecipeSections.missingLine(row);
            String need = QuantityFormatter.format(context, line.need());
            binding.missing.setText(line.have() == null
                    ? context.getString(R.string.almost_missing, need, line.name())
                    : context.getString(R.string.almost_short, need, line.name(),
                            QuantityFormatter.format(context, line.have())));
        }
    }

    /**
     * Two cards are the same recipe when the ids match; a card is rebound when its name or its
     * shortfall changed, so "have 1 pcs" becomes "have 1.5 pcs" when the pantry does.
     */
    static final class ItemDiff extends DiffUtil.ItemCallback<MatchedRecipe> {

        @Override
        public boolean areItemsTheSame(@NonNull MatchedRecipe oldItem, @NonNull MatchedRecipe newItem) {
            return oldItem.recipeId() == newItem.recipeId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull MatchedRecipe oldItem, @NonNull MatchedRecipe newItem) {
            return oldItem.recipe().getRecipe().getName().equals(newItem.recipe().getRecipe().getName())
                    && oldItem.result().shortfalls().equals(newItem.result().shortfalls());
        }
    }
}
