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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes non-negotiable 6 enforceable: Room is the single source of truth, and only {@code data/}
 * touches it (Issue 9).
 *
 * <p>A screen that imports a DAO compiles and works, and then quietly stops being a view onto the
 * database: it queries on its own schedule, keeps its own list and misses the changes another screen
 * makes. This test reads every Java file under {@code src/main/java} on the JVM and fails, naming the
 * file and line, when a file outside {@code com/btk/spm/data/}:
 * <ul>
 *   <li>refers to any type in {@code com.btk.spm.data.db}, by import (single, wildcard or static) or
 *       by its full name in code;</li>
 *   <li>calls {@code pantryItemDao()} or {@code recipeDao()}.</li>
 * </ul>
 *
 * <p>One reference is allowed: {@code SpmApplication} imports {@code AppDatabase}, because it builds
 * the database and hands it to the repositories. It is the composition root, and it touches no DAO.
 * The same blanking as {@code ConventionsTest} applies, so a comment or a log message that names a
 * DAO is not an offence. It also checks that {@code allowMainThreadQueries()} never reaches main
 * code; the instrumented tests may use it, the app never does.
 */
public class DaoBoundaryTest {

    /** Main sources, relative to the module directory Gradle runs unit tests from. */
    private static final Path MAIN_JAVA = Paths.get("src", "main", "java");

    /** The one package allowed to touch the database directly. */
    private static final Path DATA = Paths.get("com", "btk", "spm", "data");

    /** The composition root, and the one type of {@code data.db} it may name. */
    private static final Path APPLICATION = Paths.get("com", "btk", "spm", "SpmApplication.java");
    private static final String APPLICATION_MAY_NAME = "com.btk.spm.data.db.AppDatabase";

    /**
     * Any reference to the {@code data.db} package: {@code import com.btk.spm.data.db.PantryItemDao;},
     * {@code import com.btk.spm.data.db.*;}, {@code import static com.btk.spm.data.db.X.y;}, or a full
     * name in code. Group 1 is the referenced name, up to the type (or {@code *}).
     */
    static final Pattern DATA_DB_REFERENCE = Pattern.compile("\\b(com\\s*\\.\\s*btk\\s*\\.\\s*spm\\s*\\.\\s*data"
            + "\\s*\\.\\s*db\\s*\\.\\s*(?:\\w+|\\*))");

    /** A call to one of the DAO getters on {@code AppDatabase}. */
    static final Pattern DAO_GETTER_CALL = Pattern.compile("\\b(pantryItemDao|recipeDao)\\s*\\(");

    /** Room's switch that lets a query block the main thread. */
    static final Pattern MAIN_THREAD_QUERIES = Pattern.compile("\\ballowMainThreadQueries\\s*\\(");

    private static List<SourceFile> sources;

    @BeforeClass
    public static void readMainSources() throws IOException {
        sources = SourceFile.readTree(MAIN_JAVA);
    }

    @Test
    public void mainSourcesAreFound() {
        // Guards against a wrong working directory turning every check below into a no-op
        assertTrue("No Java source under " + MAIN_JAVA.toAbsolutePath(), sources.size() > 5);
        assertTrue("SpmApplication.java not found",
                sources.stream().anyMatch(s -> MAIN_JAVA.relativize(s.path).equals(APPLICATION)));
    }

    @Test
    public void onlyDataReachesTheDatabasePackage() {
        List<String> offences = new ArrayList<>();
        for (SourceFile file : outsideData()) {
            Path relative = MAIN_JAVA.relativize(file.path);
            for (int i = 0; i < file.code.size(); i++) {
                if (referencesDataDb(relative, file.code.get(i))) {
                    offences.add(offence(file, i));
                }
            }
        }
        assertNoOffences("Outside data/, go through a repository (data.repo), never data.db"
                + " (non-negotiable 6). Only SpmApplication may name AppDatabase, to build it", offences);
    }

    @Test
    public void onlyDataCallsADaoGetter() {
        List<String> offences = new ArrayList<>();
        for (SourceFile file : outsideData()) {
            for (int i = 0; i < file.code.size(); i++) {
                if (DAO_GETTER_CALL.matcher(file.code.get(i)).find()) {
                    offences.add(offence(file, i));
                }
            }
        }
        assertNoOffences("Outside data/, call a repository method, not pantryItemDao() or recipeDao()"
                + " (non-negotiable 6)", offences);
    }

