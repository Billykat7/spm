/**
 * Pure Java shared by the engine and the screens: the {@code Unit}, {@code UnitKind} and
 * {@code MatchStatus} enums, {@code Quantity}, {@code ExpiryRules} (decision 6 as one pure function),
 * and the display-only {@code UnitsSystem}, {@code DisplayUnit} and {@code DisplayQuantity} for the
 * units preference. Nothing under this package imports {@code android.*} or {@code androidx.*} except
 * the {@code androidx.annotation.StringRes} annotation, so the engine and the validators run on the
 * JVM in milliseconds ({@code ConventionsTest} enforces it, Issue 6).
 */
package com.btk.spm.domain;
