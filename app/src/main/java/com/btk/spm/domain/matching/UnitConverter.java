package com.btk.spm.domain.matching;

import com.btk.spm.domain.DisplayQuantity;
import com.btk.spm.domain.DisplayUnit;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.UnitKind;
import com.btk.spm.domain.UnitsSystem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns quantities into canonical amounts before they are compared: {@code 1 kg} is {@code 1000 g},
 * {@code 2 cups} is {@code 500 ml}, {@code 6 pcs} is {@code 6 pcs} (decision 5).
 *
 * <p>Every {@link Unit} belongs to one {@link UnitKind} and carries a fixed factor to that kind's
 * canonical unit; this class does the multiplication and the addition, nothing more. <b>Kinds never
 * cross</b>: there is no density table, so a volume is never turned into a mass or the other way
 * round, and {@code 500 g} of flour does not cover a recipe's {@code 2 cups}. Converting between them
 * would need a density for every ingredient, which is ingredient knowledge the brief does not ask
 * for; the report's reflection names it as a known limitation.
 *
 * <p>It also chooses how an amount is <em>shown</em> under the units preference (Issue 28):
 * {@code 1500 g} as {@code 1.5 kg}, or as {@code 52.9 oz} in imperial. That is display only. It returns
 * a {@link DisplayQuantity}, which nothing that matches accepts, so the preference can change the
 * text on screen and never a match.
 *
 * <p>Plain Java with no Android import and no state. It is an instance rather than static methods so
 * the matcher (Issue 20) receives it in its constructor and a test can hand it a stand-in.
 */
public class UnitConverter {

    /** A metric amount of this many canonical units or more is shown in kilograms or litres. */
    static final double LARGER_METRIC_UNIT_FROM = 1000;

    /**
     * Imperial amounts are rounded to this many significant figures, {@code 52.9 oz} rather than
     * {@code 52.91094 oz}: an ounce figure is an approximation of the metric amount anyway.
     */
    static final MathContext IMPERIAL_PRECISION = new MathContext(3, RoundingMode.HALF_UP);

    /**
     * Converts a quantity to its kind's canonical unit.
     *
     * @param quantity the quantity as stored or as a recipe states it
     * @return the same amount in grams, millilitres or pieces
     * @throws NullPointerException if {@code quantity} is {@code null}
     */
    public CanonicalQuantity toCanonical(Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        Unit unit = quantity.unit();
        return new CanonicalQuantity(quantity.amount() * unit.factorToCanonical(), unit.kind());
    }

    /**
     * Says whether two units measure the same thing, and so whether their amounts can be compared or
     * added at all: {@code KG} and {@code G} can, {@code G} and {@code CUP} cannot.
     *
     * @param a one unit
     * @param b the other unit
     * @return {@code true} if both units have the same {@link UnitKind}
     * @throws NullPointerException if either unit is {@code null}
     */
    public boolean sameKind(Unit a, Unit b) {
        return Objects.requireNonNull(a, "a").kind() == Objects.requireNonNull(b, "b").kind();
    }

    /**
     * Adds quantities of one ingredient that are all the same kind, as when the pantry holds two bags
     * of flour: {@code [200 g, 0.1 kg]} is {@code 300 g}.
     *
     * <p>Rows of different kinds are refused, never added: {@code [200 g, 1 cup]} throws, naming both
     * kinds. When a pantry may hold one ingredient in two kinds, use {@link #sumByKind}, which keeps
     * them apart.
     *
     * @param quantities the rows to add; at least one
     * @return their total in the kind's canonical unit
     * @throws IllegalArgumentException if {@code quantities} is empty or holds more than one kind; the
     *     message names the kinds and the rows
     * @throws NullPointerException if {@code quantities} or a row in it is {@code null}
     */
    public CanonicalQuantity sum(List<Quantity> quantities) {
        Map<UnitKind, CanonicalQuantity> byKind = sumByKind(quantities);
        if (byKind.isEmpty()) {
            throw new IllegalArgumentException("Nothing to add: the list of quantities is empty");
        }
        if (byKind.size() > 1) {
            throw new IllegalArgumentException("Cannot add " + byKind.keySet() + " into one amount: "
                    + quantities + "; a mass, a volume and a count are never added (decision 5)");
        }
        return byKind.values().iterator().next();
    }

