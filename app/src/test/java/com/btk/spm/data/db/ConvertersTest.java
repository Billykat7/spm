package com.btk.spm.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.btk.spm.domain.Unit;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Both directions of every {@link Converters} method, on the JVM (Issue 8): what reaches SQLite and
 * what comes back. The real {@code org.json} is on the test classpath, because the copy in
 * {@code android.jar} is a stub.
 */
public class ConvertersTest {

    // Unit <-> TEXT

    @Test
    public void unit_isStoredByEnumName() {
        assertEquals("KG", Converters.unitToName(Unit.KG));
        assertEquals("TBSP", Converters.unitToName(Unit.TBSP));
    }

    @Test
    public void everyUnit_roundTrips() {
        for (Unit unit : Unit.values()) {
            assertEquals(unit, Converters.nameToUnit(Converters.unitToName(unit)));
        }
    }

    @Test
    public void unit_isNotStoredByDisplaySymbol() {
        // The symbol "kg" is presentation; only the constant name is a valid stored value
        assertThrows(IllegalArgumentException.class, () -> Converters.nameToUnit("kg"));
    }

    @Test
    public void unknownUnitName_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> Converters.nameToUnit("OUNCE"));
    }

    @Test
    public void nullUnit_passesThrough() {
        assertNull(Converters.unitToName(null));
        assertNull(Converters.nameToUnit(null));
    }

    // LocalDate <-> INTEGER epoch day

    @Test
    public void date_isStoredAsEpochDay() {
        assertEquals(Long.valueOf(0L), Converters.dateToEpochDay(LocalDate.of(1970, 1, 1)));
        assertEquals(Long.valueOf(20_729L), Converters.dateToEpochDay(LocalDate.of(2026, 10, 3)));
    }

    @Test
    public void dateBefore1970_isStoredAsNegativeEpochDayAndRoundTrips() {
        LocalDate date = LocalDate.of(1969, 12, 31);
        assertEquals(Long.valueOf(-1L), Converters.dateToEpochDay(date));
        assertEquals(date, Converters.epochDayToDate(-1L));
        LocalDate older = LocalDate.of(1901, 2, 28);
        assertEquals(older, Converters.epochDayToDate(Converters.dateToEpochDay(older)));
    }

    @Test
    public void dates_roundTripInBothDirections() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        assertEquals(leapDay, Converters.epochDayToDate(Converters.dateToEpochDay(leapDay)));
        assertEquals(Long.valueOf(20_729L), Converters.dateToEpochDay(Converters.epochDayToDate(20_729L)));
    }

    @Test
    public void laterDate_hasLargerEpochDay() {
        // What lets SQLite order expiry_date as a plain INTEGER
        long earlier = Converters.dateToEpochDay(LocalDate.of(2026, 12, 31));
        long later = Converters.dateToEpochDay(LocalDate.of(2027, 1, 1));
        assertEquals(1L, later - earlier);
    }

    @Test
    public void nullDate_passesThrough() {
        // A null expiry date means the item never expires (decision 6)
        assertNull(Converters.dateToEpochDay(null));
        assertNull(Converters.epochDayToDate(null));
    }

    // List<String> <-> TEXT JSON array

    @Test
    public void steps_areStoredAsOneJsonArray() {
        assertEquals("[\"Boil the water.\",\"Add the pasta.\"]",
                Converters.stepsToJson(Arrays.asList("Boil the water.", "Add the pasta.")));
    }

    @Test
    public void awkwardSteps_roundTripInOrder() {
        List<String> steps = Arrays.asList(
                "Whisk 2 eggs, salt and pepper.",
                "",
                "Say \"done\" when it sets, not before.",
                "Line one\nline two",
                "Back\\slash, tab\there and 'single' quotes",
                "Crème fraîche, 200 g");
        assertEquals(steps, Converters.jsonToSteps(Converters.stepsToJson(steps)));
    }

    @Test
    public void jsonArray_roundTripsUnchanged() {
        String json = "[\"Chop the onion.\",\"\",\"Fry it, stirring.\"]";
        assertEquals(json, Converters.stepsToJson(Converters.jsonToSteps(json)));
    }

    @Test
    public void emptySteps_roundTrip() {
        assertEquals("[]", Converters.stepsToJson(Collections.emptyList()));
        assertEquals(Collections.emptyList(), Converters.jsonToSteps("[]"));
    }

    @Test
    public void readSteps_areUnmodifiable() {
        List<String> steps = Converters.jsonToSteps("[\"Serve.\"]");
        assertThrows(UnsupportedOperationException.class, () -> steps.add("Eat."));
    }

    @Test
    public void nullStepInsideTheList_isRefused() {
        List<String> steps = new ArrayList<>(Arrays.asList("Serve.", null));
        assertThrows(IllegalArgumentException.class, () -> Converters.stepsToJson(steps));
    }

    @Test
    public void textThatIsNotAJsonArray_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> Converters.jsonToSteps("Boil.\nServe."));
        assertThrows(IllegalArgumentException.class, () -> Converters.jsonToSteps("{\"step\":\"Boil.\"}"));
    }

    @Test
    public void nonTextStep_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> Converters.jsonToSteps("[\"Boil.\",42]"));
        assertThrows(IllegalArgumentException.class, () -> Converters.jsonToSteps("[null]"));
    }

    @Test
    public void nullSteps_passThrough() {
        assertNull(Converters.stepsToJson(null));
        assertNull(Converters.jsonToSteps(null));
    }
}
