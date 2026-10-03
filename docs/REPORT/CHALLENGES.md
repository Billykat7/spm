# Challenges

Real problems met while building the app, written when they were fresh: what appeared, why, the fix
and its commit, and what I learned. Two or three of these become section 7 of the report.

## 2026-10-03 · Issue 10 · A newer lifecycle library broke Room's compiler

**Problem:** the first `@Transaction` method in `RecipeDao` (`insertWithIngredients`, a default
method) stopped the build with `Provided Metadata instance has version 2.1.0, while maximum supported
version is 2.0.0`. `PantryItemDao`, which also returns `LiveData`, had compiled fine earlier the same day.

**Cause:** in Issue 8 I had moved lifecycle from 2.10.0 to 2.11.0 because it was the newest stable
release and asked only for compileSdk 34. Lifecycle 2.11's `LiveData` is compiled with Kotlin 2.1, so
the class carries version 2.1 Kotlin metadata. Room 2.6.1's annotation processor checks the return
type of every `@Transaction` method, and to do that it reads the metadata of `LiveData` with a bundled
`kotlinx-metadata-jvm` that understands at most 2.0. The stack trace ends in
`TransactionMethodProcessor.process`. A plain `@Query` returning `LiveData` takes another path, which
is why `PantryItemDao` never hit it. Removing the default method made the error go away, and so did
going back to lifecycle 2.10.0 with the method in place. Both builds resolve `kotlin-stdlib` 2.2.10,
so the standard library was not the cause.

**Fix:** lifecycle back to 2.10.0, the version Material 1.14 and Fragment 1.9 resolve anyway, with the
reason in `gradle/libs.versions.toml` and a narrow `lint.xml` entry for Lint's "newer version" notice
(commit `82ea76c`, `Issue 10: hold lifecycle at 2.10.0, because Room 2.6.1's processor cannot read
the Kotlin 2.1 metadata on lifecycle 2.11's LiveData`, merged in pull request #49). Room stays at 2.6.1, as decision 1 pins it. Moving to Room 2.7
or later would lift the limit, and lifecycle can move with it.

**Learned:** an annotation processor reads the libraries on the classpath too, not only my own code,
so a library bump can break the build in code that does not change. "Newest stable" is not a reason
on its own; I now upgrade a library only together with the tool that reads it, and run the full
build, including a DAO with every kind of method, before calling it safe.