    /**
     * Adds quantities of one ingredient kind by kind, for a pantry that may hold the same ingredient
     * in two kinds ({@code 500 g} of rice and {@code 2 cups} of rice). Each kind gets its own total;
     * none is ever added to another.
     *
     * @param quantities the rows to add; may be empty
     * @return one canonical total per kind present, in {@link UnitKind} order; empty for an empty
     *     list; unmodifiable
     * @throws NullPointerException if {@code quantities} or a row in it is {@code null}
     */
    public Map<UnitKind, CanonicalQuantity> sumByKind(List<Quantity> quantities) {
        Objects.requireNonNull(quantities, "quantities");
        Map<UnitKind, CanonicalQuantity> totals = new EnumMap<>(UnitKind.class);
        for (Quantity quantity : quantities) {
            CanonicalQuantity canonical = toCanonical(quantity);
            totals.merge(canonical.kind(), canonical, CanonicalQuantity::plus);
        }
        return Collections.unmodifiableMap(totals);
    }

    /**
     * Chooses how a canonical amount is shown under the units preference. Display only: the result
     * may be rounded and is never compared (decision 5).
     * <ul>
     *   <li>{@link UnitsSystem#METRIC}: grams below 1000 and kilograms from 1000
     *       ({@code 1500 g} is {@code 1.5 kg}); millilitres below 1000 and litres from 1000
     *       ({@code 750 ml} stays {@code 750 ml}); exact.</li>
     *   <li>{@link UnitsSystem#IMPERIAL}: mass in ounces and volume in US fluid ounces
     *       ({@code 1500 g} is {@code 52.9 oz}), rounded to three significant figures.</li>
     *   <li>Pieces stay pieces in both.</li>
     * </ul>
     *
     * @param quantity the canonical amount
     * @param system   the user's preference
     * @return the amount to show and its unit
     * @throws NullPointerException if either argument is {@code null}
     */
    public DisplayQuantity toPreferredDisplay(CanonicalQuantity quantity, UnitsSystem system) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(system, "system");
        DisplayUnit unit = preferredUnit(quantity, system);
        double amount = quantity.amount() / unit.factorToCanonical();
        if (system == UnitsSystem.IMPERIAL && unit.kind() != UnitKind.COUNT) {
            amount = BigDecimal.valueOf(amount).round(IMPERIAL_PRECISION).doubleValue();
        }
        return new DisplayQuantity(amount, unit);
    }

    /**
     * Chooses how a stored or recipe quantity is shown under the units preference. Spoons and cups are
     * kept as the recipe wrote them in both systems ({@code 3 cups} stays {@code 3 cups}), because
     * that is how a cook measures them; every other unit goes through
     * {@link #toPreferredDisplay(CanonicalQuantity, UnitsSystem)}.
     *
     * @param quantity the quantity as stored or as a recipe states it
     * @param system   the user's preference
     * @return the amount to show and its unit
     * @throws NullPointerException if either argument is {@code null}
     */
    public DisplayQuantity toPreferredDisplay(Quantity quantity, UnitsSystem system) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(system, "system");
        return switch (quantity.unit()) {
            // A cook measures these with the spoon or cup itself, so they are not re-scaled
            case TSP, TBSP, CUP -> new DisplayQuantity(quantity.amount(), DisplayUnit.of(quantity.unit()));
            case G, KG, ML, L, PCS -> toPreferredDisplay(toCanonical(quantity), system);
        };
    }

    private static DisplayUnit preferredUnit(CanonicalQuantity quantity, UnitsSystem system) {
        boolean imperial = system == UnitsSystem.IMPERIAL;
        boolean large = quantity.amount() >= LARGER_METRIC_UNIT_FROM;
        return switch (quantity.kind()) {
            case MASS -> imperial ? DisplayUnit.OZ : (large ? DisplayUnit.KG : DisplayUnit.G);
            case VOLUME -> imperial ? DisplayUnit.FL_OZ : (large ? DisplayUnit.L : DisplayUnit.ML);
            case COUNT -> DisplayUnit.PCS;
        };
    }
}
