package com.btk.spm.domain;

/**
 * What a quantity measures: its weight, its volume or a number of pieces.
 *
 * <p>Decision 5: quantities are compared only within one kind, in that kind's canonical unit. A
 * recipe that asks for 2 cups of flour is never satisfied by 500 g of flour, because turning a volume
 * into a mass needs a density for each ingredient, which this app deliberately does not keep.
 */
public enum UnitKind {

    /** Weight, compared in grams. */
    MASS,

    /** Volume, compared in millilitres. */
    VOLUME,

    /** A number of whole items (eggs, onions), compared in pieces. */
    COUNT;

    /**
     * Returns the unit every quantity of this kind is converted to before it is compared: grams,
     * millilitres or pieces. Its {@link Unit#factorToCanonical()} is always 1.
     *
     * <p>This is a method rather than a constructor argument on purpose: {@link Unit} stores its kind
     * in a field, and two enums that store each other's constants in their constructors can see each
     * other half-initialised, depending on which class the JVM loads first.
     *
     * @return the canonical unit of this kind
     */
    public Unit canonicalUnit() {
        return switch (this) {
            case MASS -> Unit.G;
            case VOLUME -> Unit.ML;
            case COUNT -> Unit.PCS;
        };
    }
}
