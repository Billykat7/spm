package com.btk.spm.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.matching.IngredientNormaliser;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Checks the real {@code assets/aliases.json}, read on the JVM as a test resource, against the
 * rules in {@link AliasLoader}'s Javadoc (Issue 18). A target that is not canonical would make the
 * matcher compare a name that the other side can never produce, so it fails here, naming the alias.
 */
public class AliasesJsonTest {

    /** The asset's path on the test classpath; {@code app/build.gradle} adds {@code src/main/assets}. */
    private static final String ASSET = "/" + AliasLoader.ASSET_NAME;

    /** The aliases Issue 18 names, which the table must hold whatever else it gains. */
    private static final Map<String, String> ISSUE_ALIASES = Map.of(
            "cilantro", "coriander",
            "scallion", "spring onion",
            "capsicum", "bell pepper",
            "courgette", "zucchini",
            "aubergine", "eggplant",
            "garbanzo bean", "chickpea",
            "rocket", "arugula",
            "caster sugar", "superfine sugar");

    private static Map<String, String> aliases;
    private static IngredientNormaliser normaliser;

    @BeforeClass
    public static void readTheRealAsset() throws IOException {
        try (InputStream in = AliasesJsonTest.class.getResourceAsStream(ASSET)) {
            assertNotNull(ASSET + " is not on the test classpath; see sourceSets in app/build.gradle", in);
            aliases = AliasLoader.parse(AssetText.readUtf8(in));
        }
        normaliser = new IngredientNormaliser(aliases);
    }

    @Test
    public void theIssuesAliases_areAllThere() {
        for (Map.Entry<String, String> alias : ISSUE_ALIASES.entrySet()) {
            assertEquals(alias.getKey(), alias.getValue(), aliases.get(alias.getKey()));
        }
        assertEquals("coriander", normaliser.normalise("Cilantro"));
        assertEquals("spring onion", normaliser.normalise("Scallions"));
    }

    @Test
    public void chilliInBothSpellings_singularOrPlural_isOneName() {
        // The rules give chillies -> chilli and chilies -> chili; the table's "chili" entry joins the two
        for (String spelling : List.of("chilli", "Chillies", "chili", "chilies")) {
            assertEquals(spelling, "chilli", normaliser.normalise(spelling));
        }
    }

    @Test
    public void everyTarget_isAFixedPointOfTheNormaliser() {
        List<String> notCanonical = new ArrayList<>();
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String target = alias.getValue();
            String again = normaliser.normalise(target);
            if (!again.equals(target)) {
                notCanonical.add("\"" + alias.getKey() + "\" -> \"" + target + "\", which normalises to \""
                        + again + "\"");
            }
        }
        assertTrue("Write each target in its canonical form:\n" + String.join("\n", notCanonical),
                notCanonical.isEmpty());
    }

    @Test
    public void everyAlias_isWrittenAsTheRulesLeaveIt() {
        // The normaliser forgives "Scallions"; the asset should not need forgiving
        IngredientNormaliser rulesOnly = new IngredientNormaliser(Collections.emptyMap());
        for (String alias : aliases.keySet()) {
            assertEquals("Write \"" + alias + "\" lower case and singular", rulesOnly.normalise(alias), alias);
        }
    }
}
