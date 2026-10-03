package com.btk.spm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Makes non-negotiable 7 enforceable: fixed sets are enums and keys are constants (Issue 6).
 *
 * <p>A typed {@code "kg"} or {@code "recipe_id"} compiles, runs and then fails quietly, the day one
 * side of a comparison or an Intent spells it differently. This test reads every Java file under
 * {@code src/main/java} on the JVM and fails, naming the file and line, when the code:
 * <ul>
 *   <li>passes a literal key to an Intent extra: {@code putExtra("recipe_id", id)}, use
 *       {@code IntentKeys};</li>
 *   <li>passes a literal key to {@code SharedPreferences} or {@code findPreference}, use
 *       {@code PrefKey};</li>
 *   <li>assigns, sets or compares a string literal as a unit, kind, status or extra:
 *       {@code item.unit = "kg"}, {@code status.equals("CAN_MAKE")}, use the enum;</li>
 *   <li>imports Android into {@code domain/}, other than {@code androidx.annotation.StringRes}, which
 *       would stop the engine and the validators running on the JVM.</li>
 * </ul>
 *
 * <p>Comments and the contents of string literals are blanked before matching, keeping the quote
 * marks and the line breaks, so a Javadoc example or a log message can neither trigger nor hide an
 * offence. {@link #rulesCatchTheKnownViolations()} and {@link #rulesLeaveLegitimateCodeAlone()} test
 * the patterns themselves, so a rule cannot silently stop matching.
 */
public class ConventionsTest {

    /** Main sources, relative to the module directory Gradle runs unit tests from. */
    private static final Path MAIN_JAVA = Paths.get("src", "main", "java");

    /** The one package that must stay free of Android (non-negotiable 5 and the JVM test strategy). */
    private static final Path DOMAIN = Paths.get("com", "btk", "spm", "domain");

    /** {@code putExtra("…")}, {@code getLongExtra("…", …)}, {@code hasExtra("…")}, {@code removeExtra("…")}. */
    static final Pattern LITERAL_EXTRA_KEY =
            Pattern.compile("\\.(putExtra|get\\w*Extra|hasExtra|removeExtra)\\s*\\(\\s*\"");

    /**
     * A typed get or put on a receiver named like preferences ({@code prefs}, {@code preferences},
     * {@code editor}, {@code edit()}), or {@code findPreference("…")}. Keyed on the receiver because
     * {@code JSONObject} has the same getters, and the seed parser reads JSON keys legitimately.
     */
    static final Pattern LITERAL_PREFERENCE_KEY = Pattern.compile(
            "(?i:\\b\\w*(prefs|preferences|editor)\\b|\\.edit\\(\\))\\s*\\.\\s*"
                    + "(get|put)(Boolean|Int|Long|Float|String|StringSet)\\s*\\(\\s*\""
                    + "|\\bfindPreference\\s*\\(\\s*\"");

    /**
     * A string literal assigned to, set on or compared with a name that ends in unit, kind or status,
     * or one that contains extra.
     */
    static final Pattern LITERAL_ENUM_VALUE = Pattern.compile("(?i)"
            // item.unit = "kg"     STATUS = "CAN_MAKE"     extraName = "id"
            + "\\b(\\w*(unit|kind|status)|\\w*extra\\w*)\\s*=\\s*\""
            // row.setUnit("kg")    setStatus("CAN_MAKE")
            + "|\\.set\\w*(unit|kind|status)\\s*\\(\\s*\""
            // unit == "kg"     getStatus() != "CAN_MAKE"
            + "|\\b\\w*(unit|kind|status)(\\(\\))?\\s*[!=]=\\s*\""
            // "kg" == unit
            + "|\"\\s*[!=]=\\s*[\\w.]*(unit|kind|status)\\b"
            // unit.equals("kg")     getUnit().equalsIgnoreCase("KG")
            + "|\\b\\w*(unit|kind|status)(\\(\\))?\\.(equals|equalsIgnoreCase|contentEquals)\\s*\\(\\s*\""
            // "kg".equals(item.getUnit())
            + "|\"\\.(equals|equalsIgnoreCase)\\s*\\(\\s*[\\w.]*(unit|kind|status)\\b");

    /** An Android import other than the one annotation the domain may use. */
    static final Pattern ANDROID_IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?(android|androidx)\\.(?!annotation\\.StringRes;)");

    private static List<SourceFile> sources;

    @BeforeClass
    public static void readMainSources() throws IOException {
        List<SourceFile> read = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList())) {
                read.add(SourceFile.read(file));
            }
        }
        sources = read;
    }

    @Test
    public void mainSourcesAreFound() {
        // Guards against a wrong working directory turning every check below into a no-op
        assertTrue("No Java source under " + MAIN_JAVA.toAbsolutePath(), sources.size() > 5);
    }

    @Test
    public void noLiteralIntentExtraKey() {
        assertNone("Name the extra with a constant from util.IntentKeys", LITERAL_EXTRA_KEY, sources);
    }

    @Test
    public void noLiteralPreferenceKey() {
        assertNone("Name the preference with settings.PrefKey.<KEY>.key()", LITERAL_PREFERENCE_KEY, sources);
    }

    @Test
    public void noLiteralUnitKindStatusOrExtraValue() {
        assertNone("Use the enum (Unit, UnitKind, MatchStatus) or an IntentKeys constant, not a string",
                LITERAL_ENUM_VALUE, sources);
    }

    @Test
    public void domainImportsNothingFromAndroidButStringRes() {
        List<SourceFile> domain = sources.stream()
                .filter(s -> MAIN_JAVA.relativize(s.path).startsWith(DOMAIN))
                .collect(Collectors.toList());
        assertFalse("No source found under domain/", domain.isEmpty());
        assertNone("domain/ is plain Java: only androidx.annotation.StringRes may be imported",
                ANDROID_IMPORT, domain);
    }

    @Test
    public void rulesCatchTheKnownViolations() {
        assertMatches(LITERAL_EXTRA_KEY,
                "intent.putExtra(\"recipe_id\", id);",
                "long id = getIntent().getLongExtra(\"recipe_id\", -1L);",
                "if (getIntent().hasExtra( \"tab\")) {");
        assertMatches(LITERAL_PREFERENCE_KEY,
                "prefs.getBoolean(\"count_expired\", false);",
                "preferences.edit().putInt(\"expiry_threshold_days\", 3).apply();",
                "editor.putString(\"units_system\", \"METRIC\");",
                "Preference p = findPreference(\"units_system\");");
        assertMatches(LITERAL_ENUM_VALUE,
                "item.unit = \"kg\";",
                "private static final String STATUS = \"CAN_MAKE\";",
                "String EXTRA_ID = \"id\";",
                "row.setUnit(\"kg\");",
                "if (status == \"CAN_MAKE\") {",
                "if (item.getUnit().equals(\"kg\")) {",
                "if (\"kg\".equals(item.getUnit())) {",
                "if (\"kg\" == unit) {",
                "boolean same = kind.equalsIgnoreCase(\"mass\");");
        assertMatches(ANDROID_IMPORT,
                "import android.content.Context;",
                "import androidx.annotation.NonNull;",
                "import static android.os.Build.VERSION.SDK_INT;");
    }

    @Test
    public void rulesLeaveLegitimateCodeAlone() {
        String[] legitimate = {
            "intent.putExtra(IntentKeys.EXTRA_RECIPE_ID, id);",
            "item.unit = Unit.KG;",
            "if (item.getUnit() == Unit.KG) {",
            "Unit unit = Unit.fromSymbol(json.getString(\"unit\"));",
            "String name = json.getString(\"name\");",
            "prefs.getBoolean(PrefKey.COUNT_EXPIRED_ITEMS.key(), false);",
            "binding.title.setText(getString(R.string.app_name));",
            "Log.d(TAG, \"unit = \\\"kg\\\" is not allowed\");",
            "// intent.putExtra(\"recipe_id\", id) is what not to write",
            "/* item.unit = \"kg\"; */ int units = 3;",
            "import androidx.annotation.StringRes;",
            "public static final String EXTRA_TAB = PREFIX + \"TAB\";",
        };
        for (String line : legitimate) {
            String code = SourceFile.blank(line);
            for (Pattern rule : List.of(LITERAL_EXTRA_KEY, LITERAL_PREFERENCE_KEY, LITERAL_ENUM_VALUE, ANDROID_IMPORT)) {
                assertFalse("A rule wrongly flags: " + line, rule.matcher(code).find());
            }
        }
    }

    private static void assertMatches(Pattern rule, String... lines) {
        for (String line : lines) {
            assertTrue("The rule misses: " + line, rule.matcher(SourceFile.blank(line)).find());
        }
    }

    private static void assertNone(String fix, Pattern rule, List<SourceFile> files) {
        List<String> offences = new ArrayList<>();
        for (SourceFile file : files) {
            for (int i = 0; i < file.code.size(); i++) {
                if (rule.matcher(file.code.get(i)).find()) {
                    offences.add(MAIN_JAVA.relativize(file.path) + ":" + (i + 1) + ": " + file.original.get(i).trim());
                }
            }
        }
        assertEquals(fix + ":\n" + String.join("\n", offences), 0, offences.size());
    }

    /** A source file as written, and the same lines with comments and string contents blanked. */
    private static final class SourceFile {
        final Path path;
        final List<String> original;
        final List<String> code;

        private SourceFile(Path path, List<String> original, List<String> code) {
            this.path = path;
            this.original = original;
            this.code = code;
        }

        static SourceFile read(Path path) throws IOException {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return new SourceFile(path, Arrays.asList(text.split("\n", -1)),
                    Arrays.asList(blank(text).split("\n", -1)));
        }

        /**
         * Replaces every character inside a comment with a space and every character inside a string
         * or char literal with {@code x}, keeping the quote marks and the line breaks. So
         * {@code putExtra("recipe_id"} still reads {@code putExtra("xxxxxxxxx"}, while a comment or a
         * message that mentions {@code unit = "kg"} no longer looks like code.
         */
        static String blank(String text) {
            StringBuilder out = new StringBuilder(text.length());
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
                if (c == '/' && next == '/') {
                    while (i < text.length() && text.charAt(i) != '\n') {
                        out.append(' ');
                        i++;
                    }
                } else if (c == '/' && next == '*') {
                    int end = text.indexOf("*/", i + 2);
                    end = end < 0 ? text.length() : end + 2;
                    for (; i < end; i++) {
                        out.append(text.charAt(i) == '\n' ? '\n' : ' ');
                    }
                } else if (c == '"' || c == '\'') {
                    out.append(c);
                    i++;
                    while (i < text.length() && text.charAt(i) != c && text.charAt(i) != '\n') {
                        if (text.charAt(i) == '\\' && i + 1 < text.length()) {
                            out.append("xx");
                            i += 2;
                        } else {
                            out.append('x');
                            i++;
                        }
                    }
                    if (i < text.length() && text.charAt(i) == c) {
                        out.append(c);
                        i++;
                    }
                } else {
                    out.append(c);
                    i++;
                }
            }
            return out.toString();
        }
    }
}
