# Translations

Dustvalve Next ships in **English (default), German, Spanish, French, Italian,
Brazilian Portuguese, Japanese, Simplified Chinese, and Russian**.

## State of the translations

All non-English strings are **machine-translated** (by Claude, reviewed for
placeholder/plural correctness by lint and CI, but not by native speakers).
Corrections from native speakers are very welcome — send a PR touching the
relevant `app/src/main/res/values-<lang>/strings.xml` / `plurals.xml`.

## How users pick a language

There is no in-app picker. Android's per-app language setting is used instead:
**Settings → System → Languages → App languages → Dustvalve Next** (the
`LocaleConfig` is generated at build time by AGP from the `values-*`
directories plus `res/resources.properties`).

## Rules for adding or changing strings

1. Every user-visible string lives in `app/src/main/res/values/strings.xml`
   (or `plurals.xml`) — `HardcodedText` lint is at error severity.
2. A new default string must land **with all 8 translations in the same
   change**; `MissingTranslation` is at error severity, so lint fails
   otherwise. Machine-translate at PR time if you have to.
3. Placeholders: positional only (`%1$s`), wrapped in
   `<xliff:g id="..." example="...">` in the default file. Translations must
   keep the placeholder and any brand names inside `xliff:g` verbatim —
   `StringFormatMatches` and friends enforce parity.
4. Plural quantity sets differ per language (ru: one/few/many/other;
   ja/zh: other only; de: one/other; es/fr/it/pt: one/many/other) —
   `MissingQuantity` enforces them.
5. Typography: typographic quotes/apostrophes (`’ “ ” « » 「」`), real
   ellipsis `…`, en dash for ranges — the Typography* lint checks are errors.
6. Never concatenate sentence fragments in Kotlin; add a pattern string.
   For dynamic UI text in ViewModels use `UiText` (`core/common`).

## Adding a new language

1. Create `app/src/main/res/values-<qualifier>/strings.xml` + `plurals.xml`
   (translate everything not marked `translatable="false"`).
2. Add the qualifier to `localeFilters` in `app/build.gradle.kts`.
3. Add the locale to `LocaleScreenshotTest` and run
   `./gradlew :app:recordRoborazziDebug` to record baselines.
4. Run `./gradlew lintRelease testDebugUnitTest` — lint validates the file.

## Testing

- `./gradlew :app:verifyRoborazziDebug` — screenshot-diffs a locale showcase
  (all shipped locales + `en-rXA` expansion and `ar-rXB` RTL pseudolocales).
- Debug builds have pseudolocales enabled: pick "English (XA)" or
  "Arabic (XB)" in developer settings to eyeball expansion/RTL on device.
