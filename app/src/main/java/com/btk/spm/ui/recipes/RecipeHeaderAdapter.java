package com.btk.spm.ui.recipes;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.databinding.ItemRecipeHeaderBinding;
import com.btk.spm.domain.matching.MatchResult;
import com.google.android.material.color.MaterialColors;

/**
 * The first item of the recipe detail list: the recipe's name, how many it serves and whether it can
 * be made now ("You can make this", or "Missing 2 ingredients").
 *
 * <p>A {@link ListAdapter} of at most one {@link DetailUiState.Loaded}, so it sits in the screen's
 * {@code ConcatAdapter} like any other list and is empty until the recipe has loaded. The status line
 * reads the matcher's result: {@link MatchResult#canMake()} and the number of shortfalls, nothing
 * worked out here.
 */
public class RecipeHeaderAdapter extends ListAdapter<DetailUiState.Loaded, RecipeHeaderAdapter.ViewHolder> {

    /** Creates an empty header; it appears when a loaded state is submitted. */
    public RecipeHeaderAdapter() {
        super(new Diff());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemRecipeHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /** Holds the header's views, bound through {@link ItemRecipeHeaderBinding}. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemRecipeHeaderBinding binding;

        ViewHolder(@NonNull ItemRecipeHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            // The recipe's name is the screen's heading; the XML attribute only works from API 28
            ViewCompat.setAccessibilityHeading(binding.name, true);
        }

        void bind(@NonNull DetailUiState.Loaded loaded) {
            Resources res = binding.getRoot().getResources();
            Recipe recipe = loaded.recipe().getRecipe();
            binding.name.setText(recipe.getName());
            Integer servings = recipe.getServings();
            binding.servings.setVisibility(servings == null ? View.GONE : View.VISIBLE);
            if (servings != null) {
                binding.servings.setText(res.getString(R.string.recipe_serves, servings));
            }
            MatchResult result = loaded.result();
            int missing = result.shortfalls().size();
            binding.status.setText(result.canMake()
                    ? res.getString(R.string.recipe_can_make)
                    : res.getQuantityString(R.plurals.recipe_missing_ingredients, missing, missing));
            binding.status.setTextColor(MaterialColors.getColor(binding.status,
                    result.canMake() ? androidx.appcompat.R.attr.colorPrimary : androidx.appcompat.R.attr.colorError));
        }
    }

    /** One header, so the same item always; rebound when the recipe or its verdict changed. */
    static final class Diff extends DiffUtil.ItemCallback<DetailUiState.Loaded> {

        @Override
        public boolean areItemsTheSame(@NonNull DetailUiState.Loaded oldItem, @NonNull DetailUiState.Loaded newItem) {
            return oldItem.recipe().getRecipe().getId() == newItem.recipe().getRecipe().getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull DetailUiState.Loaded oldItem, @NonNull DetailUiState.Loaded newItem) {
            return oldItem.equals(newItem);
        }
    }
}
