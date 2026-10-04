package com.btk.spm.data.seed;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.btk.spm.domain.matching.IngredientNormaliser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the alias table, {@code assets/aliases.json}, into the map that
 * {@link IngredientNormaliser} is built from (Issue 18).
 *
 * <p>The file is one JSON object of {@code "from": "to"} pairs, such as
 * {@code "cilantro": "coriander"}. An alias says that two names are the same ingredient: another
 * country's word ({@code courgette}, {@code zucchini}) or another spelling ({@code yogurt},
 * {@code yoghurt}). It never widens a name ({@code spaghetti} is not {@code pasta}), because under
 * the strict rule a wrong "can make" is worse than a missed one. JSON has no comments, so the file's
 * rules live here and {@code AliasesJsonTest} checks them on the JVM:
 * <ul>
 *   <li>every key is written the way the normaliser leaves it without aliases: lower case,
 *       singular;</li>
 *   <li>every target is a fixed point of the normaliser, so no alias needs a second lookup;</li>
 *   <li>no key is a seed ingredient name, so the seed's names are never rewritten
 *       ({@code RecipesJsonTest}).</li>
 * </ul>
 *
 * <p>This is the only Android-aware part of name normalisation, and only because {@link #load}
 * opens the asset. {@link #parse} is plain Java with {@code org.json}, so the JVM tests read the
 * real file and the normaliser itself never sees a {@link Context}.
 */
public final class AliasLoader {

    /** The alias table's file name under {@code src/main/assets/}. */
    public static final String ASSET_NAME = "aliases.json";

    private AliasLoader() {
        // Static loader; never instantiated
    }

    /**
     * Reads and parses the alias table. Reads a file, so it runs off the main thread.
     *
     * @param context any context of the app; only its assets are used
     * @return each alias mapped to its target, as written in the file
     * @throws IOException if the asset cannot be read
     * @throws IllegalArgumentException if the asset is malformed; the message names the key
     */
    @WorkerThread
    @NonNull
    public static Map<String, String> load(@NonNull Context context) throws IOException {
        return parse(AssetText.read(context.getAssets(), ASSET_NAME));
    }

    /**
     * Parses the text of an alias table. Checks the shape only: the normaliser tidies the names and
     * refuses a key with two targets.
     *
     * @param json the text of {@code aliases.json}
     * @return each alias mapped to its target, sorted by alias, unmodifiable
     * @throws IllegalArgumentException if the text is not a JSON object, a key is blank, or a value
     *     is not a non-blank string; the message names the key
     */
    @NonNull
    public static Map<String, String> parse(@NonNull String json) {
        JSONObject object;
        try {
            object = new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException(ASSET_NAME + " is not a JSON object: " + e.getMessage(), e);
        }
        Map<String, String> aliases = new TreeMap<>();
        for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
            String from = keys.next();
            Object to = object.opt(from);
            if (from.trim().isEmpty()) {
                throw invalid("a key is blank");
            }
            if (!(to instanceof String) || ((String) to).trim().isEmpty()) {
                throw invalid("\"" + from + "\" does not map to a non-blank string");
            }
            aliases.put(from, (String) to);
        }
        return Collections.unmodifiableMap(aliases);
    }

    private static IllegalArgumentException invalid(String problem) {
        return new IllegalArgumentException(ASSET_NAME + ": " + problem);
    }
}
