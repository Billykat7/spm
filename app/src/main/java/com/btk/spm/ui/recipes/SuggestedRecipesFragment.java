package com.btk.spm.ui.recipes;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.BuildConfig;
import com.btk.spm.R;
import com.btk.spm.databinding.FragmentSuggestedRecipesBinding;
import com.btk.spm.ui.LifecycleLoggingFragment;
import com.btk.spm.ui.ListChangeLog;
import com.btk.spm.ui.MainActivity;
import com.btk.spm.ui.Tab;
import com.google.android.material.divider.MaterialDividerItemDecoration;

/**
 * The Recipes tab: the recipes the pantry can make right now, and only those.
 *
 * <p>It does what a screen does and nothing else: it inflates its binding, observes
 * {@link SuggestedRecipesViewModel#getState()} and forwards taps. Each {@link UiState} goes through
 * {@link RecipesRender}, which decides which one of the progress indicator, the list and the empty
 * state is shown; this class only applies it. The list is {@link UiState.Content#canMake()} exactly as
 * the ViewModel built it from the matcher's partition; this screen never filters it, and the
 * almost-there recipes it also carries wait for their own section (Issue 27). The toolbar reads
 * "Suggested recipes (N)", N being the length of that same list.
 *
 * <p>When nothing can be made, the shared empty state says why ({@link EmptyReason}): the brief's
 * "No recipes match your pantry yet, add more ingredients", an empty pantry, or recipes that did not
 * load. For the first two its "Add ingredients" button goes to the Pantry tab of the same
 * {@link MainActivity}, through {@link MainActivity#intentFor}, so no second host is opened. The
 * progress indicator is shown only before the first result: a later match leaves the current rows or
 * message on screen until its result replaces them.
 *
 * <p>The state lives in the ViewModel, so a rotation shows the same rows at once, with no second
 * match and no progress indicator. Its lifecycle callbacks are logged in debug builds
 * ({@link LifecycleLoggingFragment}), and so is every change the adapter makes ({@link ListChangeLog}).
 */
public class SuggestedRecipesFragment extends LifecycleLoggingFragment
        implements RecipeAdapter.OnRecipeClickListener {

    /** Names this list in the debug log: {@code adb logcat -s ListChange}. */
    private static final String LIST_NAME = "Recipes";

    /** The logcat tag of a row tap, until the detail screen exists: {@code adb logcat -s Recipes}. */
    private static final String LOG_TAG = "Recipes";

    @Nullable
    private FragmentSuggestedRecipesBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSuggestedRecipesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentSuggestedRecipesBinding views = requireBinding();

        SuggestedRecipesViewModel viewModel = new ViewModelProvider(this,
                new SuggestedRecipesViewModelFactory(requireActivity().getApplication()))
                .get(SuggestedRecipesViewModel.class);

        RecipeAdapter adapter = new RecipeAdapter(this);
        // Hold the saved scroll position until the first list arrives, so a rotation lands on the
        // same rows instead of the top of an adapter that is still empty
        adapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        ListChangeLog.attach(adapter, LIST_NAME);
        views.recipeList.setAdapter(adapter);
        MaterialDividerItemDecoration divider =
                new MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL);
        divider.setLastItemDecorated(false);
        views.recipeList.addItemDecoration(divider);

        // The included empty state has no words of its own: the message follows the reason, and the
        // button always says the same thing and goes to the pantry
        views.emptyState.emptyStateAction.setText(R.string.recipes_empty_action);
        views.emptyState.emptyStateAction.setOnClickListener(v -> openPantry());
        // TalkBack reads the new message when the reason changes, without the user moving focus
        ViewCompat.setAccessibilityLiveRegion(views.emptyState.emptyStateTitle,
                ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);

        // The view's lifecycle, not the Fragment's: the observer goes when the view does
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(state, adapter));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // The Fragment can outlive its view; drop the views with it
        binding = null;
    }

    /**
     * A row was tapped. Issue 25 opens {@code RecipeDetailActivity.intentFor(context, recipeId)}
     * here; until then a debug build logs the id, which is all a tap has to carry.
     */
    @Override
    public void onRecipeClick(long recipeId) {
        // TODO(Issue 25): startActivity(RecipeDetailActivity.intentFor(requireContext(), recipeId))
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Recipe tapped: id " + recipeId);
        }
    }

    /** Applies {@link RecipesRender}: exactly one of the progress indicator, the list and the empty state. */
    private void render(@NonNull UiState state, @NonNull RecipeAdapter adapter) {
        FragmentSuggestedRecipesBinding views = requireBinding();
        RecipesRender render = RecipesRender.of(state);
        // show() and hide() rather than visibility: the indicator waits before it appears, so a first
        // match that finishes quickly shows no spinner at all
        if (render.progress()) {
            views.loading.show();
        } else {
            views.loading.hide();
            adapter.submitList(render.rows());
            showCount(render.count());
        }
        views.recipeList.setVisibility(render.list() ? View.VISIBLE : View.GONE);
        views.emptyState.getRoot().setVisibility(render.empty() ? View.VISIBLE : View.GONE);
        if (render.empty()) {
            views.emptyState.emptyStateTitle.setText(render.emptyMessage());
            views.emptyState.emptyStateAction.setVisibility(render.addIngredients() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Switches the running host to the Pantry tab. {@link MainActivity#intentFor} sets
     * {@code FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP}, so the host already on screen receives
     * it in {@code onNewIntent} and selects the tab; no second {@code MainActivity} is created.
     */
    private void openPantry() {
        startActivity(MainActivity.intentFor(requireContext(), Tab.PANTRY));
    }

    /** Puts "Suggested recipes (N)" in the host's toolbar, while the Recipes tab is the one shown. */
    private void showCount(int count) {
        if (requireActivity() instanceof MainActivity host) {
            host.setToolbarTitle(Tab.RECIPES,
                    getResources().getQuantityString(R.plurals.suggested_recipes_title, count, count));
        }
    }

    /** Returns the binding, which exists between {@code onCreateView} and {@code onDestroyView}. */
    @NonNull
    private FragmentSuggestedRecipesBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("SuggestedRecipesFragment's view is not created");
        }
        return binding;
    }
}
