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

/**
 * Keeps the strict-matching engine pure and its names normalised (Issue 20, non-negotiables 1, 2
 * and 5). It reads {@code src/main/java/com/btk/spm/domain/matching/} as text and fails, naming the
 * file and line, when:
 * <ul>
 *   <li>any file there imports {@code android.*}, {@code androidx.*} or {@code com.btk.spm.data.*}:
 *       the engine runs on the JVM and takes domain values, never Room entities;</li>
 *   <li>any file there reads the clock ({@code LocalDate.now()}, {@code Clock}, the system time):
 *       "today" is passed in through {@code MatchOptions};</li>
 *   <li>{@code StrictMatcher} calls {@code equals}, {@code equalsIgnoreCase}, {@code contentEquals}
 *       or {@code compareTo}, or reads an ingredient's {@code name()} anywhere but inside
 *       {@code normalise(...)}: a raw comparison would let {@code "Tomatoes"} miss {@code "tomato"}.</li>
 * </ul>
 *
 * <p>It sits beside {@code ConventionsTest} and {@code DaoBoundaryTest}, the other tests that read the
 * sources, and shares their {@link SourceFile}: comments and string contents are blanked first, so a
 * Javadoc example cannot trigger a rule. {@link #rulesCatchTheKnownViolations()} and
 * {@link #rulesLeaveLegitimateCodeAlone()} test the patterns themselves.
 */
public class MatchingPurityTest {

    /** Main sources, relative to the module directory Gradle runs unit tests from. */
    private static final Path MAIN_JAVA = Paths.get("src", "main", "java");

    /** The engine's package. */
    private static final Path MATCHING =
            MAIN_JAVA.resolve(Paths.get("com", "btk", "spm", "domain", "matching"));

    /** The one class that decides a match. */
    private static final Path STRICT_MATCHER = MATCHING.resolve("StrictMatcher.java");

    /** An import of Android, AndroidX or the data layer. */
    static final Pattern FORBIDDEN_IMPORT =
            Pattern.compile("^\\s*import\\s+(static\\s+)?(android|androidx|com\\.btk\\.spm\\.data)\\.");

    /** Reading the clock instead of being told the day. */
    static final Pattern CLOCK = Pattern.compile("\\b(LocalDate|LocalDateTime|Instant|ZonedDateTime)\\.now\\s*\\("
            + "|\\bClock\\b|\\bSystem\\.currentTimeMillis\\s*\\(");

    /** A text comparison: {@code a.equals(b)}, {@code Objects.equals(a, b)}, {@code a.compareTo(b)}. */
    static final Pattern RAW_COMPARISON =
            Pattern.compile("\\.\\s*(equals|equalsIgnoreCase|contentEquals|compareTo|compareToIgnoreCase)\\s*\\(");

    /** A {@code name()} call. */
    static final Pattern NAME_CALL = Pattern.compile("\\.\\s*name\\s*\\(\\s*\\)");

    /** A {@code name()} call as the argument of {@code normalise(...)}, the only place one may be read. */
    static final Pattern NORMALISED_NAME =
            Pattern.compile("\\bnormalise\\s*\\(\\s*[\\w.]+\\.\\s*name\\s*\\(\\s*\\)\\s*\\)");

    private static List<SourceFile> matching;

    @BeforeClass
    public static void readTheEngine() throws IOException {
        matching = SourceFile.readTree(MATCHING);
    }

    @Test
    public void theEngineIsFound() {
        // Guards against a wrong working directory turning every check below into a no-op
        assertTrue("StrictMatcher not found under " + MATCHING.toAbsolutePath(),
                matching.stream().anyMatch(s -> s.path.endsWith(STRICT_MATCHER.getFileName())));
    }

    @Test
    public void theEngineImportsNoAndroidAndNoDataLayer() {
        assertNone("domain/matching/ is plain Java over domain values: no android.*, androidx.* or data.*",
                FORBIDDEN_IMPORT, matching);
    }

    @Test
    public void theEngineNeverReadsTheClock() {
        assertNone("Pass the day in through MatchOptions.today()", CLOCK, matching);
    }

    @Test
    public void strictMatcherNeverComparesTextItself() {
        assertNone("Compare normalised names as map keys, never with equals or compareTo",
                RAW_COMPARISON, strictMatcher());
    }

    @Test
    public void strictMatcherReadsANameOnlyToNormaliseIt() {
        List<String> offences = new ArrayList<>();
        for (SourceFile file : strictMatcher()) {
            for (int i = 0; i < file.code.size(); i++) {
                String line = file.code.get(i);
                if (NAME_CALL.matcher(NORMALISED_NAME.matcher(line).replaceAll("")).find()) {
                    offences.add(where(file, i));
                }
            }
        }
        assertEquals("Pass every name() through normaliser.normalise(...):\n"
                + String.join("\n", offences), 0, offences.size());
    }

    @Test
    public void rulesCatchTheKnownViolations() {
        assertMatches(FORBIDDEN_IMPORT,
                "import android.util.Log;",
                "import androidx.annotation.NonNull;",
                "import com.btk.spm.data.model.PantryItem;",
                "import static android.os.Build.VERSION.SDK_INT;");
        assertMatches(CLOCK,
                "LocalDate today = LocalDate.now();",
                "private final Clock clock;",
                "long now = System.currentTimeMillis();");
        assertMatches(RAW_COMPARISON,
                "if (required.name().equals(entry.name())) {",
                "if (a.equalsIgnoreCase(b)) {",
                "boolean same = Objects.equals(a, b);",
                "int order = a.compareTo(b);");
        String[] rawNames = {
            "String key = required.name();",
            "stock.get(entry.name().toLowerCase());",
            "normaliser.normalise(required.name()).equals(entry.name())",
        };
        for (String line : rawNames) {
            String code = SourceFile.blank(line);
            assertTrue("The rule misses: " + line,
                    NAME_CALL.matcher(NORMALISED_NAME.matcher(code).replaceAll("")).find());
        }
    }

    @Test
    public void rulesLeaveLegitimateCodeAlone() {
        String[] legitimate = {
            "String name = normaliser.normalise(required.name());",
            "import com.btk.spm.domain.Quantity;",
            "import java.time.LocalDate;",
            "if (options.includeExpired() || !ExpiryRules.isExpired(entry.expiry(), options.today())) {",
            "// never write required.name().equals(entry.name())",
            "Log.d(TAG, \"LocalDate.now() is not used\");",
        };
        for (String line : legitimate) {
            String code = SourceFile.blank(line);
            for (Pattern rule : List.of(FORBIDDEN_IMPORT, CLOCK, RAW_COMPARISON)) {
                assertFalse("A rule wrongly flags: " + line, rule.matcher(code).find());
            }
            assertFalse("A rule wrongly flags: " + line,
                    NAME_CALL.matcher(NORMALISED_NAME.matcher(code).replaceAll("")).find());
        }
    }

    private static List<SourceFile> strictMatcher() {
        List<SourceFile> files = new ArrayList<>();
        for (SourceFile file : matching) {
            if (file.path.endsWith(STRICT_MATCHER.getFileName())) {
                files.add(file);
            }
        }
        assertFalse("StrictMatcher.java not found", files.isEmpty());
        return files;
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
                    offences.add(where(file, i));
                }
            }
        }
        assertEquals(fix + ":\n" + String.join("\n", offences), 0, offences.size());
    }

    private static String where(SourceFile file, int index) {
        return MAIN_JAVA.relativize(file.path) + ":" + (index + 1) + ": " + file.original.get(index).trim();
    }
}
