package com.btk.spm.data.db;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import com.btk.spm.domain.Unit;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the three field types SQLite has no column type for into ones it has, for every entity in
 * {@link AppDatabase}.
 *
 * <ul>
 *   <li>{@link Unit} is stored as {@link Unit#name()} in a {@code TEXT} column: the enum constant is
 *       the identity, while the display symbol is a string resource that may change wording, and an
 *       {@code ordinal()} would corrupt every row the day the enum is reordered (non-negotiable 7).</li>
 *   <li>{@link LocalDate} is stored as its epoch day ({@link LocalDate#toEpochDay()}) in an
 *       {@code INTEGER} column, so SQLite sorts and compares expiry dates as numbers. {@code java.time}
 *       needs no desugaring at {@code minSdk 26} (decision 4).</li>
 *   <li>A recipe's steps, a {@code List<String>}, are stored as one JSON array in a {@code TEXT}
 *       column (decided in Issue 8). Joining them with line breaks would break the day a step
 *       contains one, and a fourth table would add a relation no screen needs.</li>
 * </ul>
 *
 * <p>Every method passes {@code null} through as {@code null}, so a nullable column such as
 * {@code expiry_date} (null means the item never expires, decision 6) round-trips unchanged. Apart
 * from {@code org.json} this class is plain Java, so {@code ConvertersTest} runs it on the JVM.
 */
public final class Converters {

    private Converters() {
        // Room calls the static methods; there is nothing to instantiate
    }

    /**
     * Stores a unit by its enum constant name.
     *
     * @param unit the unit, or {@code null}
     * @return {@code "KG"} for {@link Unit#KG}, or {@code null}
     */
    @TypeConverter
    @Nullable
    public static String unitToName(@Nullable Unit unit) {
        return unit == null ? null : unit.name();
    }

    /**
     * Reads a unit back from its enum constant name.
     *
     * @param name a name written by {@link #unitToName(Unit)}, or {@code null}
     * @return the unit, or {@code null}
     * @throws IllegalArgumentException if {@code name} is no {@link Unit} constant; a row like that
     *     was not written by this app, and guessing a unit would let the matcher compare the wrong
     *     quantities
     */
    @TypeConverter
    @Nullable
    public static Unit nameToUnit(@Nullable String name) {
        return name == null ? null : Unit.valueOf(name);
    }

    /**
     * Stores a date as the number of days since 1970-01-01.
     *
     * @param date the date, or {@code null}
     * @return its epoch day, negative before 1970, or {@code null}
     */
    @TypeConverter
    @Nullable
    public static Long dateToEpochDay(@Nullable LocalDate date) {
        return date == null ? null : date.toEpochDay();
    }

    /**
     * Reads a date back from its epoch day.
     *
     * @param epochDay a value written by {@link #dateToEpochDay(LocalDate)}, or {@code null}
     * @return the date, or {@code null}
     */
    @TypeConverter
    @Nullable
    public static LocalDate epochDayToDate(@Nullable Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay);
    }

    /**
     * Stores a list of steps as one JSON array, in order: {@code ["Boil the water.","Add the pasta."]}.
     * JSON escapes commas, quotes and line breaks inside a step, so any text survives.
     *
     * @param steps the steps, or {@code null}
     * @return the JSON array as text, or {@code null}
     * @throws IllegalArgumentException if a step is {@code null}: JSON would store it as {@code null}
     *     and it would read back as the text {@code "null"}
     */
    @TypeConverter
    @Nullable
    public static String stepsToJson(@Nullable List<String> steps) {
        if (steps == null) {
            return null;
        }
        JSONArray array = new JSONArray();
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            if (step == null) {
                throw new IllegalArgumentException("Recipe step " + i + " is null");
            }
            array.put(step);
        }
        return array.toString();
    }

    /**
     * Reads a list of steps back from the JSON array {@link #stepsToJson(List)} wrote.
     *
     * @param json the column's text, or {@code null}
     * @return the steps in their stored order, unmodifiable, or {@code null}
     * @throws IllegalArgumentException if the text is not a JSON array of strings; a corrupt row is
     *     reported where it is read rather than shown as a recipe with no method
     */
    @TypeConverter
    @Nullable
    public static List<String> jsonToSteps(@Nullable String json) {
        if (json == null) {
            return null;
        }
        try {
            JSONArray array = new JSONArray(json);
            List<String> steps = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                Object step = array.get(i);
                if (!(step instanceof String)) {
                    throw new IllegalArgumentException("Recipe step " + i + " is not text: " + json);
                }
                steps.add((String) step);
            }
            return Collections.unmodifiableList(steps);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Recipe steps are not a JSON array: " + json, e);
        }
    }
}
