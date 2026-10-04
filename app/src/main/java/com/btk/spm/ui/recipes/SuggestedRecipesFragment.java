package com.btk.spm.ui.recipes;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.databinding.FragmentSuggestedRecipesBinding;
import com.btk.spm.ui.FragmentLifecycleLog;
import com.btk.spm.ui.ListChangeLog;
import com.btk.spm.ui.MainActivity;
import com.btk.spm.ui.StateMessage;
import com.btk.spm.ui.Tab;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

/**
 * The Recipes tab: the recipes the pantry can make right now, then, clearly apart, the ones one
 * ingredient short.
 *
 * <p>It does what a screen does and nothing else: it inflates its binding, observes
 * {@link SuggestedRecipesViewModel#getState()} and forwards taps. Each {@link UiState} goes through
 * {@link RecipesRender}, which decides which one of the progress indicator, the list and the empty
 * state is shown; this class only applies it. The list is one {@code RecyclerView} over a
 * {@link ConcatAdapter} of four sections, from {@link RecipeSections#split}: the brief's zero-match
 * sentence when nothing can be made, the suggestions ({@link RecipeAdapter}, exactly the matcher's
 * {@code canMake}), the "Almost there (missing one ingredient)" heading with a divider, and the
 * almost-there cards ({@link AlmostThereAdapter}). The brief allows that last list for bonus credit only
 * if it is "clearly separated from the strict suggestions" (§2.3): it has its own adapter, its own row
 * and its own heading, is never counted in "Suggested recipes (N)", and is absent when it is empty.
 *
 * <p>When there is nothing to show at all, the shared empty state says why ({@link EmptyReason}): the
 * brief's "No recipes match your pantry yet, add more ingredients", an empty pantry, or recipes that
 * did not load. For the first two its "Add ingredients" button goes to the Pantry tab of the same
 * {@link MainActivity}, through {@link MainActivity#intentFor}, so no second host is opened. The
 * progress indicator is shown only before the first result: a later match leaves the current rows or
 * message on screen until its result replaces them. A tap on any recipe opens it in full.
 *
 * <p>The state lives in the ViewModel, so a rotation shows the same rows at once, with no second
 * match and no progress indicator. Its lifecycle callbacks are logged in debug builds
 * ({@link FragmentLifecycleLog}), and so is every change the adapter makes ({@link ListChangeLog}).
 */
