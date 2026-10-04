package com.btk.spm.ui.pantry;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.data.model.PantryItem;
import com.btk.spm.databinding.FragmentPantryBinding;
import com.btk.spm.ui.LifecycleLoggingFragment;
import com.btk.spm.ui.ListChangeLog;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

/**
 * The Pantry tab: everything in the pantry, live from the database, with a button to add more.
 *
 * <p>It does what a screen does and nothing else: it inflates its binding, observes
 * {@link PantryViewModel#getItems()} and forwards events. Each list the database emits goes straight
 * to {@link PantryAdapter#submitList}; the Fragment keeps no copy, so an item inserted or changed
 * anywhere, even from the Database Inspector, appears here with no refresh. An empty pantry shows
 * the shared empty state instead of the list. The FAB and the empty state's button open
 * {@link AddEditIngredientActivity} through its explicit {@code Intent}.
 *
 * <p>Its lifecycle callbacks are logged in debug builds ({@link LifecycleLoggingFragment}), and so
 * is every change the adapter makes to the list ({@link ListChangeLog}).
 */
public class PantryFragment extends LifecycleLoggingFragment implements PantryAdapter.Listener {

    /** Names this list in the debug log: {@code adb logcat -s ListChange}. */
    private static final String LIST_NAME = "Pantry";

    @Nullable
    private FragmentPantryBinding binding;

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

        PantryAdapter adapter = new PantryAdapter(this);
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

        PantryViewModel viewModel = new ViewModelProvider(this).get(PantryViewModel.class);
        // The view's lifecycle, not the Fragment's: the observer goes when the view does, so a list
        // can never be delivered to a destroyed RecyclerView
        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            showEmptyState(items);
        });
    }

    @Override
    public void onDestroyView() {
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

    /** Deleting from the row's overflow menu, with a confirmation and an undo, is Issue 16. */
    @Override
    public void onDelete(@NonNull PantryItem item) {
        // Issue 16: confirm, delete through PantryViewModel, offer undo in a Snackbar
    }

    /** Shows the list when the pantry has items and the empty state when it has none, never both. */
    private void showEmptyState(@NonNull List<PantryItem> items) {
        FragmentPantryBinding views = requireBinding();
        boolean empty = items.isEmpty();
        views.pantryList.setVisibility(empty ? View.GONE : View.VISIBLE);
        views.emptyState.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    /** Opens the add ingredient screen. Issue 14 starts it through an {@code ActivityResultLauncher}. */
    private void openAddIngredient() {
        startActivity(AddEditIngredientActivity.intentForAdd(requireContext()));
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
