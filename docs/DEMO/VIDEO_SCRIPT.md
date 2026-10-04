# Video script (brief §5)

One recording, 5 to 7 minutes, in the four parts of brief §5.1, with my voice narrating throughout
(§5.2, compulsory), at 1080p, exported as MP4 (H.264 and AAC) and placed directly inside the
submission ZIP. It is recorded to `docs/DEMO/spm_demo.mp4` on the laptop and never committed
(`docs/DEMO/.gitignore`); Issue 37's packaging script takes it from that path.

This is an outline, not a text to read aloud. Each part lists what is on screen and the points to
make; the words are mine on the day. The target is **6:00**, with 30 seconds of slack either way.

| Part | Starts | Ends | Planned | Dry run (fill in) |
|------|--------|------|---------|-------------------|
| 1. GitHub walkthrough | 0:00 | 1:00 | 1:00 | |
| 2. Live app | 1:00 | 3:30 | 2:30 | |
| 3. Concepts at the code | 3:30 | 5:30 | 2:00 | |
| 4. Database justification | 5:30 | 6:00 | 0:30 | |
| **Total** | | | **6:00** | |

**Line ranges.** Every path and range below was read at commit `2e225d5`, the base of Issue 36.
M7 changes nothing under `app/`, so `v0.6.0` and `v1.0.0` carry the same lines. Before recording,
check out the tag being recorded and re-check any range with:

```bash
git show v1.0.0:<path> | sed -n 'A,Bp'
```

If a range has moved, fix it here before recording, never on camera.

---

## Part 1: the GitHub walkthrough (0:00 to 1:00)

Browser at 100% zoom, signed in, on `https://github.com/Billykat7/spm`.

| Time | On screen | Talking points |
|------|-----------|----------------|
| 0:00–0:15 | The commits page of `main`, scrolled from the bottom: the first commit, "Initial commit", 1 October 2026, then up through the history | One step per commit; every subject starts `Issue N:`, so each commit leads back to its issue; the history was built in milestones, not dumped at the end |
| 0:15–0:30 | The Milestones page, Closed then Open | Six milestones closed, M1 to M6; M7 (the report, this video, the ZIP) is in progress at the time of recording, and say so |
| 0:30–0:50 | One merged pull request, suggested #71 (Issue 28, the Settings screen): its description, then the green check | The description's Testing, Screenshots, Acceptance criteria, and Risk and rollback; CI runs the guards, lint, the unit tests, 100% line coverage of the matcher and the debug build on every pull request; nothing merged red |
| 0:50–1:00 | The Releases page | One release per milestone, `v0.1.0` onwards; a `v*` tag builds the APK and attaches it to the release |

## Part 2: the live app (1:00 to 3:30)

Emulator `spm_api35`, the app installed from the recorded tag, the pantry seeded with one row (see
the checklist). That row, Milk, is in no recipe's reach, so the Recipes tab opens on the zero-match
state rather than on step 1's empty-pantry sentence in `MATCH_PROOF_STEPS.md`; say so if it is
noticed. Nothing is refreshed by hand at any point.

| Time | On screen | Talking points |
|------|-----------|----------------|
| 1:00–1:35 | **CRUD** on the Pantry tab. Create: the add button, "Flour", 500, g, no expiry, Save; try Save with the name blank first to show the error. Read: the row in the list, in name order. Update: the row's menu, Edit, 500 g to 1 kg, Save. Delete: the row's menu, Delete, confirm | Every write goes to Room on a background thread; the list observes the table, so it redraws itself; the form validates before anything is written; delete asks first and offers undo |
| 1:35–2:15 | **The match proof**, steps 1 to 3 of [`MATCH_PROOF_STEPS.md`](MATCH_PROOF_STEPS.md): Recipes shows the zero-match sentence and (0); add Pasta, Tomatoes, Olive oil and Salt; Recipes shows Tomato pasta under "Almost there", missing 2 pcs garlic; add Garlic; Recipes shows Tomato pasta, "Suggested recipes (1)" | The rule is strict: every ingredient, in at least the amount; four of five is not a match; "Tomatoes" covers "tomato"; the fifth goes in and the recipe appears with no refresh |
| 2:15–2:30 | **The recipe detail**, step 4: tap Tomato pasta; five checks, "You can make this", the method | Opened by an Intent carrying only the recipe's id; the checks come from the same matcher |
| 2:30–2:45 | **The removal and the zero-match state**, step 5: back, delete Garlic, back to Recipes: the zero-match sentence, (0), Tomato pasta back under "Almost there" | Take one away and it leaves the suggestions; this is the zero-match state, worded, not a blank screen |
| 2:45–3:05 | **Force-stop and relaunch**: in a terminal, `adb shell am force-stop com.btk.spm` (or Settings, Apps, spm, Force stop); relaunch from the launcher; the Pantry tab: Milk and the four rows | The process is killed, not just backgrounded; the rows come back from the SQLite file on the device |
| 3:05–3:30 | **Settings and a test notification**: the Settings tab; move the expiring-soon threshold and come back to see Milk's badge follow; tap "Send a test alert now"; pull down the shade; the notification names Milk; tap it, the Pantry tab opens | Each setting changes behaviour, nothing decorative; the alert is a WorkManager worker that runs daily, and the test runs it at once; the notification permission is the app's only permission |

