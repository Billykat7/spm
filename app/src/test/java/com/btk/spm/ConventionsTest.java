package com.btk.spm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 *       would stop the engine and the validators running on the JVM;</li>
 *   <li>builds a {@code FieldError} or names an {@code R.string.error_*} message anywhere but
 *       {@code domain/validation/}, so every validation rule stays in {@code Validators}
 *       (non-negotiable 8, Issue 31);</li>
 *   <li>shows an {@code error_*} message in a Toast or a Snackbar under {@code ui/}: a validation
 *       error belongs under its field, where it stays until fixed.</li>
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

    /** Where the validation rules live: the one package that may build a FieldError or name an error message. */
    private static final Path VALIDATION = DOMAIN.resolve("validation");

    /** The screens, where no validation message may be shown in a Toast or a Snackbar. */
    private static final Path UI = Paths.get("com", "btk", "spm", "ui");

    /** A validation error built or named: {@code new FieldError(...)} or {@code R.string.error_...}. */
    static final Pattern VALIDATION_ERROR = Pattern.compile("\\bnew\\s+FieldError\\s*\\(|\\bR\\.string\\.error_\\w+");

    /** A Toast or a Snackbar given an error message, on the same line. */
    static final Pattern ERROR_IN_TOAST_OR_SNACKBAR =
            Pattern.compile("\\b(Toast\\.makeText|Snackbar\\.make)\\s*\\(.*\\berror_\\w+");

    /** An Android import other than the one annotation the domain may use. */
    static final Pattern ANDROID_IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?(android|androidx)\\.(?!annotation\\.StringRes;)");

    private static List<SourceFile> sources;

    @BeforeClass
    public static void readMainSources() throws IOException {
        sources = SourceFile.readTree(MAIN_JAVA);
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
    public void onlyValidatorsBuildsAFieldErrorOrNamesAnErrorMessage() {
        List<SourceFile> outside = sources.stream()
                .filter(s -> !MAIN_JAVA.relativize(s.path).startsWith(VALIDATION))
                .collect(Collectors.toList());
        assertNone("Build FieldErrors and name R.string.error_* only in domain/validation/Validators",
                VALIDATION_ERROR, outside);
    }

    @Test
    public void noScreenShowsAnErrorMessageInAToastOrASnackbar() {
        List<SourceFile> screens = sources.stream()
                .filter(s -> MAIN_JAVA.relativize(s.path).startsWith(UI))
                .collect(Collectors.toList());
        assertFalse("No source found under ui/", screens.isEmpty());
        assertNone("Show a validation error under its field with TextInputLayout.setError, not in a Toast or Snackbar",
                ERROR_IN_TOAST_OR_SNACKBAR, screens);
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
        assertMatches(VALIDATION_ERROR,
                "errors.add(new FieldError(Field.NAME, R.string.error_name_required));",
                "layout.setError(getString(R.string.error_quantity_invalid));");
        assertMatches(ERROR_IN_TOAST_OR_SNACKBAR,
                "Toast.makeText(this, R.string.error_name_required, Toast.LENGTH_SHORT).show();",
                "Snackbar.make(root, getString(R.string.error_unit_required), Snackbar.LENGTH_LONG).show();");
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
            "layout.setError(getString(error.messageRes()));",
            "Toast.makeText(this, R.string.recipe_not_found, Toast.LENGTH_LONG).show();",
        };
        for (String line : legitimate) {
            String code = SourceFile.blank(line);
            for (Pattern rule : List.of(LITERAL_EXTRA_KEY, LITERAL_PREFERENCE_KEY, LITERAL_ENUM_VALUE, ANDROID_IMPORT,
                    VALIDATION_ERROR, ERROR_IN_TOAST_OR_SNACKBAR)) {
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
}
