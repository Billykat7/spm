package com.btk.spm.ui.recipes;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.util.List;

/**
 * The Recipes tab: the recipes the pantry can make right now, and only those.
 *
 * <p>It does what a screen does and nothing else: it inflates its binding, observes
 * {@link SuggestedRecipesViewModel#getState()} and forwards taps. Each {@link UiState} is drawn as
 * it comes: {@link UiState.Loading} as a progress indicator, {@link UiState.Empty} as the shared
 * empty state, and {@link UiState.Content} as the list, handed to {@link RecipeAdapter#submitList}.
 * The list is {@link UiState.Content#canMake()} exactly as the ViewModel built it from the matcher's
 * partition; this screen never filters it, and the almost-there recipes it also carries wait for
 * their own section (Issue 27). The toolbar reads "Suggested recipes (N)", N being the length of that
 * same list.
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

        // The included empty state has no words of its own. Issue 24 gives it its final sentence and
        // the button to the pantry; until then it says why the list is empty, with no button.
        views.emptyState.emptyStateTitle.setText(R.string.suggested_empty_title);
        views.emptyState.emptyStateAction.setVisibility(View.GONE);

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

    /** Shows exactly one of the progress indicator, the empty state and the list. */
    private void render(@NonNull UiState state, @NonNull RecipeAdapter adapter) {
        FragmentSuggestedRecipesBinding views = requireBinding();
        if (state instanceof UiState.Loading) {
            views.loading.show();
            views.recipeList.setVisibility(View.GONE);
            views.emptyState.getRoot().setVisibility(View.GONE);
            return;
        }
        views.loading.hide();
        List<MatchedRecipe> canMake = state instanceof UiState.Content content ? content.canMake() : List.of();
        adapter.submitList(canMake);
        views.recipeList.setVisibility(canMake.isEmpty() ? View.GONE : View.VISIBLE);
        views.emptyState.getRoot().setVisibility(canMake.isEmpty() ? View.VISIBLE : View.GONE);
        showCount(canMake.size());
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
