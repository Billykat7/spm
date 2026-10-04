package com.btk.spm.domain.matching;

import com.btk.spm.domain.UnitKind;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An amount in its kind's canonical unit: grams, millilitres or pieces. The only form the matcher
 * compares amounts in (non-negotiable 2), made by {@link UnitConverter#toCanonical}.
 *
 * <p>Immutable, and valid by construction like {@code Quantity}: the amount is a finite number above
 * zero and the kind is never missing.
 *
 * @param amount how much, in grams, millilitres or pieces; finite and greater than zero
 * @param kind   what is measured, never {@code null}
 */
public record CanonicalQuantity(double amount, UnitKind kind) {

    /**
     * How far below the required amount a pantry amount may be and still count as enough. Sums of
     * doubles are not exact ({@code 0.1 + 0.2} is {@code 0.30000000000000004}), so {@code 0.3 g} must
     * not fall short of {@code 0.1 g + 0.2 g}. A millionth of a gram, millilitre or piece is far below
     * anything a kitchen measures, so this never lets a real shortfall through.
     */
    public static final double EPSILON = 1e-6;

    /**
     * Creates a canonical quantity, refusing one that cannot exist in a pantry or a recipe.
     *
     * @throws IllegalArgumentException if {@code amount} is zero, negative, infinite or NaN, or
     *     {@code kind} is {@code null}
     */
    public CanonicalQuantity {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            // !(amount > 0) is also true for NaN, which "amount <= 0" would let through
            throw new IllegalArgumentException(
                    "Canonical amount must be a finite number above 0, was " + amount);
        }
        if (kind == null) {
            throw new IllegalArgumentException("Canonical quantity kind is missing");
        }
    }

    /**
     * Says whether this amount covers {@code required}: the "in at least the required quantity" half
     * of the strict rule (brief §2.3).
     *
     * <p><b>Kinds never cross</b> (decision 5). A mass never covers a volume or a count, and the other
     * way round, so {@code 500 g} of flour is not at least {@code 2 cups} of flour: the answer is
     * {@code false}, a shortfall, not an error. Turning a volume into a mass needs a density for each
     * ingredient (a cup of flour weighs about half what a cup of sugar does), and keeping one is
     * ingredient-by-ingredient knowledge the brief does not ask for. The seed recipes therefore write
     * each ingredient in the kind a pantry would hold it in.
     *
     * <p>Within a kind the comparison allows {@link #EPSILON} of rounding error and nothing more:
     * {@code 200 g} is not at least {@code 250 g}.
     *
     * @param required the amount a recipe needs
     * @return {@code true} if both are the same kind and this amount is at least {@code required}'s
     * @throws NullPointerException if {@code required} is {@code null}
     */
    public boolean isAtLeast(CanonicalQuantity required) {
        Objects.requireNonNull(required, "required");
        return kind == required.kind && amount >= required.amount - EPSILON;
    }

    /**
     * Adds another amount of the same kind, as when two pantry rows hold the same ingredient.
     *
     * @param other the amount to add
     * @return the total, of this kind
     * @throws IllegalArgumentException if {@code other} is of another kind; a mass and a volume are
     *     never added (decision 5)
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public CanonicalQuantity plus(CanonicalQuantity other) {
        Objects.requireNonNull(other, "other");
        if (kind != other.kind) {
            throw new IllegalArgumentException("Cannot add " + kind + " and " + other.kind + ": " + this
                    + " + " + other + " has no meaning without a density (decision 5)");
        }
        return new CanonicalQuantity(amount + other.amount, kind);
    }

    /**
     * Returns the amount as a log line reads it, in the kind's canonical unit: {@code 250 g},
     * {@code 500 ml}, {@code 6 pcs}. Not for screens.
     *
     * @return the amount and the canonical unit's symbol
     */
    @Override
    public String toString() {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() + " "
                + kind.canonicalUnit().symbol();
    }
}
