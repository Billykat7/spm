# Screenshots index

Real screenshots of the running app, taken on the emulator, one per screen and core function (brief
section 6). `NN_<slug>.png`, two-digit order; `_dark` for the dark-theme variants. Issue 33 re-takes
the final set after the UI pass, so earlier rows are working copies.

| NN | File | Caption | Issue |
|----|------|---------|-------|
| 01 | 01_scaffold_main_activity_api35.png | The scaffolded app's single screen, `MainActivity` showing the app name, on the API 35 (Android 15) emulator | #1 |
| 02 | 02_scaffold_main_activity_api26.png | The same screen on the API 26 (Android 8.0) emulator, the minimum SDK | #1 |
| 03 | 03_scaffold_project_tree_android_studio.png | The project in Android Studio's *Android* view: under `com.btk.spm` the six top-level packages (`data`, `domain`, `notifications`, `settings`, `ui`, `util`) and `SpmApplication`, with the `androidTest` and `test` source sets below | #1 |
| 04 | 04_theme_placeholder.png | The placeholder screen in `Theme.Spm`'s light scheme on API 35: the app name in the leaf-green primary role and the tagline in the on-surface-variant role, both sized by `TextAppearance.Spm.*` | #2 |
| 04 | 04_theme_placeholder_dark.png | The same screen after switching the emulator to dark mode with the app open: the same theme, re-coloured from `values-night/colors.xml`, with no restart | #2 |
| 05 | 05_launcher_icon.png | The app's own adaptive launcher icon in the API 35 app drawer, a jar with an amber lid and a leaf on the brand green, beside the system icons | #2 |
