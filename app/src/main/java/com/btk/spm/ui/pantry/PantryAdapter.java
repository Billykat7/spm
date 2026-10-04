package com.btk.spm.ui.pantry;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.databinding.ItemPantryBinding;
import com.btk.spm.domain.ExpiryRules;
import com.btk.spm.util.ExpiryStatus;
import com.btk.spm.util.QuantityFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Shows the pantry in a {@code RecyclerView}: one {@code item_pantry} row per {@link PantryItem}.
 *
 * <p>It is a {@link ListAdapter}, so it keeps no list of its own. {@code PantryFragment} hands it
 * each list the database emits through {@link #submitList}, and {@link ItemDiff} works out, on a
 * background thread, which rows were added, removed, moved or changed. Only those rows are rebound
 * and animated: when one item's quantity changes, that row updates and the others stay as they are.
 *
 * <p>The adapter only renders. It does not sort (the {@code ViewModel} applies the
 * {@link SortOrder}), filter or write; a tap on a row or a choice in its overflow menu goes to the
 * {@link Listener} the Fragment implements. Nor does it decide what "expiring soon" means: each row's
 * badge comes from {@link ExpiryRules} for today's date and the threshold it was given, shown through
 * {@link ExpiryBadgeFormatter}. The badge is worked out when a row is bound, so a list left open
 * across midnight shows yesterday's badges until its rows are bound again.
 */
public class PantryAdapter extends ListAdapter<PantryItem, PantryAdapter.ViewHolder> {

    /** What a row asks its screen to do. The Fragment implements it; the adapter never acts itself. */
    public interface Listener {

        /**
         * The row was tapped.
         *
         * @param item the item the row shows
         */
        void onItemClick(@NonNull PantryItem item);

        /**
         * Edit was chosen in the row's overflow menu.
         *
         * @param item the item to edit
         */
        void onEdit(@NonNull PantryItem item);

        /**
         * Delete was chosen in the row's overflow menu.
         *
         * @param item the item to delete
         */
        void onDelete(@NonNull PantryItem item);
    }

    private final Listener listener;

    /** Days ahead that still count as expiring soon (decision 6), from the user's settings. */
    private final int expiryThresholdDays;

    /**
     * Creates an empty adapter; rows appear when the first list is submitted.
     *
     * @param listener            receives the row taps and the overflow menu choices
     * @param expiryThresholdDays how many days ahead a badge reads as expiring soon
     */
    public PantryAdapter(@NonNull Listener listener, int expiryThresholdDays) {
        super(new ItemDiff());
        this.listener = listener;
        this.expiryThresholdDays = expiryThresholdDays;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPantryBinding binding = ItemPantryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewHolder holder = new ViewHolder(binding);
        // Listeners are set once per row view, not on every bind, and look the item up when tapped,
        // so a row that was rebound or moved since never reports the item it used to show
        binding.getRoot().setOnClickListener(v -> withItemAt(holder, listener::onItemClick));
        binding.overflow.setOnClickListener(v -> withItemAt(holder, item -> showOverflowMenu(v, item)));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), LocalDate.now(), expiryThresholdDays);
    }

    /** Runs {@code action} on the item the row shows now, unless the row is on its way out. */
    private void withItemAt(@NonNull ViewHolder holder, @NonNull Consumer<PantryItem> action) {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            action.accept(getItem(position));
        }
    }

    /** Opens the row's Edit and Delete menu under its overflow button. */
    private void showOverflowMenu(@NonNull View anchor, @NonNull PantryItem item) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.inflate(R.menu.item_pantry_overflow);
        menu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_edit_ingredient) {
                listener.onEdit(item);
                return true;
            }
            if (id == R.id.action_delete_ingredient) {
                listener.onDelete(item);
                return true;
            }
            return false;
        });
        menu.show();
    }

    /** Holds one row's views, bound through {@link ItemPantryBinding}. */
    static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemPantryBinding binding;

        ViewHolder(@NonNull ItemPantryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Shows {@code item}: its name, its amount with the unit's symbol ({@code 4 pcs}) and its
         * expiry badge as of {@code today}.
         */
        void bind(@NonNull PantryItem item, @NonNull LocalDate today, int expiryThresholdDays) {
            Context context = binding.getRoot().getContext();
            binding.name.setText(item.getName());
            binding.quantity.setText(QuantityFormatter.format(context, item.getQuantity(), item.getUnit()));
            // Spoken by TalkBack, so it names the row it belongs to
            binding.overflow.setContentDescription(
                    context.getString(R.string.pantry_item_options_for, item.getName()));

            LocalDate expiry = item.getExpiryDate();
            ExpiryStatus status = ExpiryRules.statusOf(expiry, today, expiryThresholdDays);
            showBadge(ExpiryBadgeFormatter.format(context.getResources(), status,
                    expiry == null ? 0 : ExpiryRules.daysUntil(expiry, today)));
        }

        /** Shows the badge in its theme colours, or hides it for an item with no date. */
        private void showBadge(@Nullable ExpiryBadgeFormatter.Badge badge) {
            Chip chip = binding.expiryBadge;
            if (badge == null) {
                chip.setVisibility(View.GONE);
                return;
            }
            chip.setText(badge.text());
            chip.setChipBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(chip, badge.backgroundAttr())));
            chip.setTextColor(MaterialColors.getColor(chip, badge.textColorAttr()));
            chip.setVisibility(View.VISIBLE);
        }
    }

    /**
     * How {@link ListAdapter} compares two lists of the pantry. Two items are the same row when their
     * ids match; the row needs rebinding when anything in it changed, which {@link PantryItem#equals}
     * decides over every column.
     */
    static final class ItemDiff extends DiffUtil.ItemCallback<PantryItem> {

        @Override
        public boolean areItemsTheSame(@NonNull PantryItem oldItem, @NonNull PantryItem newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull PantryItem oldItem, @NonNull PantryItem newItem) {
            return oldItem.equals(newItem);
        }
    }
}
