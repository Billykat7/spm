package com.btk.spm.data.repo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps {@link RecipeRepository} read-only (decision 7, Issue 10). Recipes are seed data, so the
 * repository the screens see offers reads and a count and nothing else. A write method added later
 * fails here on the JVM, naming it, instead of being found in review.
 */
public class RecipeRepositoryReadOnlyTest {

    @Test
    public void publicMethodsAreTheThreeReads() {
        Set<String> publicMethods = new TreeSet<>();
        for (Method method : RecipeRepository.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                publicMethods.add(method.getName());
            }
        }
        assertEquals("RecipeRepository must stay read-only: recipes are seed data (decision 7)",
                new TreeSet<>(Arrays.asList("count", "observeAllWithIngredients", "observeById")),
                publicMethods);
    }
}
