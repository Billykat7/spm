package com.btk.spm.data.seed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.btk.spm.domain.matching.IngredientNormaliser;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;

/**
 * {@link AliasLoader#load} on a device, with the asset from the APK and Android's own
 * {@code org.json} (Issue 18). The JVM tests read the same file with the reference {@code org.json};
 * this proves the app reads it the same way.
 */
@RunWith(AndroidJUnit4.class)
public class AliasLoaderAssetTest {

    @Test
    public void load_readsTheShippedTable_intoAWorkingNormaliser() throws IOException {
        Map<String, String> aliases = AliasLoader.load(ApplicationProvider.getApplicationContext());

        assertTrue("aliases.json read as empty", aliases.size() >= 8);
        assertEquals("coriander", aliases.get("cilantro"));
        IngredientNormaliser normaliser = new IngredientNormaliser(aliases);
        assertEquals("coriander", normaliser.normalise("Cilantro"));
        assertEquals("spring onion", normaliser.normalise("Scallions"));
        assertEquals("tomato", normaliser.normalise("TOMATOES."));
    }
}
