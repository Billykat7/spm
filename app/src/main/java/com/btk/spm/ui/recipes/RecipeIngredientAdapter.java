package com.btk.spm.ui.recipes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.databinding.ItemRecipeIngredientBinding;
import com.btk.spm.util.QuantityFormatter;
import com.google.android.material.color.MaterialColors;

/**
 * The ingredients of the recipe detail screen: one {@code item_recipe_ingredient} row per
 * {@link IngredientRow}, with a check or a cross and "need 250 g, have 1 kg".
 *
 * <p>It renders what it is given and decides nothing. {@link IngredientRow#have()} is the matcher's
 * verdict on that line ({@code MatchResult.checks()}), and the amounts were converted to display units
 * before they arrived; this class only picks the icon, its tint and description, and formats the text.
 * When the pantry changes while the screen is open, {@link ItemDiff} rebinds only the rows whose mark
 * or amounts changed.
 */
public class RecipeIngredientAdapter extends ListAdapter<IngredientRow, RecipeIngredientAdapter.ViewHolder> {

    /** Creates an empty adapter; rows appear when the first list is submitted. */
    public RecipeIngredientAdapter() {
        super(new ItemDiff());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemRecipeIngredientBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /** Holds one row's views, bound through {@link ItemRecipeIngredientBinding}. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemRecipeIngredientBinding binding;

        ViewHolder(@NonNull ItemRecipeIngredientBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /** Shows the mark for the matcher's verdict, the name and the two amounts. */
        void bind(@NonNull IngredientRow row) {
            Context context = binding.getRoot().getContext();
            binding.mark.setImageResource(row.have() ? R.drawable.ic_have : R.drawable.ic_need);
            binding.mark.setContentDescription(context.getString(row.have() ? R.string.ingredient_have : R.string.ingredient_need));
            binding.mark.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(binding.mark,
                    row.have() ? androidx.appcompat.R.attr.colorPrimary : androidx.appcompat.R.attr.colorError)));
            binding.name.setText(row.name());
            String need = QuantityFormatter.format(context, row.required());
            binding.amounts.setText(row.available() == null
                    ? context.getString(R.string.ingredient_need_have_none, need)
                    : context.getString(R.string.ingredient_need_have, need, QuantityFormatter.format(context, row.available())));
        }
    }

    /** Rows are the same ingredient when their recipe line ids match; rebound when anything shown changed. */
    static final class ItemDiff extends DiffUtil.ItemCallback<IngredientRow> {

        @Override
        public boolean areItemsTheSame(@NonNull IngredientRow oldItem, @NonNull IngredientRow newItem) {
            return oldItem.ingredientId() == newItem.ingredientId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull IngredientRow oldItem, @NonNull IngredientRow newItem) {
            return oldItem.equals(newItem);
        }
    }
}
