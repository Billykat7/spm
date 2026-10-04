package com.btk.spm.ui.recipes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.btk.spm.R;
import com.btk.spm.databinding.ActivityRecipeDetailBinding;
import com.btk.spm.ui.StateMessage;
import com.btk.spm.util.IntentKeys;

import java.util.List;

/**
 * One recipe in full: every ingredient with a check or a cross and how much is needed and held, then
 * the numbered method (brief §2.2, decision 3).
 *
 * <p>It is opened by an explicit {@link Intent} that only {@link #intentFor(Context, long)} builds,
 * carrying the recipe's id in {@link IntentKeys#EXTRA_RECIPE_ID} and nothing else. {@code onCreate}
 * reads the id once; a missing or non-positive id, or one no recipe has, says "Recipe not found" and
 * closes, rather than showing an empty screen. Up and Back both close the screen, so the user lands
 * on the Recipes tab they came from, still showing its list.
 *
 * <p>The screen shows any recipe, suggested or not: the almost-there rows (Issue 27) open it too, and
 * the cross next to the one missing ingredient is what they are for. Every mark comes from
 * {@link RecipeDetailViewModel}, which asks the matcher; the adapters only render. The whole screen is
 * one {@code RecyclerView} over a {@link ConcatAdapter} (header, ingredients, the "Method" heading,
 * steps), so it scrolls as one and the layout manager keeps the position across a rotation, while the
 * ViewModel keeps the data.
 */
public class RecipeDetailActivity extends AppCompatActivity {

    /** What {@code getLongExtra} gives when the extra is absent; no recipe id is 0 or below. */
    private static final long NO_ID = 0L;

    private ActivityRecipeDetailBinding binding;

    /**
     * Returns the Intent that opens this screen on one recipe: its id travels in
     * {@link IntentKeys#EXTRA_RECIPE_ID}, the one place the extra's name is written.
     *
     * @param context  the screen starting the Intent
     * @param recipeId the {@code recipes.id} of the recipe to show
     * @return an explicit Intent for {@code RecipeDetailActivity} carrying the id
     */
    @NonNull
    public static Intent intentFor(@NonNull Context context, long recipeId) {
        return new Intent(context, RecipeDetailActivity.class).putExtra(IntentKeys.EXTRA_RECIPE_ID, recipeId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Read once, with a default that means "absent"; anything that cannot be a row id ends here
        long recipeId = getIntent().getLongExtra(IntentKeys.EXTRA_RECIPE_ID, NO_ID);
        if (recipeId <= NO_ID) {
            closeAsNotFound();
            return;
        }

        EdgeToEdge.enable(this);
        binding = ActivityRecipeDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Edge to edge draws under the system bars; the root keeps the toolbar and the list clear of them
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        RecipeHeaderAdapter header = new RecipeHeaderAdapter();
        RecipeIngredientAdapter ingredients = new RecipeIngredientAdapter();
        SectionHeadingAdapter methodHeading = new SectionHeadingAdapter(R.string.recipe_method);
        RecipeStepAdapter steps = new RecipeStepAdapter();
        // Until the recipe arrives the list is empty; hold a saved scroll position until it is not
        header.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        ingredients.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        steps.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        binding.detailList.setAdapter(new ConcatAdapter(header, ingredients, methodHeading, steps));

        RecipeDetailViewModel viewModel = new ViewModelProvider(this,
                new RecipeDetailViewModelFactory(getApplication(), recipeId)).get(RecipeDetailViewModel.class);
        viewModel.getState().observe(this, state -> {
            if (state instanceof DetailUiState.Loading) {
                StateMessage.showLoading(binding.stateMessage, R.string.recipe_loading);
            } else if (state instanceof DetailUiState.NotFound) {
                closeAsNotFound();
            } else if (state instanceof DetailUiState.Loaded loaded) {
                StateMessage.hide(binding.stateMessage);
                header.submitList(List.of(loaded));
                ingredients.submitList(loaded.rows());
                methodHeading.setShown(!loaded.recipe().getRecipe().getSteps().isEmpty());
                steps.submitList(loaded.recipe().getRecipe().getSteps());
            }
        });
    }

    /** Up closes the screen, like Back, so the Recipes tab it came from is shown again as it was. */
    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    /** Says the recipe does not exist and closes; a Toast, because it must outlive this screen. */
    private void closeAsNotFound() {
        Toast.makeText(this, R.string.recipe_not_found, Toast.LENGTH_LONG).show();
        finish();
    }
}
