package com.btk.spm;

import static org.junit.Assert.assertSame;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The objects the process must hold exactly once (Issue 9): every screen that asks for the pantry
 * repository gets the same one, so every screen observes the same {@code LiveData}.
 */
@RunWith(AndroidJUnit4.class)
public class SpmApplicationTest {

    private final SpmApplication application = ApplicationProvider.getApplicationContext();

    @Test
    public void getPantryRepository_returnsTheSameInstanceOnEveryCall() {
        assertSame(application.getPantryRepository(), application.getPantryRepository());
        assertSame(application.getPantryRepository(), SpmApplication.from(application).getPantryRepository());
    }

    @Test
    public void getDatabase_returnsTheSameInstanceOnEveryCall() {
        assertSame(application.getDatabase(), application.getDatabase());
    }
}