## Part 3: the concepts at the code (3:30 to 5:30)

Android Studio, editor font 14 pt or larger, each file opened at its first line below before the
part starts (one tab per file, in this order). Name each concept out loud as it starts.

**Choice:** the strict-matching algorithm, Room end to end, and **Intents**. Intents rather than the
lifecycle because the whole path is four short spans in three files, while the lifecycle logcat needs
tab switching and backgrounding with a log pane that competes with the code for the screen.

### 3.1 The strict-matching algorithm (3:30 to 4:10)

| File | Lines | Point at |
|------|-------|----------|
| `app/src/main/java/com/btk/spm/domain/matching/StrictMatcher.java` | 61–63 | `match`: builds the pantry stock, then decides |
| same | 111–123 | `stock`: the pantry map, normalised name to kind to canonical total; expired rows left out |
| same | 89–108 | `decide`: the loop over every required ingredient (93–104), `isAtLeast` (98), a `Shortfall` for each line not covered (101–103) |
| `app/src/main/java/com/btk/spm/domain/matching/CanonicalQuantity.java` | 63–66 | `isAtLeast`: same kind, and at least the amount |
| `app/src/main/java/com/btk/spm/domain/MatchStatus.java` | 33–41 | `forShortfalls`: 0 is can make, 1 is almost there, more is cannot make |

Points: plain Java, one place in the app decides; names are normalised and amounts converted before
any comparison, so 1 kg covers 250 g and grams never cover cups; one shortfall is enough to keep a
recipe out of the suggestions, which is the four-of-five case from part 2.

### 3.2 How Room works, end to end (4:10 to 5:00)

| File | Lines | Point at |
|------|-------|----------|
| `app/src/main/java/com/btk/spm/data/model/PantryItem.java` | 28–80 | `@Entity` `pantry_items`, the `@PrimaryKey`, one `@ColumnInfo` per column |
| `app/src/main/java/com/btk/spm/data/db/PantryItemDao.java` | 58–60 | `observeAll`: the `@Query` that returns `LiveData<List<PantryItem>>` |
| same | 47–48, 125–126, 134–135 | `@Insert`, `@Update`, `@Delete`: the C, U and D |
| `app/src/main/java/com/btk/spm/data/repo/PantryRepository.java` | 51–54, 98–100 | `observeAll` hands the DAO's `LiveData` on; `insert` runs the write on the write executor |
| `app/src/main/java/com/btk/spm/ui/pantry/PantryViewModel.java` | 86–91, 116–119 | The repository's `LiveData`, sorted with `switchMap` and `map`; `getItems` |
| `app/src/main/java/com/btk/spm/ui/pantry/PantryFragment.java` | 100, 121–128 | The ViewModel from `ViewModelProvider`; `observe` with the view's lifecycle owner, then `submitList` |
| `app/src/main/java/com/btk/spm/ui/pantry/PantryAdapter.java` | 52, 214–225 | `extends ListAdapter`, whose `submitList` diffs the new list with `ItemDiff` |

Points: Room generates the SQL code from the annotations and checks each query at compile time; a
write changes the table, Room re-runs every observed query on it, the `LiveData` emits, and the list
redraws only the rows that changed; the Fragment keeps no copy of the list, which is why part 2
needed no refresh.

### 3.3 Intents (5:00 to 5:30)

