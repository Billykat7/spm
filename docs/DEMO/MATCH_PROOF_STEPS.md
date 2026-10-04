# Match proof: one ingredient in, one recipe in (brief §5.1 part 2)

The steps for the video's live proof of the strict-matching rule, and the ones Issue 32's
`MatchToggleTest` copies. They are checked by `SuggestedRecipesViewModelLiveTest` (JVM) and
`SuggestedRecipesLiveTest` (device), so if either is red, these steps will not work on camera either.

**The recipe:** Tomato pasta, the five-ingredient recipe of the seed (`app/src/main/assets/recipes.json`
is the truth: if it changes, change this list, never the asset). Type exactly these, no expiry date:

| # | Name | Quantity | Unit |
|---|------|----------|------|
| 1 | Pasta | 200 | g |
| 2 | Tomatoes | 4 | pcs |
| 3 | Olive oil | 2 | tbsp |
| 4 | Salt | 2 | g |
| 5 | Garlic | 2 | pcs |

"Tomatoes" is typed in the plural on purpose: the recipe says "tomato", and the match still holds.

**The steps** (start from a fresh install or an empty pantry; about a minute, no restart):

| Step | Do | Expect on the Recipes tab | Say |
|------|----|---------------------------|-----|
| 1 | Open the Recipes tab | "Your pantry is empty, add some ingredients to see what you can cook", "Suggested recipes (0)" | "Nothing in the pantry, so nothing to suggest." |
| 2 | Add rows 1 to 4 on the Pantry tab, back to Recipes | "No recipes match your pantry yet, add more ingredients", still (0); below it, under "Almost there (missing one ingredient)", Tomato pasta, "Missing: 2 pcs garlic" | "Four of the five ingredients. Four of five is not a match: it is almost there, kept apart and not counted." |
| 3 | Add row 5, Garlic, back to Recipes | Tomato pasta, "You have all 5 ingredients", "Suggested recipes (1)"; the "Almost there" heading gone; no refresh tapped | "The fifth goes in, and the recipe moves into the suggestions." |
| 4 | Tap Tomato pasta | Five checks, "You can make this", the method numbered 1 to 4 | "Every line is covered, by the same matcher." |
| 5 | Back, delete Garlic on the Pantry tab, back to Recipes | The zero-match sentence again, (0), and Tomato pasta back under "Almost there" | "Take one away, and it is out of the suggestions." |

The before and after are `docs/REPORT/screenshots/24_match_four_ingredients.png` and
`24_match_five_ingredients.png`.
