package com.btk.spm.ui.pantry;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.btk.spm.R;
import com.btk.spm.databinding.ActivityAddEditIngredientBinding;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.validation.Field;
import com.btk.spm.domain.validation.FieldError;
import com.btk.spm.domain.validation.ValidationResult;
import com.btk.spm.util.PickerDates;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The screen that adds an ingredient to the pantry, and later edits one (decision 3).
 *
 * <p>It is a separate {@code Activity}, opened by an explicit {@link Intent} that only this class
 * builds: {@link #intentForAdd(Context)} carries no extra, which means "add"; Issue 15 adds
 * {@code intentFor(Context, long)}, whose {@code IntentKeys.EXTRA_PANTRY_ITEM_ID} means "edit". The
 * caller starts it for a result: {@link Activity#RESULT_OK} when an item was saved, nothing passed
 * back, because the list sees the new row through Room.
 *
 * <p>The form is name, quantity, unit and an optional expiry date. The screen holds no rule: Save
 * hands the typed text to {@link AddEditIngredientViewModel#save}, which validates and writes only a
 * valid item, and the screen shows what came back. Every error at once, each under its own field,
 * the first field focused; an error clears as soon as its field is edited. The up arrow and Back
 * cancel without writing.
 */
public class AddEditIngredientActivity extends AppCompatActivity {

    /** Tag of the date picker in the fragment manager, so a rotation can find it again. */
    private static final String EXPIRY_PICKER_TAG = "com.btk.spm.tag.EXPIRY_PICKER";

    private ActivityAddEditIngredientBinding binding;
    private AddEditIngredientViewModel viewModel;

    /** Each validated field and the input layout its error is shown on, in screen order. */
    private final Map<Field, TextInputLayout> fieldLayouts = new EnumMap<>(Field.class);

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
        viewModel = new ViewModelProvider(this).get(AddEditIngredientViewModel.class);

        // Edge to edge draws under the system bars and the keyboard, so the root keeps the form clear
        // of both: the last field can always be scrolled above the keyboard
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
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

        fieldLayouts.put(Field.NAME, binding.nameLayout);
        fieldLayouts.put(Field.QUANTITY, binding.quantityLayout);
        fieldLayouts.put(Field.UNIT, binding.unitLayout);
        fieldLayouts.put(Field.EXPIRY, binding.expiryLayout);

        setUpUnitDropdown();
        setUpExpiryField();
    }

    /**
     * Starts clearing an error when its field is edited. Not in {@code onCreate}: after a rotation
     * each field's text is restored after its error, and a watcher already listening would take that
     * restore for an edit and wipe the error the user has not fixed yet.
     */
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        clearErrorWhenEdited(binding.nameLayout);
        clearErrorWhenEdited(binding.quantityLayout);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.add_edit_ingredient, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            save();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Up cancels the way Back does: to the Pantry tab of the {@code MainActivity} underneath, as it
     * was left, with nothing written and no result.
     */
    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    /** Validates and saves through the ViewModel; closes on success, shows every error otherwise. */
    private void save() {
        ValidationResult result = viewModel.save(textOf(binding.nameLayout), textOf(binding.quantityLayout));
        if (result.isOk()) {
            setResult(Activity.RESULT_OK);
            finish();
            return;
        }
        showErrors(result.errors());
    }

    /**
     * Puts each error under its field and clears the fields that are now valid, then moves focus to
     * the first field in error, or scrolls to it when it cannot take focus. The errors arrive in
     * screen order, so the first is the top one.
     */
    private void showErrors(@NonNull List<FieldError> errors) {
        for (TextInputLayout layout : fieldLayouts.values()) {
            layout.setError(null);
        }
        for (FieldError error : errors) {
            TextInputLayout layout = fieldLayouts.get(error.field());
            if (layout != null) {
                layout.setError(getString(error.messageRes()));
            }
        }
        TextInputLayout first = fieldLayouts.get(errors.get(0).field());
        if (first == null) {
            return;
        }
        // The expiry field is read-only and cannot take focus, so when it is the first field in
        // error it is scrolled into view instead
        if (first.getEditText() == null || !first.getEditText().requestFocus()) {
            binding.formContainer.smoothScrollTo(0, first.getTop());
        }
    }

    /** Fills the dropdown with every unit's symbol and records the tapped position as its enum constant. */
    private void setUpUnitDropdown() {
        Unit[] units = Unit.values();
        String[] symbols = new String[units.length];
        for (int i = 0; i < units.length; i++) {
            symbols[i] = getString(units[i].symbolRes());
        }
        binding.unitInput.setSimpleItems(symbols);
        // The position indexes Unit.values(), the order the symbols were listed in; the text is never read
        binding.unitInput.setOnItemClickListener((parent, view, position, id) -> {
            viewModel.setUnit(units[position]);
            binding.unitLayout.setError(null);
        });
        viewModel.getUnit().observe(this, unit -> {
            // false: show the symbol without filtering the list down to it
            binding.unitInput.setText(unit == null ? "" : getString(unit.symbolRes()), false);
        });
    }

    /** Wires the read-only date field to the picker and the clear icon, and shows the chosen date. */
    private void setUpExpiryField() {
        binding.expiryInput.setOnClickListener(v -> showExpiryPicker());
        binding.expiryLayout.setStartIconOnClickListener(v -> showExpiryPicker());
        binding.expiryLayout.setEndIconOnClickListener(v -> {
            viewModel.setExpiry(null);
            binding.expiryLayout.setError(null);
        });

        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(getResources().getConfiguration().getLocales().get(0));
        viewModel.getExpiry().observe(this, date -> {
            binding.expiryInput.setText(date == null ? "" : format.format(date));
            // Nothing to clear when there is no date
            binding.expiryLayout.setEndIconVisible(date != null);
        });

        // A picker left open across a rotation is restored by the fragment manager without its listener
        @SuppressWarnings("unchecked")
        MaterialDatePicker<Long> restored =
                (MaterialDatePicker<Long>) getSupportFragmentManager().findFragmentByTag(EXPIRY_PICKER_TAG);
        if (restored != null) {
            listenTo(restored);
        }
    }

    /** Opens the date picker on the date already chosen, if any. */
    private void showExpiryPicker() {
        if (getSupportFragmentManager().findFragmentByTag(EXPIRY_PICKER_TAG) != null) {
            return; // already open: a double tap must not stack a second picker
        }
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.expiry_picker_title);
        LocalDate chosen = viewModel.getExpiry().getValue();
        if (chosen != null) {
            builder.setSelection(PickerDates.toSelection(chosen));
        }
        MaterialDatePicker<Long> picker = builder.build();
        listenTo(picker);
        picker.show(getSupportFragmentManager(), EXPIRY_PICKER_TAG);
    }

    /** Records the day the picker confirms; the picker gives midnight UTC, read back in UTC. */
    private void listenTo(@NonNull MaterialDatePicker<Long> picker) {
        picker.addOnPositiveButtonClickListener(selection -> {
            viewModel.setExpiry(PickerDates.toLocalDate(selection));
            binding.expiryLayout.setError(null);
        });
    }

    /** Clears a field's error as soon as its text changes, so a fixed field stops looking wrong. */
    private static void clearErrorWhenEdited(@NonNull TextInputLayout layout) {
        if (layout.getEditText() == null) {
            return;
        }
        layout.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** Returns what is typed in a field, or {@code null} when it has no text view. */
    @Nullable
    private static String textOf(@NonNull TextInputLayout layout) {
        return layout.getEditText() == null || layout.getEditText().getText() == null
                ? null : layout.getEditText().getText().toString();
    }
}