public class SuggestedRecipesFragment extends Fragment
        implements RecipeAdapter.OnRecipeClickListener {

    /** Names the suggestions in the debug log: {@code adb logcat -s ListChange}. */
    private static final String LIST_NAME = "Recipes";

    /** Names the "Almost there" section in the debug log. */
    private static final String ALMOST_LIST_NAME = "Almost there";

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

        // One RecyclerView, four adapters in screen order: the sections are separate adapters, so the
        // suggestions and the almost-there recipes are kept apart by structure, not by a sort order,
        // and the screen still has one scroll position and recycles its rows (Issue 27). No adapter
        // holds back the saved position when empty: inside a ConcatAdapter an empty child with that
        // policy would block it for good, and an empty section is normal here. The ViewModel outlives
        // a rotation, so the rows are back before the position is restored.
        Sections sections = new Sections(new ZeroMatchHeaderAdapter(), new RecipeAdapter(this),
                new SectionHeadingAdapter(R.string.almost_there_heading, true), new AlmostThereAdapter(this));
        ListChangeLog.attach(sections.suggested, LIST_NAME);
        ListChangeLog.attach(sections.almostThere, ALMOST_LIST_NAME);
        ConcatAdapter all = new ConcatAdapter(sections.zeroMatchHeader, sections.suggested,
                sections.almostThereHeader, sections.almostThere);
        views.recipeList.setAdapter(all);
        views.recipeList.addItemDecoration(new SuggestedRowDividers(requireContext(), all, sections.suggested));

        // TalkBack reads the new message when the reason changes, without the user moving focus
        // (a platform call since API 19, so no ViewCompat at minSdk 26)
        views.stateMessage.stateHeadline.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        // The view's lifecycle, not the Fragment's: the observer goes when the view does
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(state, sections));
        // Imperial on the Settings tab: the cards' amounts read in ounces; which recipes are listed does not change
        viewModel.getUnitsSystem().observe(getViewLifecycleOwner(), sections.almostThere::setUnitsSystem);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // The Fragment can outlive its view; drop the views with it
        binding = null;
    }

    /**
     * A row was tapped: open that recipe in full, through the Intent only
     * {@link RecipeDetailActivity#intentFor} builds.
     */
    @Override
    public void onRecipeClick(long recipeId) {
        startActivity(RecipeDetailActivity.intentFor(requireContext(), recipeId));
    }

    /**
     * Applies {@link RecipesRender}, exactly one of the progress indicator, the list and the empty
     * state, and hands each section its part from {@link RecipeSections#split}. Nothing here decides
     * which recipe goes where; the adapters refuse a recipe of the wrong status.
     */
    private void render(@NonNull UiState state, @NonNull Sections sections) {
        FragmentSuggestedRecipesBinding views = requireBinding();
        RecipesRender render = RecipesRender.of(state);
        // The indicator waits before it appears, so a first match that finishes quickly shows no
        // spinner at all
        if (render.progress()) {
            StateMessage.showLoading(views.stateMessage, R.string.recipes_loading);
        } else {
            sections.show(state instanceof UiState.Content content ? RecipeSections.split(content) : null);
            // Only the suggestions are counted, never the almost-there recipes
            showCount(render.count());
        }
        views.recipeList.setVisibility(render.list() ? View.VISIBLE : View.GONE);
        if (render.empty()) {
            // The message follows the reason; the button always says the same thing and goes to the pantry
            StateMessage.showMessage(views.stateMessage, render.emptyMessage(), StateMessage.NONE,
                    render.addIngredients() ? R.string.recipes_empty_action : StateMessage.NONE, v -> openPantry());
        } else if (render.list()) {
            StateMessage.hide(views.stateMessage);
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

    /** The four adapters of the list, in screen order, and how one split is handed to them. */
    private static final class Sections {

        final ZeroMatchHeaderAdapter zeroMatchHeader;
        final RecipeAdapter suggested;
        final SectionHeadingAdapter almostThereHeader;
        final AlmostThereAdapter almostThere;

        Sections(@NonNull ZeroMatchHeaderAdapter zeroMatchHeader, @NonNull RecipeAdapter suggested,
                 @NonNull SectionHeadingAdapter almostThereHeader, @NonNull AlmostThereAdapter almostThere) {
            this.zeroMatchHeader = zeroMatchHeader;
            this.suggested = suggested;
            this.almostThereHeader = almostThereHeader;
            this.almostThere = almostThere;
        }

        /** Shows {@code split}, or clears every section when there is nothing to show. */
        void show(@Nullable RecipeSections.Sections split) {
            zeroMatchHeader.setShown(split != null && split.zeroMatchHeader());
            suggested.submitList(split == null ? List.of() : split.suggested());
            almostThereHeader.setShown(split != null && split.almostThereHeader());
            almostThere.submitList(split == null ? List.of() : split.almostThere());
        }
    }

    /**
     * Draws the list divider between two suggested rows only. The headers and the outlined
     * almost-there cards bring their own separation, and a line through them would blur the break
     * between the sections.
     */
    private static final class SuggestedRowDividers extends MaterialDividerItemDecoration {

        private final ConcatAdapter all;
        private final RecipeAdapter suggested;

        SuggestedRowDividers(@NonNull Context context, @NonNull ConcatAdapter all, @NonNull RecipeAdapter suggested) {
            super(context, LinearLayoutManager.VERTICAL);
            this.all = all;
            this.suggested = suggested;
            setLastItemDecorated(false);
        }

        @Override
        protected boolean shouldDrawDivider(int position, @Nullable RecyclerView.Adapter<?> adapter) {
            return isSuggestedRow(position) && isSuggestedRow(position + 1);
        }

        private boolean isSuggestedRow(int position) {
            return position >= 0 && position < all.getItemCount()
                    && all.getWrappedAdapterAndPosition(position).first == suggested;
        }
    }
}