    @Test
    public void mainCodeNeverAllowsMainThreadQueries() {
        List<String> offences = new ArrayList<>();
        for (SourceFile file : sources) {
            for (int i = 0; i < file.code.size(); i++) {
                if (MAIN_THREAD_QUERIES.matcher(file.code.get(i)).find()) {
                    offences.add(offence(file, i));
                }
            }
        }
        assertNoOffences("allowMainThreadQueries() is for tests under androidTest only;"
                + " run the query on the write executor or observe its LiveData", offences);
    }

    @Test
    public void rulesCatchTheKnownViolations() {
        Path screen = Paths.get("com", "btk", "spm", "ui", "MainActivity.java");
        for (String line : new String[] {
            "import com.btk.spm.data.db.PantryItemDao;",
            "import com.btk.spm.data.db.RecipeDao;",
            "import com.btk.spm.data.db.*;",
            "import com.btk.spm.data.db.AppDatabase;",
            "import static com.btk.spm.data.db.Converters.unitToName;",
            "com.btk.spm.data.db.PantryItemDao dao = null;",
        }) {
            assertTrue("The rule misses: " + line, referencesDataDb(screen, SourceFile.blank(line)));
        }
        // The allowance is for AppDatabase in SpmApplication, nothing wider
        assertTrue(referencesDataDb(APPLICATION, "import com.btk.spm.data.db.PantryItemDao;"));
        assertTrue(referencesDataDb(APPLICATION, "import com.btk.spm.data.db.*;"));
        for (String line : new String[] {
            "List<PantryItem> items = db.pantryItemDao().getAllSync();",
            "SpmApplication.from(this).getDatabase().recipeDao ().count();",
            "dao = database.pantryItemDao();",
        }) {
            assertTrue("The rule misses: " + line, DAO_GETTER_CALL.matcher(SourceFile.blank(line)).find());
        }
        assertTrue(MAIN_THREAD_QUERIES.matcher("Room.databaseBuilder(c, A.class, n).allowMainThreadQueries().build();").find());
    }

    @Test
    public void rulesLeaveLegitimateCodeAlone() {
        Path screen = Paths.get("com", "btk", "spm", "ui", "MainActivity.java");
        for (String line : new String[] {
            "import com.btk.spm.data.repo.PantryRepository;",
            "import com.btk.spm.data.model.PantryItem;",
            "import com.btk.spm.data.dbtools.Helper;",
            "// never import com.btk.spm.data.db.PantryItemDao here",
            "Log.d(TAG, \"pantryItemDao() is off limits\");",
            "repository.observeAll().observe(getViewLifecycleOwner(), adapter::submitList);",
            "PantryItemDao pantryItemDaoName;",
        }) {
            String code = SourceFile.blank(line);
            assertFalse("A rule wrongly flags: " + line, referencesDataDb(screen, code));
            assertFalse("A rule wrongly flags: " + line, DAO_GETTER_CALL.matcher(code).find());
        }
        assertFalse(referencesDataDb(APPLICATION, "import com.btk.spm.data.db.AppDatabase;"));
    }

    /**
     * Whether one blanked line of the file at {@code relative} names something in {@code data.db} it
     * may not. {@code SpmApplication} may name {@code AppDatabase} and nothing else from there.
     */
    static boolean referencesDataDb(Path relative, String code) {
        Matcher reference = DATA_DB_REFERENCE.matcher(code);
        while (reference.find()) {
            String name = reference.group(1).replaceAll("\\s+", "");
            if (!(relative.equals(APPLICATION) && name.equals(APPLICATION_MAY_NAME))) {
                return true;
            }
        }
        return false;
    }

    private static List<SourceFile> outsideData() {
        List<SourceFile> outside = new ArrayList<>();
        for (SourceFile file : sources) {
            if (!MAIN_JAVA.relativize(file.path).startsWith(DATA)) {
                outside.add(file);
            }
        }
        assertFalse("Every source is under data/", outside.isEmpty());
        return outside;
    }

    private static String offence(SourceFile file, int line) {
        return MAIN_JAVA.relativize(file.path) + ":" + (line + 1) + ": " + file.original.get(line).trim();
    }

    private static void assertNoOffences(String fix, List<String> offences) {
        assertEquals(fix + ":\n" + String.join("\n", offences), 0, offences.size());
    }
}
