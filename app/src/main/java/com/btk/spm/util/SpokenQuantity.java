package com.btk.spm.util;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;

import com.btk.spm.R;
import com.btk.spm.domain.DisplayQuantity;
import com.btk.spm.domain.DisplayUnit;

import java.util.Locale;

/**
 * An amount as a screen reader should say it: "4 pieces", "1.5 kilograms", "1 litre" (Issue 30).
 *
 * <p>{@link QuantityFormatter} writes {@code 4 pcs}, which is right on screen and wrong aloud: TalkBack
 * reads it as "4 p c s". Every display unit has a plural here with its full name, so the pantry row
 * can be announced in words.
 */
public final class SpokenQuantity {

    private SpokenQuantity() {
        // Static helpers only; never instantiated
    }

    /**
     * Returns {@code quantity} in words, with the unit's full name in the right number.
     *
     * @param resources the app's resources, in the device's language
     * @param quantity  the amount and the unit it is shown in
     * @return "4 pieces", "1.5 kilograms", "1 litre"
     */
    @NonNull
    public static String of(@NonNull Resources resources, @NonNull DisplayQuantity quantity) {
        Locale locale = resources.getConfiguration().getLocales().get(0);
        String amount = QuantityFormatter.formatAmount(quantity.amount(), locale);
        // Exactly one takes the singular; 1.5, like 0 and 2, takes the plural, as English says it
        int count = quantity.amount() == 1 ? 1 : 2;
        return resources.getQuantityString(unitName(quantity.unit()), count, amount);
    }

    /** The plural that names {@code unit} in full. */
    @PluralsRes
    static int unitName(@NonNull DisplayUnit unit) {
        return switch (unit) {
            case G -> R.plurals.unit_spoken_g;
            case KG -> R.plurals.unit_spoken_kg;
            case ML -> R.plurals.unit_spoken_ml;
            case L -> R.plurals.unit_spoken_l;
            case TSP -> R.plurals.unit_spoken_tsp;
            case TBSP -> R.plurals.unit_spoken_tbsp;
            case CUP -> R.plurals.unit_spoken_cup;
            case PCS -> R.plurals.unit_spoken_pcs;
            case OZ -> R.plurals.unit_spoken_oz;
            case FL_OZ -> R.plurals.unit_spoken_fl_oz;
        };
    }
}
