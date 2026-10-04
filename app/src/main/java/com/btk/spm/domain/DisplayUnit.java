package com.btk.spm.domain;

import androidx.annotation.StringRes;

import com.btk.spm.R;

/**
 * A unit a quantity can be <em>shown</em> in: every {@link Unit}, plus the ounce and the fluid ounce
 * of the imperial preference (Issue 28).
 *
 * <p>A separate enum, not two more {@link Unit} constants, on purpose. {@code Unit} is what is stored,
 * what the add form offers and what the matcher converts; an ounce in it would let a pantry row be
 * saved in ounces and matched through a rounded factor. Here the ounces can only be displayed: no
 * method that stores or matches takes a {@code DisplayUnit} (decision 5).
 *
 * <p>The ounce is the international avoirdupois ounce, 28.349523125 g; the fluid ounce is the US
 * fluid ounce, 29.5735295625 ml. Both are exact definitions, so the only rounding is in what is shown.
 */
public enum DisplayUnit {

    /** Grams. */
    G(Unit.G),
    /** Kilograms. */
    KG(Unit.KG),
    /** Millilitres. */
    ML(Unit.ML),
    /** Litres. */
    L(Unit.L),
    /** Teaspoons, kept as the recipe wrote them. */
    TSP(Unit.TSP),
    /** Tablespoons, kept as the recipe wrote them. */
    TBSP(Unit.TBSP),
    /** Cups, kept as the recipe wrote them. */
    CUP(Unit.CUP),
    /** Pieces. */
    PCS(Unit.PCS),
    /** Ounces, for display under {@link UnitsSystem#IMPERIAL} only. */
    OZ(UnitKind.MASS, 28.349523125, "oz", R.string.unit_oz),
    /** US fluid ounces, for display under {@link UnitsSystem#IMPERIAL} only. */
    FL_OZ(UnitKind.VOLUME, 29.5735295625, "fl oz", R.string.unit_fl_oz);

    private final UnitKind kind;
    private final double factorToCanonical;
    private final String symbol;
    @StringRes
    private final int symbolRes;

    DisplayUnit(Unit unit) {
        this(unit.kind(), unit.factorToCanonical(), unit.symbol(), unit.symbolRes());
    }

    DisplayUnit(UnitKind kind, double factorToCanonical, String symbol, @StringRes int symbolRes) {
        this.kind = kind;
        this.factorToCanonical = factorToCanonical;
        this.symbol = symbol;
        this.symbolRes = symbolRes;
    }

    /**
     * Returns the display unit for a stored unit: {@code Unit.KG} is shown as {@link #KG}.
     *
     * @param unit a stored unit
     * @return the display unit with the same name, kind and factor
     */
    public static DisplayUnit of(Unit unit) {
        return valueOf(unit.name());
    }

    /**
     * Returns what this unit measures.
     *
     * @return the kind, never {@code null}
     */
    public UnitKind kind() {
        return kind;
    }

    /**
     * Returns how many of the kind's canonical unit one of this unit is: 1000 for {@link #KG},
     * 28.349523125 for {@link #OZ}.
     *
     * @return the factor, always positive
     */
    public double factorToCanonical() {
        return factorToCanonical;
    }

    /**
     * Returns the symbol for logs and tests: {@code "kg"}, {@code "fl oz"}. Screens show
     * {@link #symbolRes()}.
     *
     * @return the symbol, never {@code null}
     */
    public String symbol() {
        return symbol;
    }

    /**
     * Returns the string resource that displays this unit on screen.
     *
     * @return a {@code R.string.unit_*} id
     */
    @StringRes
    public int symbolRes() {
        return symbolRes;
    }
}
