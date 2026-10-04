package com.btk.spm.data.seed;

import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads the seed's text assets, {@code recipes.json} and {@code aliases.json}, as UTF-8.
 *
 * <p>{@code InputStream.readAllBytes()} would do this in one call, but Android added it in API 33 and
 * the app runs from API 26, so the bytes are copied through a buffer.
 */
final class AssetText {

    private AssetText() {
        // Static helper; never instantiated
    }

    /**
     * Reads a whole asset.
     *
     * @param assets the app's assets
     * @param name the file name under {@code src/main/assets/}
     * @return the file's text
     * @throws IOException if the asset is missing or cannot be read
     */
    @WorkerThread
    @NonNull
    static String read(@NonNull AssetManager assets, @NonNull String name) throws IOException {
        try (InputStream in = assets.open(name)) {
            return readUtf8(in);
        }
    }

    /**
     * Reads a stream to its end as UTF-8, without closing it. JVM tests use it on the same assets,
     * read as test resources.
     *
     * @param in the stream to read
     * @return everything left in the stream, as text
     * @throws IOException if the stream cannot be read
     */
    @NonNull
    static String readUtf8(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = in.read(buffer)) != -1; ) {
            bytes.write(buffer, 0, read);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
