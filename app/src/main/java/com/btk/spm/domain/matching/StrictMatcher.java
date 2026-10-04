package com.btk.spm.domain.matching;

import com.btk.spm.domain.ExpiryRules;
import com.btk.spm.domain.MatchStatus;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.UnitKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The one place the strict-matching rule is decided (non-negotiable 1). A {@code ViewModel} calls it,
 * an adapter renders its {@link MatchResult}, and nothing else compares a pantry with a recipe.
 *
 * <p>Plain Java: no Android, no Room, no clock. It holds the normaliser and the converter it is
 * given and nothing else, so one instance can be shared across threads and a test builds one in a
 * line. The caller runs it off the main thread (Issue 23).
 */
public final class StrictMatcher {

    private final IngredientNormaliser normaliser;
    private final UnitConverter converter;

    /**
     * Creates a matcher.
     *
     * @param normaliser turns every ingredient name into the key that is compared (Issue 18)
     * @param converter  turns every quantity into a canonical amount (Issue 19)
     * @throws NullPointerException if either is {@code null}
     */
    public StrictMatcher(IngredientNormaliser normaliser, UnitConverter converter) {
        this.normaliser = Objects.requireNonNull(normaliser, "normaliser");
        this.converter = Objects.requireNonNull(converter, "converter");
    }

    /**
     * Decides whether {@code recipe} can be cooked from {@code pantry} on {@code options.today()}.
     *
     * <p>Brief §2.3: a recipe is suggested only if every single ingredient it requires is present in
     * the pantry in at least the required quantity. If it needs five and the pantry has four, it is
     * not suggested: the result is {@code ALMOST_THERE}, never {@code CAN_MAKE}, with a
     * {@link Shortfall} naming the fifth.
     *
     * <p>Three invariants hold. Names are compared only after
     * {@link IngredientNormaliser#normalise}, so {@code "Tomatoes"} covers {@code "tomato"}. Amounts
     * are compared only as {@link CanonicalQuantity canonical quantities} of one kind, with
     * same-kind pantry rows summed, so {@code 1 kg} covers {@code 250 g} and {@code 500 g} never
     * covers {@code 2 cups}. Expired rows are absent unless {@link MatchOptions#includeExpired()}.
     *
     * @param pantry  every pantry row, expired ones included; the options decide whether they count
     * @param recipe  the recipe to judge
     * @param options the day of the match and whether expired rows count
     * @return {@code CAN_MAKE} with no shortfalls, {@code ALMOST_THERE} with exactly one, or
     *     {@code CANNOT_MAKE} with one per line not covered
     * @throws NullPointerException if an argument is {@code null}
     */
    public MatchResult match(List<PantryEntry> pantry, RecipeSpec recipe, MatchOptions options) {
        return decide(stock(pantry, options), Objects.requireNonNull(recipe, "recipe"));
    }

    /**
     * Judges every recipe against the same pantry. The pantry is normalised, converted and summed
     * once, not once per recipe.
     *
     * @param pantry  every pantry row
     * @param recipes the recipes to judge
     * @param options the day of the match and whether expired rows count
     * @return one result per recipe, in the order of {@code recipes}; unmodifiable
     * @throws NullPointerException if an argument, or a recipe in the list, is {@code null}
     */
    public List<MatchResult> matchAll(List<PantryEntry> pantry, List<RecipeSpec> recipes,
                                      MatchOptions options) {
        Map<String, Map<UnitKind, CanonicalQuantity>> stock = stock(pantry, options);
        List<MatchResult> results = new ArrayList<>(Objects.requireNonNull(recipes, "recipes").size());
        for (RecipeSpec recipe : recipes) {
            results.add(decide(stock, Objects.requireNonNull(recipe, "recipe")));
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * The rule itself: each required line is covered by what the pantry holds under the same name in
     * the same kind, or it is a shortfall. A recipe that lists one ingredient twice needs the sum.
     */
    private MatchResult decide(Map<String, Map<UnitKind, CanonicalQuantity>> stock, RecipeSpec recipe) {
        Map<String, Map<UnitKind, CanonicalQuantity>> needs = needs(recipe);
        List<Shortfall> shortfalls = new ArrayList<>();
        List<IngredientCheck> checks = new ArrayList<>();
        for (RequiredIngredient required : recipe.ingredients()) {
            String name = normaliser.normalise(required.name());
            UnitKind kind = required.quantity().unit().kind();
            Map<UnitKind, CanonicalQuantity> held = stock.get(name);
            CanonicalQuantity have = held == null ? null : held.get(kind);
            boolean covered = have != null && have.isAtLeast(needs.get(name).get(kind));
            // Every line gets a check, so the detail screen can show "have" rows without comparing again
            checks.add(new IngredientCheck(required, have, covered));
            if (!covered) {
                shortfalls.add(new Shortfall(required, have));
            }
        }
        int needCount = recipe.ingredients().size();
        MatchStatus status = MatchStatus.forShortfalls(shortfalls.size());
        return new MatchResult(recipe.id(), status, shortfalls, needCount - shortfalls.size(), needCount, checks);
    }

    /** What the pantry holds: canonical totals by normalised name and kind, expired rows left out. */
    private Map<String, Map<UnitKind, CanonicalQuantity>> stock(List<PantryEntry> pantry,
                                                                MatchOptions options) {
        Objects.requireNonNull(options, "options");
        Map<String, List<Quantity>> rows = new HashMap<>();
        for (PantryEntry entry : Objects.requireNonNull(pantry, "pantry")) {
            // Decision 6: an expired row is absent unless the caller counts expired items
            if (options.includeExpired() || !ExpiryRules.isExpired(entry.expiry(), options.today())) {
                String name = normaliser.normalise(entry.name());
                rows.computeIfAbsent(name, k -> new ArrayList<>()).add(entry.quantity());
            }
        }
        return totals(rows);
    }

    /** What the recipe needs: canonical totals by normalised name and kind. */
    private Map<String, Map<UnitKind, CanonicalQuantity>> needs(RecipeSpec recipe) {
        Map<String, List<Quantity>> lines = new HashMap<>();
        for (RequiredIngredient required : recipe.ingredients()) {
            String name = normaliser.normalise(required.name());
            lines.computeIfAbsent(name, k -> new ArrayList<>()).add(required.quantity());
        }
        return totals(lines);
    }

    /** Sums each name's quantities kind by kind; a mass and a volume are never added (decision 5). */
    private Map<String, Map<UnitKind, CanonicalQuantity>> totals(Map<String, List<Quantity>> byName) {
        Map<String, Map<UnitKind, CanonicalQuantity>> totals = new HashMap<>();
        for (Map.Entry<String, List<Quantity>> name : byName.entrySet()) {
            totals.put(name.getKey(), converter.sumByKind(name.getValue()));
        }
        return totals;
    }
}
