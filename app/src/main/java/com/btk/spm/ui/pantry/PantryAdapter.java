package com.btk.spm.ui.pantry;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.databinding.ItemPantryBinding;
import com.btk.spm.util.QuantityFormatter;

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
 * {@link Listener} the Fragment implements.
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

    /**
     * Creates an empty adapter; rows appear when the first list is submitted.
     *
     * @param listener receives the row taps and the overflow menu choices
     */
    public PantryAdapter(@NonNull Listener listener) {
        super(new ItemDiff());
        this.listener = listener;
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
        holder.bind(getItem(position));
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

        /** Shows {@code item}: its name, and its amount with the unit's symbol ({@code 4 pcs}). */
        void bind(@NonNull PantryItem item) {
            Context context = binding.getRoot().getContext();
            binding.name.setText(item.getName());
            binding.quantity.setText(QuantityFormatter.format(context, item.getQuantity(), item.getUnit()));
            // Spoken by TalkBack, so it names the row it belongs to
            binding.overflow.setContentDescription(
                    context.getString(R.string.pantry_item_options_for, item.getName()));
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
