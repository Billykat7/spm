package com.btk.spm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Java source file as written, and the same lines with comments and string contents blanked, for the
 * JVM tests that read {@code src/main/java} as text ({@code ConventionsTest}, {@code DaoBoundaryTest}).
 * Blanking keeps the line numbers, so an offence is reported at the line the author sees.
 */
final class SourceFile {

    /** Where the file is, as found under the root it was read from. */
    final Path path;
    /** The lines as written, for the failure message. */
    final List<String> original;
    /** The same lines with comments and string contents blanked, for matching. */
    final List<String> code;

    private SourceFile(Path path, List<String> original, List<String> code) {
        this.path = path;
        this.original = original;
        this.code = code;
    }

    /**
     * Reads every {@code .java} file under {@code root}, in path order, so failures list the same way
     * on every machine.
     */
    static List<SourceFile> readTree(Path root) throws IOException {
        List<SourceFile> read = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList())) {
                read.add(read(file));
            }
        }
        return read;
    }

    /** Reads one file and blanks its comments and string contents. */
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
