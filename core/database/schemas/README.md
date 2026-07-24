# Room schema export

Room's KSP compiler writes the JSON schema of every `@Database` version into
this directory (`room.schemaLocation` in `core/database/build.gradle.kts`).

The directory is populated at the next build (a
`com.dustvalve.next.android.data.local.db.DustvalveNextDatabase/<version>.json`
file per schema version) and the generated files MUST be committed. They are
the source of truth for writing `Migration`s when the schema version bumps,
and release builds have no destructive-migration fallback: a version bump
without a matching migration crashes on open instead of wiping user data.
