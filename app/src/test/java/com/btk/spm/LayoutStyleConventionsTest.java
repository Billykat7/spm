package com.btk.spm;

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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps every layout on the app's one theme (Issue 2).
 *
 * <p>Colours come from the theme's roles ({@code ?attr/colorPrimary}) and text sizes from the
 * {@code TextAppearance.Spm.*} type scale, so light and dark mode and the user's font scale work on
 * every screen without per-layout fixes. A raw hex colour or an {@code android:textSize} in a layout
 * would bypass both; this test fails naming the file and line of each one. It reads the XML as text
 * on the JVM, so it needs no emulator.
 */
public class LayoutStyleConventionsTest {

    /** Resource root, relative to the module directory Gradle runs unit tests from. */
    private static final Path RES_DIR = Paths.get("src", "main", "res");

    /** An attribute value that is a colour literal: #RGB, #ARGB, #RRGGBB or #AARRGGBB. */
    private static final Pattern RAW_HEX_COLOUR = Pattern.compile("=\"#(?:[0-9A-Fa-f]{3,4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\"");

    /** Any text size set on the view instead of through a text appearance. */
    private static final Pattern TEXT_SIZE = Pattern.compile("android:textSize\\s*=");

    private static List<Path> layoutFiles;

    @BeforeClass
    public static void collectLayouts() throws IOException {
        try (Stream<Path> dirs = Files.list(RES_DIR)) {
            List<Path> layoutDirs = dirs
                    .filter(dir -> dir.getFileName().toString().startsWith("layout"))
                    .collect(Collectors.toList());
            List<Path> files = new ArrayList<>();
            for (Path dir : layoutDirs) {
                try (Stream<Path> xml = Files.list(dir)) {
                    xml.filter(file -> file.toString().endsWith(".xml")).forEach(files::add);
                }
            }
            layoutFiles = files;
        }
    }

    @Test
    public void layoutsAreFound() {
        // Guards against a wrong working directory silently turning the checks below into no-ops
        assertFalse("No layout found under " + RES_DIR.toAbsolutePath(), layoutFiles.isEmpty());
    }

    @Test
    public void noLayoutUsesARawHexColour() throws IOException {
        List<String> offences = findLines(RAW_HEX_COLOUR);
        assertTrue("Use a theme colour role such as ?attr/colorOnSurface instead of a hex value:\n"
                + String.join("\n", offences), offences.isEmpty());
    }

    @Test
    public void noLayoutSetsATextSize() throws IOException {
        List<String> offences = findLines(TEXT_SIZE);
        assertTrue("Use android:textAppearance=\"@style/TextAppearance.Spm.*\" instead of android:textSize:\n"
                + String.join("\n", offences), offences.isEmpty());
    }

    /** Returns "file:line: text" for every line of every layout that matches {@code pattern}. */
    private static List<String> findLines(Pattern pattern) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : layoutFiles) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    hits.add(RES_DIR.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        }
        return hits;
    }
}
