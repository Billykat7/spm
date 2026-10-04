package com.btk.spm.domain.matching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What {@link IngredientNormaliser} promises beyond the row table (Issue 18): how it reads an alias
 * table, what it refuses to build from, and that the phone's locale cannot change a name.
 */
public class IngredientNormaliserContractTest {

    @Test
    public void aTurkishPhone_stillLowerCasesRice() {
        // With the default locale, Turkish lower-cases I to a dotless ı: "RICE" would be "rıce"
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals("rice", new IngredientNormaliser(Collections.emptyMap()).normalise("RICE"));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    public void anEmptyTable_skipsOnlyTheAliasStep() {
        assertEquals("cilantro", new IngredientNormaliser(Collections.emptyMap()).normalise("Cilantros."));
    }

    @Test
    public void aliasKeysAndTargets_areTidiedWhenBuilt() {
        IngredientNormaliser normaliser = new IngredientNormaliser(Map.of(" Garbanzo  Beans.", "Chickpeas"));

        assertEquals("chickpea", normaliser.normalise("garbanzo bean"));
    }

    @Test
    public void twoSpellingsOfOneAlias_withTheSameTarget_areAccepted() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("scallion", "spring onion");
        aliases.put("Scallions", "spring onion");

        assertEquals("spring onion", new IngredientNormaliser(aliases).normalise("scallions"));
    }

    @Test
    public void twoTargetsForOneAlias_areRefused() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("scallion", "spring onion");
        aliases.put("scallions", "green onion");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new IngredientNormaliser(aliases));
        assertTrue(e.getMessage(), e.getMessage().contains("\"scallion\""));
    }

    @Test
    public void anAliasThatNormalisesToNothing_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> new IngredientNormaliser(Map.of("...", "salt")));
        assertThrows(IllegalArgumentException.class, () -> new IngredientNormaliser(Map.of("salt", " ")));
    }

    @Test
    public void aMissingTable_isRefused() {
        assertThrows(NullPointerException.class, () -> new IngredientNormaliser(null));
    }

    @Test
    public void anAliasIsAppliedOnce_soAChainIsNotFollowed() {
        // Why every target in aliases.json must be a fixed point: green onion stops at scallion
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("green onion", "scallion");
        aliases.put("scallion", "spring onion");

        assertEquals("scallion", new IngredientNormaliser(aliases).normalise("green onions"));
    }

    @Test
    public void changingTheTableAfterwards_changesNothing() {
        Map<String, String> aliases = new HashMap<>(Map.of("cilantro", "coriander"));
        IngredientNormaliser normaliser = new IngredientNormaliser(aliases);

        aliases.put("cilantro", "parsley");
        aliases.put("rocket", "arugula");

        assertEquals("coriander", normaliser.normalise("cilantro"));
        assertEquals("rocket", normaliser.normalise("rocket"));
    }
}
