package com.btk.spm.ui.recipes;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.databinding.ItemRecipeBinding;
import com.btk.spm.domain.matching.MatchResult;

import java.util.List;
import java.util.Objects;

/**
 * Shows the suggested recipes in a {@code RecyclerView}: one {@code item_recipe} row per
 * {@link MatchedRecipe}, with the recipe's name, how many it serves and "You have all N ingredients".
 *
 * <p>It is a {@link ListAdapter}, so it keeps no list of its own. Each time the matcher runs,
 * {@code SuggestedRecipesFragment} hands it the new list through {@link #submitList}, and
 * {@link ItemDiff} works out on a background thread which rows came, went or changed; only those are
 * bound again. {@link #onCreateViewHolder} inflates a row once and {@link #onBindViewHolder} fills it
 * for whichever recipe scrolls into it.
 *
 * <p>It only renders. Every row it is given is a recipe the matcher said can be made, and the count
 * it shows is the matcher's {@link MatchResult#needCount()}: the adapter never filters, sorts or
 * decides. {@link #submitList} checks that, through {@link RecipeSections#requireCanMake}: a list
 * holding an almost-there recipe throws instead of being drawn as a suggestion (non-negotiable 3). A
 * tap goes to the {@link OnRecipeClickListener} the Fragment implements.
 */
public class RecipeAdapter extends ListAdapter<MatchedRecipe, RecipeAdapter.ViewHolder> {

    /** What a row asks its screen to do. The Fragment implements it; the adapter never acts itself. */
    public interface OnRecipeClickListener {

        /**
         * The row was tapped. The detail screen (Issue 25) opens the recipe by this id.
         *
         * @param recipeId the {@code recipes.id} of the recipe the row shows
         */
        void onRecipeClick(long recipeId);
    }

    private final OnRecipeClickListener listener;

    /**
     * Creates an empty adapter; rows appear when the first list is submitted.
     *
     * @param listener receives the row taps
     */
    public RecipeAdapter(@NonNull OnRecipeClickListener listener) {
        super(new ItemDiff());
        this.listener = listener;
    }

    /**
     * Shows {@code list} as the suggestions.
     *
     * @param list recipes that can be made, or {@code null} to clear the list
     * @throws IllegalArgumentException if any recipe in it is not {@code CAN_MAKE}
     */
    @Override
    public void submitList(@Nullable List<MatchedRecipe> list) {
        super.submitList(RecipeSections.requireCanMake(list));
    }

    /**
     * Shows {@code list} as the suggestions, then runs {@code commitCallback}.
     *
     * @throws IllegalArgumentException if any recipe in {@code list} is not {@code CAN_MAKE}
     */
    @Override
    public void submitList(@Nullable List<MatchedRecipe> list, @Nullable Runnable commitCallback) {
        super.submitList(RecipeSections.requireCanMake(list), commitCallback);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecipeBinding binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewHolder holder = new ViewHolder(binding);
        // Set once per row view, and the recipe looked up when tapped, so a row that was rebound or
        // moved since never opens the recipe it used to show
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

    /** Holds one row's views, bound through {@link ItemRecipeBinding}. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemRecipeBinding binding;

        ViewHolder(@NonNull ItemRecipeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /** Shows the recipe's name, its servings when the seed gives them, and the ingredient count. */
        void bind(@NonNull MatchedRecipe row) {
            Resources res = binding.getRoot().getResources();
            Recipe recipe = row.recipe().getRecipe();
            binding.name.setText(recipe.getName());
            Integer servings = recipe.getServings();
            binding.servings.setVisibility(servings == null ? View.GONE : View.VISIBLE);
            if (servings != null) {
                binding.servings.setText(res.getString(R.string.recipe_serves, servings));
            }
            int needCount = row.result().needCount();
            binding.haveAll.setText(res.getQuantityString(R.plurals.recipe_have_all_ingredients, needCount, needCount));
        }
    }

    /**
     * How {@link ListAdapter} compares two lists of suggestions. Two rows are the same recipe when the
     * recipe ids match. A row needs binding again when anything it shows, or the verdict behind it,
     * changed: the name, the servings, the status or the counts.
     */
    static final class ItemDiff extends DiffUtil.ItemCallback<MatchedRecipe> {

        @Override
        public boolean areItemsTheSame(@NonNull MatchedRecipe oldItem, @NonNull MatchedRecipe newItem) {
            return oldItem.recipeId() == newItem.recipeId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull MatchedRecipe oldItem, @NonNull MatchedRecipe newItem) {
            Recipe oldRecipe = oldItem.recipe().getRecipe();
            Recipe newRecipe = newItem.recipe().getRecipe();
            MatchResult oldResult = oldItem.result();
            MatchResult newResult = newItem.result();
            // equals on the status too: the same answer as == for an enum, and what Lint's
            // DiffUtilEquals check asks for in this method
            return oldRecipe.getName().equals(newRecipe.getName())
                    && Objects.equals(oldRecipe.getServings(), newRecipe.getServings())
                    && oldResult.status().equals(newResult.status())
                    && oldResult.haveCount() == newResult.haveCount()
                    && oldResult.needCount() == newResult.needCount();
        }
    }
}
