package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.UnitKind;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.List;

/**
 * {@link CanonicalQuantity} on the JVM (Issue 19): "at least" within a kind, with rounding error
 * forgiven and nothing else, and never across kinds (decision 5).
 */
@RunWith(Enclosed.class)
public class CanonicalQuantityTest {

    private static final UnitKind MASS = UnitKind.MASS;
    private static final UnitKind VOLUME = UnitKind.VOLUME;
    private static final UnitKind COUNT = UnitKind.COUNT;

    /** One row per case of {@code have.isAtLeast(need)}. */
    @RunWith(Parameterized.class)
    public static class IsAtLeast {

        @Parameters(name = "{index}: {0}: {1} {2} at least {3} {4} is {5}")
        public static List<Object[]> rows() {
            return Arrays.asList(new Object[][]{
                {"more", 1000.0, MASS, 250.0, MASS, true},
                {"exactly enough", 250.0, MASS, 250.0, MASS, true},
                {"short", 200.0, MASS, 250.0, MASS, false},
                {"short by one gram", 249.0, MASS, 250.0, MASS, false},
                {"short by a thousandth", 249.999, MASS, 250.0, MASS, false},
                {"rounding error forgiven", 0.3, MASS, 0.1 + 0.2, MASS, true},
                {"rounding error the other way", 0.1 + 0.2, MASS, 0.3, MASS, true},
                {"volume", 500.0, VOLUME, 500.0, VOLUME, true},
                {"count short by one", 5.0, COUNT, 6.0, COUNT, false},
                {"count", 6.0, COUNT, 6.0, COUNT, true},
                {"mass never covers volume", 500.0, MASS, 500.0, VOLUME, false},
                {"much mass never covers a little volume", 100000.0, MASS, 1.0, VOLUME, false},
                {"volume never covers mass", 1000.0, VOLUME, 1.0, MASS, false},
                {"mass never covers a count", 1000.0, MASS, 1.0, COUNT, false},
                {"a count never covers volume", 12.0, COUNT, 1.0, VOLUME, false},
            });
        }

        @Parameter(0) public String rule;
        @Parameter(1) public double have;
        @Parameter(2) public UnitKind haveKind;
        @Parameter(3) public double need;
        @Parameter(4) public UnitKind needKind;
        @Parameter(5) public boolean expected;

        @Test
        public void isAtLeast() {
            CanonicalQuantity required = new CanonicalQuantity(need, needKind);

            assertEquals(expected, new CanonicalQuantity(have, haveKind).isAtLeast(required));
        }
    }

    /** Construction, addition and the log form. */
    public static class Contract {

        @Test
        public void zeroNegativeInfiniteAndNaN_areRefused() {
            for (double amount : new double[]{0, -1, Double.POSITIVE_INFINITY, Double.NaN}) {
                assertThrows(String.valueOf(amount), IllegalArgumentException.class,
                        () -> new CanonicalQuantity(amount, MASS));
            }
        }

        @Test
        public void aMissingKind_isRefused() {
            assertThrows(IllegalArgumentException.class, () -> new CanonicalQuantity(1, null));
        }

        @Test
        public void aMissingRequirement_isRefused() {
            assertThrows(NullPointerException.class, () -> new CanonicalQuantity(1, MASS).isAtLeast(null));
        }

        @Test
        public void plus_addsTheSameKind() {
            assertEquals(new CanonicalQuantity(300, MASS),
                    new CanonicalQuantity(200, MASS).plus(new CanonicalQuantity(100, MASS)));
        }

        @Test
        public void plus_refusesAnotherKind_namingBoth() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new CanonicalQuantity(200, MASS).plus(new CanonicalQuantity(250, VOLUME)));
            assertTrue(e.getMessage(), e.getMessage().contains("MASS") && e.getMessage().contains("VOLUME"));
        }

        @Test
        public void toString_isTheAmountInTheCanonicalUnit() {
            assertEquals("250 g", new CanonicalQuantity(250, MASS).toString());
            assertEquals("1.5 ml", new CanonicalQuantity(1.5, VOLUME).toString());
            assertEquals("6 pcs", new CanonicalQuantity(6, COUNT).toString());
        }

        @Test
        public void twoMillionthsShort_isStillShort() {
            // The tolerance is a millionth; anything beyond it is a real shortfall
            assertFalse(new CanonicalQuantity(250 - 2e-6, MASS).isAtLeast(new CanonicalQuantity(250, MASS)));
        }
    }
}
