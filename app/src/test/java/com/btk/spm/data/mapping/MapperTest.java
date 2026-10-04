package com.btk.spm.data.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.btk.spm.data.model.PantryItem;
import com.btk.spm.data.model.Recipe;
import com.btk.spm.data.model.RecipeIngredient;
import com.btk.spm.data.model.RecipeWithIngredients;
import com.btk.spm.domain.Quantity;
import com.btk.spm.domain.Unit;
import com.btk.spm.domain.matching.PantryEntry;
import com.btk.spm.domain.matching.RecipeSpec;
import com.btk.spm.domain.matching.RequiredIngredient;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link PantryEntryMapper} and {@link RecipeSpecMapper}: every field an entity holds reaches the
 * engine value unchanged, an item with no expiry stays one, and order is kept.
 */
public class MapperTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 10, 20);
    private static final long CREATED = 1_790_000_000_000L;

    @Test
    public void aPantryItemWithAnExpiry_keepsItsNameQuantityUnitAndDate() {
        PantryItem tomatoes = new PantryItem(4, "Tomatoes", 4, Unit.PCS, EXPIRY, CREATED);

        PantryEntry entry = PantryEntryMapper.toEntry(tomatoes);

        assertEquals(new PantryEntry("Tomatoes", new Quantity(4, Unit.PCS), EXPIRY), entry);
    }

    @Test
    public void aPantryItemWithNoExpiry_staysAnItemThatNeverExpires() {
        PantryItem flour = new PantryItem(5, "Plain flour", 1.5, Unit.KG, null, CREATED);

        PantryEntry entry = PantryEntryMapper.toEntry(flour);

        assertEquals("Plain flour", entry.name());
        assertEquals(new Quantity(1.5, Unit.KG), entry.quantity());
        assertNull(entry.expiry());
    }

    @Test
    public void thePantry_isMappedInOrder() {
        List<PantryEntry> entries = PantryEntryMapper.toEntries(List.of(
                new PantryItem(1, "egg", 6, Unit.PCS, EXPIRY, CREATED),
                new PantryItem(2, "milk", 1, Unit.L, null, CREATED)));

        assertEquals(List.of(
                new PantryEntry("egg", new Quantity(6, Unit.PCS), EXPIRY),
                new PantryEntry("milk", new Quantity(1, Unit.L), null)), entries);
    }

    @Test
    public void aRowWithNothingInIt_isLeftOut_andTheRestAreKept() {
        // The add form never stores 0, but the Database Inspector can, and Quantity cannot hold it
        List<PantryEntry> entries = PantryEntryMapper.toEntries(List.of(
                new PantryItem(1, "egg", 0, Unit.PCS, null, CREATED),
                new PantryItem(2, "milk", 1, Unit.L, null, CREATED)));

        assertEquals(List.of(new PantryEntry("milk", new Quantity(1, Unit.L), null)), entries);
    }

    @Test
    public void theMappedPantry_cannotBeChanged() {
        List<PantryEntry> entries = PantryEntryMapper.toEntries(List.of());

        assertThrows(UnsupportedOperationException.class,
                () -> entries.add(new PantryEntry("egg", new Quantity(1, Unit.PCS), null)));
    }

    @Test
    public void aRecipe_keepsItsIdAndEveryLineInOrder() {
        RecipeWithIngredients omelette = recipe(9, "Cheese omelette",
                new RecipeIngredient(31, 9, "egg", 3, Unit.PCS),
                new RecipeIngredient(32, 9, "cheese", 30, Unit.G),
                new RecipeIngredient(33, 9, "butter", 10, Unit.G));

        RecipeSpec spec = RecipeSpecMapper.toSpec(omelette);

        assertEquals(9, spec.id());
        assertEquals(List.of(
                new RequiredIngredient("egg", new Quantity(3, Unit.PCS)),
                new RequiredIngredient("cheese", new Quantity(30, Unit.G)),
                new RequiredIngredient("butter", new Quantity(10, Unit.G))), spec.ingredients());
    }

    @Test
    public void everyRecipe_isMappedInOrder() {
        List<RecipeSpec> specs = RecipeSpecMapper.toSpecs(List.of(
                recipe(2, "Garlic bread", new RecipeIngredient(5, 2, "bread", 4, Unit.PCS)),
                recipe(1, "Rice pudding", new RecipeIngredient(6, 1, "rice", 100, Unit.G))));

        assertEquals(2, specs.size());
        assertEquals(2, specs.get(0).id());
        assertEquals(1, specs.get(1).id());
    }

    @Test
    public void aRecipeWithNoIngredients_isRefused() {
        // The seed never has one (Issue 11); the engine cannot judge a recipe that needs nothing
        RecipeWithIngredients empty = recipe(3, "Nothing");

        assertThrows(IllegalArgumentException.class, () -> RecipeSpecMapper.toSpec(empty));
    }

    private static RecipeWithIngredients recipe(long id, String name, RecipeIngredient... ingredients) {
        return new RecipeWithIngredients(new Recipe(id, name, 2, List.of("Cook it.")), List.of(ingredients));
    }
}
