package com.btk.spm.ui.pantry;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.databinding.FragmentPantryBinding;
import com.btk.spm.ui.LifecycleLoggingFragment;
import com.btk.spm.ui.ListChangeLog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Pantry tab: everything in the pantry, live from the database, with a button to add more.
 *
 * <p>It does what a screen does and nothing else: it inflates its binding, observes
 * {@link PantryViewModel#getItems()} and forwards events. Each list the database emits goes straight
 * to {@link PantryAdapter#submitList}; the Fragment keeps no copy, so an item inserted or changed
 * anywhere, even from the Database Inspector, appears here with no refresh. An empty pantry shows
 * the shared empty state instead of the list. The FAB and the empty state's button open
 * {@link AddEditIngredientActivity} through its explicit {@code Intent}, for a result: when it comes
 * back {@link Activity#RESULT_OK} a Snackbar says the ingredient was saved. Nothing is passed back;
 * the new row reaches the list the way every row does, through Room.
 *
 * <p>Delete works the same way. The row's overflow Delete asks for confirmation, then
 * {@link PantryViewModel#delete} removes the row from the database and the list follows. A Snackbar
 * offers to undo it, and undo inserts the very object that was deleted, so the row comes back with
 * its original id.
 *
 * <p>Its lifecycle callbacks are logged in debug builds ({@link LifecycleLoggingFragment}), and so
 * is every change the adapter makes to the list ({@link ListChangeLog}).
 */
public class PantryFragment extends LifecycleLoggingFragment implements PantryAdapter.Listener {

    /** Names this list in the debug log: {@code adb logcat -s ListChange}. */
    private static final String LIST_NAME = "Pantry";

    @Nullable
    private FragmentPantryBinding binding;

    /** Set in {@code onViewCreated}; the ViewModel outlives the view, so it is the same one after a rotation. */
    private PantryViewModel viewModel;

    /** The open delete confirmation, kept so it is closed with the view rather than leaked. */
    @Nullable
    private AlertDialog deleteConfirmation;

    /**
     * Starts the add screen and hears how it ended. Registered when the Fragment is created, as the
     * Activity Result API requires, so a result that arrives after a rotation still finds it.
     */
    private final ActivityResultLauncher<Intent> addIngredient =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    showSaved();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPantryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentPantryBinding views = requireBinding();

        viewModel = new ViewModelProvider(this).get(PantryViewModel.class);
        PantryAdapter adapter = new PantryAdapter(this, viewModel.getExpiryThresholdDays());
        // Hold the saved scroll position until the first list arrives, so a rotation lands on the
        // same rows instead of the top of an adapter that is still empty
        adapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        ListChangeLog.attach(adapter, LIST_NAME);
        views.pantryList.setAdapter(adapter);
        MaterialDividerItemDecoration divider =
                new MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL);
        divider.setLastItemDecorated(false);
        views.pantryList.addItemDecoration(divider);

        // The included empty state has no words of its own; this screen gives it the pantry's
        views.emptyState.emptyStateTitle.setText(R.string.pantry_empty_title);
        views.emptyState.emptyStateAction.setText(R.string.action_add_ingredient);
        views.emptyState.emptyStateAction.setOnClickListener(v -> openAddIngredient());
        views.addIngredient.setOnClickListener(v -> openAddIngredient());

        // The view's lifecycle, not the Fragment's: the observer goes when the view does, so a list
        // can never be delivered to a destroyed RecyclerView
        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            showEmptyState(items);
        });
    }

    @Override
    public void onDestroyView() {
        // A rotation closes the confirmation without deleting; the user taps Delete again (Issue 30
        // decides whether a DialogFragment that survives it is worth having)
        if (deleteConfirmation != null) {
            deleteConfirmation.dismiss();
            deleteConfirmation = null;
        }
        super.onDestroyView();
        // The Fragment can outlive its view (another tab is shown); drop the views with it
        binding = null;
    }

    /** Opening an item for editing is Issue 15. */
    @Override
    public void onItemClick(@NonNull PantryItem item) {
        // Issue 15: open AddEditIngredientActivity.intentFor(context, item.getId())
    }

    /** Editing from the row's overflow menu is Issue 15. */
    @Override
    public void onEdit(@NonNull PantryItem item) {
        // Issue 15: the same as onItemClick
    }

    /**
     * Asks before deleting: the dialog names the item, Cancel changes nothing, Delete removes it and
     * offers an undo.
     */
    @Override
    public void onDelete(@NonNull PantryItem item) {
        deleteConfirmation = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_confirm_title, item.getName()))
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    viewModel.delete(item);
                    showUndo(item);
                })
                .setOnDismissListener(dialog -> deleteConfirmation = null)
                .show();
    }

    /** Shows the list when the pantry has items and the empty state when it has none, never both. */
    private void showEmptyState(@NonNull List<PantryItem> items) {
        FragmentPantryBinding views = requireBinding();
        boolean empty = items.isEmpty();
        views.pantryList.setVisibility(empty ? View.GONE : View.VISIBLE);
        views.emptyState.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    /** Opens the add ingredient screen for a result. */
    private void openAddIngredient() {
        addIngredient.launch(AddEditIngredientActivity.intentForAdd(requireContext()));
    }

    /** Confirms a save above the FAB. The row itself is already on its way through {@code LiveData}. */
    private void showSaved() {
        if (binding == null) {
            return; // the result outlived the view; the list shows the row when one is created
        }
        Snackbar.make(binding.getRoot(), R.string.ingredient_saved, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.addIngredient)
                .show();
    }

    /**
     * Says what was deleted, above the FAB, with an Undo that puts back the same object. The object
     * is kept as it was, id included, because that id is what brings back the original row.
     */
    private void showUndo(@NonNull PantryItem deleted) {
        if (binding == null) {
            return;
        }
        // One undo per delete: a second tap while the Snackbar animates out would insert the row
        // again, and an insert with an id that exists fails on the write thread
        AtomicBoolean restored = new AtomicBoolean();
        Snackbar.make(binding.getRoot(), getString(R.string.deleted_item, deleted.getName()), Snackbar.LENGTH_LONG)
                .setAnchorView(binding.addIngredient)
                .setAction(R.string.action_undo, v -> {
                    if (restored.compareAndSet(false, true)) {
                        viewModel.undoDelete(deleted);
                    }
                })
                .show();
    }

    /** Returns the binding, which exists between {@code onCreateView} and {@code onDestroyView}. */
    @NonNull
    private FragmentPantryBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("PantryFragment's view is not created");
        }
        return binding;
    }
}
