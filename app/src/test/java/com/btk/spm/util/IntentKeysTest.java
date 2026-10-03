package com.btk.spm.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/** Checks that every extra name in {@link IntentKeys} follows the one convention. */
public class IntentKeysTest {

    @Test
    public void everyExtraIsPrefixedNamedAfterItsConstantAndUnique() throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        int extras = 0;
        for (Field field : IntentKeys.class.getDeclaredFields()) {
            if (!field.getName().startsWith("EXTRA_")) {
                continue;
            }
            extras++;
            int modifiers = field.getModifiers();
            assertTrue(field.getName() + " must be public static final",
                    Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
            String value = (String) field.get(null);
            assertEquals(field.getName(),
                    IntentKeys.PREFIX + field.getName().substring("EXTRA_".length()), value);
            assertTrue("duplicate extra name " + value, values.add(value));
        }
        assertEquals("EXTRA_PANTRY_ITEM_ID, EXTRA_RECIPE_ID and EXTRA_TAB", 3, extras);
    }

    @Test
    public void prefixIsThePackageName() {
        assertEquals("com.btk.spm.extra.", IntentKeys.PREFIX);
    }
}
