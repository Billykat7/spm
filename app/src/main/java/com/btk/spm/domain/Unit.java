package com.btk.spm.domain;

import androidx.annotation.StringRes;

import com.btk.spm.R;

import java.util.Locale;

/**
 * Every unit a pantry item or a recipe ingredient can be measured in.
 *
 * <p>Each unit carries what the rest of the app needs to know about it, so no other class keeps a
 * table of units or compares a unit as text:
 * <ul>
 *   <li>its {@link UnitKind}, so the matcher never compares a mass with a volume (decision 5);</li>
 *   <li>its factor to the kind's canonical unit ({@code KG} is 1000 g, {@code CUP} is 250 ml), which
 *       the converter (Issue 19) multiplies by;</li>
 *   <li>its machine symbol ({@code "kg"}), the form the seeded recipes are written in, read back by
 *       {@link #fromSymbol(String)};</li>
 *   <li>its display symbol, a string resource, so the text on screen can change without touching
 *       the data.</li>
 * </ul>
 *
 * <p>The database stores {@link #name()} (Issue 8), never the symbol: the constant is the identity,
 * the symbols are presentation.
 */
public enum Unit {

    /** Grams: the canonical mass unit. */
    G(UnitKind.MASS, 1, "g", R.string.unit_g),
    /** Kilograms: 1000 g. */
    KG(UnitKind.MASS, 1000, "kg", R.string.unit_kg),
    /** Millilitres: the canonical volume unit. */
    ML(UnitKind.VOLUME, 1, "ml", R.string.unit_ml),
    /** Litres: 1000 ml. */
    L(UnitKind.VOLUME, 1000, "l", R.string.unit_l),
    /** Teaspoons: 5 ml. */
    TSP(UnitKind.VOLUME, 5, "tsp", R.string.unit_tsp),
    /** Tablespoons: 15 ml. */
    TBSP(UnitKind.VOLUME, 15, "tbsp", R.string.unit_tbsp),
    /** Cups: 250 ml, the metric cup. */
    CUP(UnitKind.VOLUME, 250, "cup", R.string.unit_cup),
    /** Pieces: the canonical count unit. */
    PCS(UnitKind.COUNT, 1, "pcs", R.string.unit_pcs);

    private final UnitKind kind;
    private final double factorToCanonical;
    private final String symbol;
    @StringRes
    private final int symbolRes;

    Unit(UnitKind kind, double factorToCanonical, String symbol, @StringRes int symbolRes) {
        this.kind = kind;
        this.factorToCanonical = factorToCanonical;
        this.symbol = symbol;
        this.symbolRes = symbolRes;
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
     * Returns how many of the kind's canonical unit one of this unit is: 1000 for {@code KG}, 5 for
     * {@code TSP}, 1 for the canonical units themselves.
     *
     * @return the factor, always positive
     */
    public double factorToCanonical() {
        return factorToCanonical;
    }

    /**
     * Returns the machine symbol, lower case, as the seeded recipes write it: {@code "g"},
     * {@code "tbsp"}, {@code "pcs"}. For parsing and logs; screens show {@link #symbolRes()}.
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

    /**
     * Parses a machine symbol into its unit, ignoring surrounding spaces and letter case, so the
     * seed's {@code "kg"} and {@code " KG "} both give {@link #KG}. Anything else is refused rather
     * than guessed, so a typo in the seed fails when it is read, not later in the matcher.
     *
     * @param symbol a symbol such as {@code "tbsp"}
     * @return the unit with that symbol
     * @throws IllegalArgumentException if {@code symbol} is {@code null} or names no unit; the
     *     message lists the symbols that are accepted
     */
    public static Unit fromSymbol(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Unit symbol is missing; expected one of " + symbols());
        }
        String wanted = symbol.trim().toLowerCase(Locale.ROOT);
        for (Unit unit : values()) {
            if (unit.symbol.equals(wanted)) {
                return unit;
            }
        }
        throw new IllegalArgumentException(
                "Unknown unit symbol \"" + symbol + "\"; expected one of " + symbols());
    }

    private static String symbols() {
        StringBuilder all = new StringBuilder();
        for (Unit unit : values()) {
            if (all.length() > 0) {
                all.append(", ");
            }
            all.append(unit.symbol);
        }
        return all.toString();
    }

    /**
     * Returns the machine symbol, so a log line reads {@code 250 g} rather than {@code 250 G}.
     *
     * @return {@link #symbol()}
     */
    @Override
    public String toString() {
        return symbol;
    }
}
