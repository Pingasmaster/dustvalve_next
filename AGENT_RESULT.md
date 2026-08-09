# AGENT_RESULT - detekt mechanical fixes

Branch: `wip/detekt-mechanical`
Worktree: `/home/user/dustvalve_next_detekt_mech`

## Verdict

Owned mechanical detekt issue classes are cleared. `:app:compileCompatDebugKotlin` succeeds.
`:app:detektCompatDebug` still fails on LongParameterList / ViewModelForwarding / PlayerLibraryCoordinator NoNameShadowing (other agents).

## Commit

(See git log on this branch after commit.)

## Fixed rules

| Rule | Fix |
|------|-----|
| UnusedPrivateProperty / UnusedParameter | Compat stubs: keep Context for DiagnosticsInitializer API parity, drop `private val`, `@Suppress("UnusedPrivateProperty")`. DownloadController / PlaybackManager / SearchViewModel / SoundCloudViewModel / AlbumThemeManager: constructor params used only in property inits are non-`val` params. Removed unused `getAlbumDetailUseCase` from AlbumDetailViewModel. Removed unused `navViewModel` from `AppNavigationDestination`. |
| AbstractClassCanBeInterface (+ follow-on OptionalAbstractKeyword) | Hilt `@Module` binds-only types -> `interface` (compat+future AudioPowerModule; DownloadReportingModule, MediaCacheModule, RepositoryModule, SoundCloudModule, YouTubeModule, YouTubeMusicModule); dropped redundant `abstract` on interface members. |
| InjectDispatcher | `@Suppress("InjectDispatcher", "RawDispatchersUse")` on DispatchersModule Provides (binding site; cannot inject what it provides). No detekt.yml change. |
| ImplicitDefaultLocale | AppUpdateService hex digest uses `Locale.US`. |
| MissingReadOnlyComposable | `@ReadOnlyComposable` on AppShapes getters that only read `MaterialTheme.shapes.*` (not MaterialShapes.toShape()). |
| UnnecessaryComposable | Removed `@Composable` from `soundCloudSearchInputField` (returns a composable lambda only). |
| NoNameShadowing | ReorderableMusicList: named `entry` in nested `indexOfFirst` lambdas. |
| TooGenericExceptionCaught (baselined) | AppUpdateController migrated to `runCatchingUi` / `runCatchingUiIgnore` / `runCatchingUiIgnoreSync`; removed AppUpdateController baseline ID. |

## Remaining owned failures

None.

## Remaining detekt failures (out of scope)

LongParameterList / ViewModelForwarding / PlayerLibraryCoordinator NoNameShadowing and related screen hotspots left for other agents.

## Verify

```
./gradlew :app:detektCompatDebug :app:compileCompatDebugKotlin
```

- compileCompatDebugKotlin: SUCCESS
- detektCompatDebug: FAIL (other agents' issues only; owned rules absent)
