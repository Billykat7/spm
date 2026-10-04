package com.btk.spm.ui.pantry;

import androidx.annotation.NonNull;

import com.btk.spm.domain.UnitsSystem;

import java.util.Objects;

/**
 * The two settings a pantry row is drawn with: how many days ahead its badge reads as expiring soon,
 * and the units its amount is shown in (Issue 28).
 *
 * <p>{@code PantryViewModel} builds one from the live preferences and {@code PantryAdapter} redraws
 * every row when it changes, so moving the threshold or switching to imperial on the Settings tab is
 * already on the pantry list when the user comes back to it.
 *
 * @param expiryThresholdDays days ahead that still count as expiring soon (decision 6)
 * @param unitsSystem         the units amounts are shown in, display only (decision 5)
 */
public record PantryDisplay(int expiryThresholdDays, @NonNull UnitsSystem unitsSystem) {

    /**
     * Creates the settings of a row.
     *
     * @throws NullPointerException if {@code unitsSystem} is {@code null}
     */
    public PantryDisplay {
        Objects.requireNonNull(unitsSystem, "unitsSystem");
    }
}
