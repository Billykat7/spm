package com.btk.spm.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

/**
 * {@link AliasLoader#parse} on small documents, on the JVM (Issue 18): a valid table reads back in
 * full and sorted, and every malformed one is refused with a message that names the key.
 */
public class AliasLoaderTest {

    @Test
    public void aValidTable_parsesInFull_sortedByAlias() {
        Map<String, String> aliases =
                AliasLoader.parse("{\"scallion\": \"spring onion\", \"cilantro\": \"coriander\"}");

        assertEquals(Map.of("scallion", "spring onion", "cilantro", "coriander"), aliases);
        assertEquals(Arrays.asList("cilantro", "scallion"), Arrays.asList(aliases.keySet().toArray()));
    }

    @Test
    public void anEmptyObject_isAnEmptyTable() {
        assertTrue(AliasLoader.parse("{}").isEmpty());
    }

    @Test
    public void theTable_cannotBeChanged() {
        Map<String, String> aliases = AliasLoader.parse("{\"cilantro\": \"coriander\"}");

        assertThrows(UnsupportedOperationException.class, () -> aliases.put("rocket", "arugula"));
    }

    @Test
    public void anArray_isRefused() {
        assertRefused("[\"cilantro\", \"coriander\"]", "not a JSON object");
    }

    @Test
    public void aNumberAsTarget_isRefused() {
        assertRefused("{\"cilantro\": 4}", "\"cilantro\"");
    }

    @Test
    public void aNullTarget_isRefused() {
        assertRefused("{\"cilantro\": null}", "\"cilantro\"");
    }

    @Test
    public void aBlankTarget_isRefused() {
        assertRefused("{\"cilantro\": \"  \"}", "\"cilantro\"");
    }

    @Test
    public void aBlankAlias_isRefused() {
        assertRefused("{\" \": \"coriander\"}", "a key is blank");
    }

    private static void assertRefused(String json, String expectedInMessage) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AliasLoader.parse(json));
        assertTrue(e.getMessage(), e.getMessage().startsWith(AliasLoader.ASSET_NAME));
        assertTrue(e.getMessage(), e.getMessage().contains(expectedInMessage));
    }
}
