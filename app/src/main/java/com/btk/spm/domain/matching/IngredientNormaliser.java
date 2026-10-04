package com.btk.spm.domain.matching;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns an ingredient name as someone typed it into the one key the matcher compares
 * (non-negotiable 2): {@code "Tomatoes"}, {@code " tomato "} and {@code "TOMATO."} all become
 * {@code "tomato"}, and {@code "Cilantro"} becomes {@code "coriander"}.
 *
 * <p>The brief (§2.3) asks for matching that survives "simple real-world messiness", such as
 * singular and plural names, and says no NLP is needed. So this class is a fixed list of rules, run
 * in this order:
 * <ol>
 *   <li><b>Case and spaces.</b> Lower case with {@link Locale#ROOT}, so a phone set to Turkish
 *       still turns {@code "RICE"} into {@code "rice"}; trimmed; every run of whitespace, a
 *       no-break space included, becomes one space.</li>
 *   <li><b>Punctuation.</b> Anything that is not a letter, a digit or a space becomes a space, except
 *       a hyphen or an apostrophe between two letters or digits: {@code "self-raising flour"} and
 *       {@code "baker's yeast"} keep theirs, {@code "TOMATO."} loses its full stop. A space rather
 *       than nothing, so {@code "salt,pepper"} does not become one word. A curly apostrophe counts as
 *       a straight one.</li>
 *   <li><b>Plurals.</b> The last word is made singular by suffix, longest suffix first:
 *       {@code -ies → -y} (berries, but pies is pie), {@code -oes → -o} (tomatoes); {@code -ches},
 *       {@code -shes}, {@code -sses} and {@code -xes} lose {@code es} (peaches, radishes);
 *       {@code -lves}, {@code -eaves} and {@code -oaves} end in {@code -f} (halves, leaves,
 *       loaves); otherwise a final {@code s} goes (eggs, and olives, not "olif"). Left alone: words
 *       of three letters or fewer, words ending in {@code ss} (watercress), and the words in
 *       {@link #SINGULAR_WORDS_ENDING_IN_S}, which end in {@code s} but are not plurals
 *       (asparagus, hummus). Only the last word changes, because in an English ingredient name the
 *       last word is the thing itself: {@code "spring onions"} is {@code "spring onion"}.</li>
 *   <li><b>Aliases.</b> The whole result is looked up in the alias table and replaced when found:
 *       {@code cilantro → coriander}, {@code courgette → zucchini}. The table's keys and targets go
 *       through steps 1 to 3 when the normaliser is built, so {@code "Scallions"} finds the
 *       {@code scallion} entry. Each target must already be canonical, which
 *       {@code AliasesJsonTest} checks for the shipped table.</li>
 * </ol>
 *
 * <p><b>What it deliberately does not do.</b> No stemming ({@code "sliced"} stays {@code "sliced"}),
 * no fuzzy or edit-distance matching ({@code "tomatoe"} is not {@code "tomato"}), no dictionary and
 * no language model. Each of those can join two different ingredients, and a wrong "can make" is
 * worse than a missed one under the strict rule. Anything the rules get wrong is fixed by an alias
 * or an exception, with a row in the test table.
 *
 * <p>Plain Java with no Android import, so the JVM tests it row by row. The alias table is passed
 * in as a map ({@code data/seed/AliasLoader} reads it from {@code assets/aliases.json}). Immutable
 * once built, so one instance can be shared across threads.
 */
public final class IngredientNormaliser {

    /** Words that end in {@code s} but are already singular, so the plural rules skip them. */
    static final Set<String> SINGULAR_WORDS_ENDING_IN_S = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("asparagus", "couscous", "hummus", "molasses", "oats")));

    /** A word this short is never treated as a plural: {@code "gas"} is not the plural of {@code "ga"}. */
    private static final int SHORTEST_PLURAL = 4;

    /** Whitespace of any kind, including the no-break space a pasted name can carry. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    /** Anything other than a letter, a digit, whitespace, a hyphen or an apostrophe. */
    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{N}\\s\\p{Z}'-]");

    /** A hyphen or an apostrophe that is not between two letters or digits. */
    private static final Pattern LOOSE_JOINER =
            Pattern.compile("(?<![\\p{L}\\p{N}])['-]|['-](?![\\p{L}\\p{N}])");

    private static final char CURLY_APOSTROPHE = '\u2019';

    private final Map<String, String> aliases;

    /**
     * Builds a normaliser over an alias table.
     *
     * @param aliases each name to replace, mapped to the name that replaces it; keys and values are
     *     normalised by steps 1 to 3 here, so the table may be written loosely. Empty for no aliases.
     * @throws NullPointerException if {@code aliases}, or a key or value in it, is {@code null}
     * @throws IllegalArgumentException if a key or value is blank once normalised, or two keys
     *     normalise to the same name but name different targets
     */
    public IngredientNormaliser(Map<String, String> aliases) {
        Objects.requireNonNull(aliases, "aliases");
        Map<String, String> normalised = new HashMap<>();
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String from = withoutAliases(Objects.requireNonNull(alias.getKey(), "alias key"));
            String to = withoutAliases(
                    Objects.requireNonNull(alias.getValue(), "alias target of " + alias.getKey()));
            if (from.isEmpty() || to.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blank alias: \"" + alias.getKey() + "\" -> \"" + alias.getValue() + "\"");
            }
            String earlier = normalised.put(from, to);
            if (earlier != null && !earlier.equals(to)) {
                throw new IllegalArgumentException("Alias \"" + from + "\" names two targets: \"" + earlier
                        + "\" and \"" + to + "\"");
            }
        }
        this.aliases = Collections.unmodifiableMap(normalised);
    }

    /**
     * Returns the canonical form of an ingredient name: the only form the matcher ever compares.
     *
     * @param raw the name as typed or as stored, or {@code null}
     * @return the canonical name; {@code ""} for {@code null} or a name with no letters or digits.
     *     Never throws.
     */
    public String normalise(String raw) {
        String name = withoutAliases(raw);
        String alias = aliases.get(name);
        return alias == null ? name : alias;
    }

    /** Steps 1 to 3: case, spaces, punctuation and the plural of the last word. */
    private static String withoutAliases(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.toLowerCase(Locale.ROOT).replace(CURLY_APOSTROPHE, '\'');
        name = PUNCTUATION.matcher(name).replaceAll(" ");
        name = LOOSE_JOINER.matcher(name).replaceAll(" ");
        name = WHITESPACE.matcher(name).replaceAll(" ").trim();
        if (name.isEmpty()) {
            return name;
        }
        int lastSpace = name.lastIndexOf(' ');
        return name.substring(0, lastSpace + 1) + singularise(name.substring(lastSpace + 1));
    }

    /**
     * Makes one lower-case word singular by its suffix. The longer suffixes are tried first: the
     * generic {@code -s} rule alone would turn "tomatoes" into "tomatoe".
     */
    static String singularise(String word) {
        if (word.length() < SHORTEST_PLURAL || SINGULAR_WORDS_ENDING_IN_S.contains(word)) {
            return word;
        }
        // A word ending in ss is never a simple plural (watercress, lemongrass); -sses is handled below
        if (word.endsWith("ss")) {
            return word;
        }
        // With one letter before it, -ies is -ie plus s: "pies" is pie, not "py"
        if (word.endsWith("ies") && word.length() > SHORTEST_PLURAL) {
            return drop(word, 3) + "y";
        }
        if (word.endsWith("oes")) {
            return drop(word, 2);
        }
        if (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses") || word.endsWith("xes")) {
            return drop(word, 2);
        }
        // Only -lves, -eaves and -oaves come from an -f word (halves, leaves, loaves). Every other
        // -ves in a kitchen is an -ve word plus s, and "-ves to -f" turned olives into "olif"
        if (word.endsWith("lves") || word.endsWith("eaves") || word.endsWith("oaves")) {
            return drop(word, 3) + "f";
        }
        if (word.endsWith("s")) {
            return drop(word, 1);
        }
        return word;
    }

    private static String drop(String word, int letters) {
        return word.substring(0, word.length() - letters);
    }
}