| File | Lines | Point at |
|------|-------|----------|
| `app/src/main/java/com/btk/spm/ui/recipes/SuggestedRecipesFragment.java` | 119–122 | `onRecipeClick`: `startActivity` with the Intent the detail screen builds |
| `app/src/main/java/com/btk/spm/ui/recipes/RecipeDetailActivity.java` | 60–63 | `intentFor`: an explicit Intent, the recipe's id in `IntentKeys.EXTRA_RECIPE_ID` |
| `app/src/main/java/com/btk/spm/util/IntentKeys.java` | 17–24 | `PREFIX` and `EXTRA_RECIPE_ID`, the extra's name written once |
| `app/src/main/java/com/btk/spm/ui/recipes/RecipeDetailActivity.java` | 66–69, 86–90 | `onCreate` reads it with `getLongExtra` and a default meaning absent; a missing id ends in the not-found state |

Points: explicit, not implicit, so only this screen can answer it, and it is not exported (the
manifest, `app/src/main/AndroidManifest.xml` lines 43–51); only the id travels, the screen loads the
rest from Room; both sides use the one constant, so a misspelt key cannot compile into a silent bug.

## Part 4: the database justification (5:30 to 6:00)

On screen: `app/src/main/java/com/btk/spm/data/db/AppDatabase.java` lines 26–31 (`@Database`, the
three entities, `exportSchema = true`), then the committed schema
`app/schemas/com.btk.spm.data.db.AppDatabase/1.json`. Talking points for decision 1, in my own
words, not read from the README:

- **Chosen: Room over SQLite, on the device.** It is what the module's persistent-data chapter
  teaches; the pantry is the user's own, so it needs no account, server, key or network.
- **What Room adds:** compile-time checked SQL, the exported schema the report's ER diagram is drawn
  from, `LiveData` queries that keep the Recipes tab current (part 3.2), and an in-memory database
  for the tests.
- **Not chosen, Firebase:** a cloud dependency and a sign-in the app has no use for.
- **Not chosen, PostgreSQL:** a REST backend to build and host, a second project for no gain.

---

## Recording checklist

Before recording:

- [ ] The recorded tag is checked out and the debug APK built from it is installed on `spm_api35`;
      every line range above re-checked with `git show <tag>:<path> | sed -n 'A,Bp'`.
- [ ] Recorder: OBS (Display Capture, 1920×1080 canvas and output, 30 fps, MP4) or the Android
      Studio recorder for the emulator parts. Output 1080p.
- [ ] Microphone test: ten seconds recorded and played back; levels peak below clipping; notifications
      on the Mac silenced.
- [ ] Emulator at 100% zoom, light theme, animations at normal speed (the test gate sets them to 0;
      reset with `adb shell settings put global window_animation_scale 1`,
      `transition_animation_scale 1` and `animator_duration_scale 1`).
- [ ] Android Studio editor font 14 pt or larger; the files of part 3 open in order.
- [ ] Pantry seeded: uninstall and reinstall (or clear data), then add one row, Milk, 1 l, expiring
      two days from today. Nothing else; the match proof adds its own rows.
- [ ] Settings: expiring-soon alerts on, and the notification permission already granted, so no
      system dialog interrupts part 2.
- [ ] A logcat pane open in Android Studio, filtered to the app, for anything that goes wrong.
- [ ] A terminal open with `adb shell am force-stop com.btk.spm` typed and ready.
- [ ] A browser tab for each page of part 1, in order.
- [ ] A timed dry run, start to end, with the actual times written into the timing table above and
      the beats trimmed until the total is between 5:30 and 6:30.

## Compression and checks

`ffmpeg` and `ffprobe` are not installed on this Mac yet: `brew install ffmpeg` installs both.

Compress the raw recording to the file the ZIP takes:

```bash
ffmpeg -i in.mp4 -c:v libx264 -crf 28 -preset slow -c:a aac -b:a 96k docs/DEMO/spm_demo.mp4
```

It must be under 25 MB (`stat -f %z docs/DEMO/spm_demo.mp4` below 25000000). If it is not, raise
`-crf` a step at a time (30, 32) before ever lowering the resolution.

Length, resolution and audio:

```bash
# Duration in seconds: must be between 300 and 420
ffprobe -v error -show_entries format=duration -of csv=p=0 docs/DEMO/spm_demo.mp4
# Frame size: must print 1920,1080
ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 docs/DEMO/spm_demo.mp4
# An audio stream: must print audio
ffprobe -v error -select_streams a -show_entries stream=codec_type -of csv=p=0 docs/DEMO/spm_demo.mp4
```

Then watch it once, start to end, with sound, before it goes into the ZIP.
