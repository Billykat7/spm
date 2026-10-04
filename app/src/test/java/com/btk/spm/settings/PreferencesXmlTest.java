package com.btk.spm.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.btk.spm.domain.UnitsSystem;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Holds the Settings screen's XML to {@link PrefKey} and {@link AppPreferences} (Issue 28).
 *
 * <p>{@code res/xml/preferences.xml} is read as plain XML on the JVM, with no emulator. The test fails
 * when a preference's {@code android:key} is not a {@code PrefKey} key string (a typo, or a setting
 * nobody reads), when a {@link PrefKey#userFacing()} key is missing or appears twice, and when an
 * {@code android:defaultValue} is not the default {@link AppPreferences#defaultValue} declares, which
 * is the one every reader falls back to. The seek bar's range and the units list's stored values are
 * checked against the code the same way.
 */
public class PreferencesXmlTest {

    private static final Path RES = Paths.get("src", "main", "res");
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String APP = "http://schemas.android.com/apk/res-auto";

    /** Every element with an {@code android:key}, in the order the screen shows them. */
    private static List<Element> preferences;

    @BeforeClass
    public static void readTheXml() throws Exception {
        Document xml = parse(RES.resolve(Paths.get("xml", "preferences.xml")));
        preferences = new ArrayList<>();
        NodeList all = xml.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            if (element.hasAttributeNS(ANDROID, "key")) {
                preferences.add(element);
            }
        }
    }

    @Test
    public void everyKeyInTheXml_isAPrefKey() {
        List<String> known = Arrays.stream(PrefKey.values()).map(PrefKey::key).collect(Collectors.toList());
        List<String> unknown = keysInXml().stream().filter(key -> !known.contains(key)).collect(Collectors.toList());
        assertTrue("preferences.xml has keys that are not PrefKey key strings: " + unknown, unknown.isEmpty());
    }

    @Test
    public void everyUserFacingPrefKey_isInTheXmlExactlyOnce() {
        List<String> keys = keysInXml();
        for (PrefKey prefKey : PrefKey.values()) {
            long times = keys.stream().filter(prefKey.key()::equals).count();
            if (prefKey.userFacing()) {
                assertEquals(prefKey + " (" + prefKey.key() + ") must be in preferences.xml exactly once", 1, times);
            } else {
                assertEquals(prefKey + " is set elsewhere and must not be on the Settings screen", 0, times);
            }
        }
    }

    @Test
    public void everyDefaultInTheXml_isTheDefaultAppPreferencesReadsBackTo() {
        for (PrefKey prefKey : PrefKey.values()) {
            Element element = byKey().get(prefKey.key());
            if (element == null) {
                continue;
            }
            Object expected = AppPreferences.defaultValue(prefKey);
            String declared = element.hasAttributeNS(ANDROID, "defaultValue")
                    ? element.getAttributeNS(ANDROID, "defaultValue") : null;
            assertEquals(prefKey + " android:defaultValue", expected == null ? null : String.valueOf(expected), declared);
        }
    }

    @Test
    public void theSeekBarsRange_isTheRangeAppPreferencesAccepts() {
        Element threshold = byKey().get(PrefKey.EXPIRY_THRESHOLD_DAYS.key());
        assertNotNull(threshold);
        assertEquals("SeekBarPreference", threshold.getTagName());
        assertEquals(String.valueOf(AppPreferences.MIN_EXPIRY_THRESHOLD_DAYS), threshold.getAttributeNS(APP, "min"));
        assertEquals(String.valueOf(AppPreferences.MAX_EXPIRY_THRESHOLD_DAYS), threshold.getAttributeNS(ANDROID, "max"));
    }

    @Test
    public void theUnitsListStoresUnitsSystemNames_inTheEnumsOrder() throws Exception {
        Element units = byKey().get(PrefKey.UNITS_SYSTEM.key());
        assertNotNull(units);
        assertEquals("@array/units_system_values", units.getAttributeNS(ANDROID, "entryValues"));
        assertEquals("@array/units_system_entries", units.getAttributeNS(ANDROID, "entries"));

        Map<String, List<String>> arrays = stringArrays();
        List<String> names = Arrays.stream(UnitsSystem.values()).map(Enum::name).collect(Collectors.toList());
        assertEquals(names, arrays.get("units_system_values"));
        assertEquals("one label per stored value", names.size(), arrays.get("units_system_entries").size());
    }

    @Test
    public void theTestAlertShipsDisabled_untilIssue29WiresIt() {
        Element testAlert = byKey().get(PrefKey.SEND_TEST_ALERT.key());
        assertNotNull(testAlert);
        assertEquals("false", testAlert.getAttributeNS(ANDROID, "enabled"));
        assertFalse(testAlert.hasAttributeNS(ANDROID, "defaultValue"));
    }

    private static List<String> keysInXml() {
        return preferences.stream().map(e -> e.getAttributeNS(ANDROID, "key")).collect(Collectors.toList());
    }

    private static Map<String, Element> byKey() {
        Map<String, Element> map = new HashMap<>();
        for (Element element : preferences) {
            map.put(element.getAttributeNS(ANDROID, "key"), element);
        }
        return map;
    }

    /** Every {@code <string-array>} in {@code values/arrays.xml}, by name. */
    private static Map<String, List<String>> stringArrays() throws Exception {
        Document xml = parse(RES.resolve(Paths.get("values", "arrays.xml")));
        Map<String, List<String>> arrays = new HashMap<>();
        NodeList found = xml.getElementsByTagName("string-array");
        for (int i = 0; i < found.getLength(); i++) {
            Element array = (Element) found.item(i);
            List<String> items = new ArrayList<>();
            NodeList children = array.getElementsByTagName("item");
            for (int j = 0; j < children.getLength(); j++) {
                items.add(children.item(j).getTextContent().trim());
            }
            arrays.put(array.getAttribute("name"), items);
        }
        return arrays;
    }

    private static Document parse(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file.toFile());
    }
}
