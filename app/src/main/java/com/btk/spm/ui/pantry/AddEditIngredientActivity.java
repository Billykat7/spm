package com.btk.spm.ui.pantry;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.btk.spm.databinding.ActivityAddEditIngredientBinding;

/**
 * The screen that adds an ingredient to the pantry, and later edits one (decision 3).
 *
 * <p>It is a separate {@code Activity}, opened by an explicit {@link Intent} that only this class
 * builds: {@link #intentForAdd(Context)} carries no extra, which means "add"; Issue 15 adds
 * {@code intentFor(Context, long)}, whose {@code IntentKeys.EXTRA_PANTRY_ITEM_ID} means "edit".
 *
 * <p>A shell for now: a toolbar titled "Add ingredient" with an up arrow, and an empty content area.
 * Issue 14 adds the form, its validation and the result returned to the list.
 */
public class AddEditIngredientActivity extends AppCompatActivity {

    private ActivityAddEditIngredientBinding binding;

    /**
     * Returns the Intent that opens this screen to add a new ingredient. It carries no extra: the
     * absence of {@code IntentKeys.EXTRA_PANTRY_ITEM_ID} is what "add" means.
     *
     * @param context the screen starting the Intent
     * @return an explicit Intent for {@code AddEditIngredientActivity}
     */
    @NonNull
    public static Intent intentForAdd(@NonNull Context context) {
        return new Intent(context, AddEditIngredientActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddEditIngredientBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge to edge draws under the system bars, so the root keeps its content clear of them
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // The toolbar is the action bar, so it takes the title from the manifest's android:label
        // and shows the up arrow, with "Navigate up" read by TalkBack
        setSupportActionBar(binding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * Up goes back the way Back does: to the Pantry tab of the {@code MainActivity} underneath, as it
     * was left, rather than a new copy of it. Issue 14 can then ask about unsaved changes in one place.
     */
    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
