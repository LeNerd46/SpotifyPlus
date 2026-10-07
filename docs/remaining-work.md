# SpotifyPlus remaining work

Codebase review: October 1, 2026. This reviews the **current working tree, including existing staged, unstaged, and untracked work**, rather than just the last commit. Target Spotify version: **9.1.82.2160**.

This is a source-backed backlog, not a claim that every possible runtime defect has been found. **Confirmed** means the gap is visible in source or a local check failed. **Validate** means implementation exists but needs a real hooked device or a specific build check. **Expansion** means an optional product decision, not a required bug fix. Priority indicates suggested sequencing: P1 before calling the current feature set dependable; P2 next; P3 optional expansion/cleanup.

## Where the project stands

The project has progressed well beyond the original native lyrics overlay. It now has an embedded Node host, a React renderer, extension loading and asset APIs, worklet/native animation tooling, local extension development, a marketplace installer, and an authenticated Spotify service bridge.

Player commands, queue edits, library operations, playlists, search, user profile, Connect, clipboard, native event broadcasting, context-menu predicates, UI replacement ordering, and several React screen adapters already have implementations. They should be validated and stabilized rather than rebuilt from scratch.

The clearest unfinished areas are the generated npm SDK, the migration to the React Beautiful Lyrics extension, the standalone manager's demonstration behavior, and release/device verification. The working tree also contains substantial work that still needs to be committed with its new source files and tests.

## Local verification

| Check | Result |
| --- | --- |
| Runtime `npm run build` | Passed; TypeScript runtime, SDK bundle, and runtime dependency packaging completed |
| Runtime `npm test`, rerun after build | 55 passed |
| `npm run test:ui`, rerun after build | 12 passed |
| All `tools/tests/*.test.mjs`, including marketplace metadata | 68 passed |
| `npm run test:sdk-types` | **Failed**: three unresolved metadata types and an incompatible repeat-mode literal |
| `:app:testDebugUnitTest` | 10 passed, including one template example test |
| `:app:lintDebug` | **Failed**: 2 errors, 269 warnings |
| Settings `npm run check` | **Failed**: SideDrawer constructor argument mismatch |
| Marketplace `npm run check` | **Failed**: missing reanimated import declarations and SideDrawer constructor mismatch |

These JS tests include host-side and mock checks; their success does not establish Spotify hook compatibility or native layout correctness. No APK was installed, no Spotify service mutation was performed, and no connected-device instrumentation was run during this review. A release APK and a clean-machine build were not verified. Build commands regenerate ignored outputs; this review does not fix implementation files.

## P1 — SDK and build correctness

1. [ ] **Confirmed: fix generated SDK metadata declarations.** `npm run test:sdk-types` cannot resolve `MetadataAlbum`, `MetadataArtist`, and `MetadataPlaylist` in `dist/internal/script-api.d.ts`. `tools/build-sdk-package.mjs` extracts exported declarations but its manually composed imports omit these model types. Preserve their imports/exports through packaging and exercise the public consumer fixture without suppressing declaration errors. Sources: `app/src/main/assets/nodejs-src/tools/build-sdk-package.mjs`, `loader/script-api.ts`, `core/models.ts`.

2. [ ] **Confirmed: unify repeat-mode values across JS, Java, consumers, and docs.** The JS type permits `off`, `repeat`, and `repeat-one`; Java accepts/emits `off`, `context`, and `track`. The SDK fixture fails on `context`. Currently a developer following the JS type can send values Java rejects. Choose one public contract or translate deliberately at the bridge, and cover commands, state, cycleRepeat, and repeatChanged. Sources: `loader/script-api.ts:58`, `SpotifyServices.java`, `docs/modern-api.md`.

3. [ ] **Confirmed: include Beautiful Lyrics in the intended build pipeline.** `buildLyricsExtension` exists, but `buildBuiltInExtensions` depends only on Settings and Marketplace. On this checkout the generated `nodejs/scriptss` lyrics output was absent. Connect the task if lyrics should ship by default; otherwise document how the primary advertised feature is installed. Sources: `app/build.gradle.kts`, `scripts/lyrics/package.json`, module `scripting/ScriptManager.java`.

4. [ ] **Confirmed: rebuild the native lyrics plugin from source as part of its packaging.** The lyrics JS build copies the declared `lyrics.apk`; `build:native` is a separate command. The top-level lyrics task declares native source inputs but does not invoke the native build. Ensure Java/native changes produce a fresh plugin APK and avoid depending silently on the checked-in binary. Sources: `scripts/lyrics/tools/build-native.mjs`, `scripts/lyrics/package.json`, `app/build.gradle.kts`.

5. [ ] **Validate: make release packaging use the matching native outputs.** Native addon synchronization is attached to `mergeDebugAssets`, depends on `externalNativeBuildDebug`, and reads debug output paths. The old release JNI dependency is commented out. Verify a fresh `assembleRelease` contains current ABI-specific `.node` addons and libraries; refactor into per-variant tasks where needed. Also check the Copy task's ABI destination handling, because it repeatedly configures a single `into(...)`. Sources: `app/build.gradle.kts`, `app/src/main/cpp/CMakeLists.txt`.

6. [ ] **Confirmed: integrate all relevant verification into an automated gate.** The npm default test list excludes UI tests and `marketplace-metadata.test.mjs`; SDK type checks are separate. No `.github` CI directory is present. Add one documented verification command or CI workflow that builds the runtime, runs runtime/UI/metadata tests, checks both SDK consumers, and runs Android unit tests/lint. SDK checks would have caught items 1–2 even while all runtime tests passed. Source: runtime `package.json`.

7. [ ] **Confirmed: resolve the two Android lint errors.** `fingerprint/TargetType.java:59` uses `Stream.toList()`, which lint identifies as requiring API 34 while minSdk is 30. Use an API-compatible implementation or a verified desugaring solution. `LocalExtensionHook.java:174` passes flags that lint cannot establish as the allowed read/write grant constants to `takePersistableUriPermission`; use explicitly valid flags and verify persisted access on supported versions. These errors make the combined Gradle verification run fail even though compilation and unit tests succeed.

8. [ ] **Confirmed: align bundled extension development types with the runtime SDK.** Settings and Marketplace fail their own `npm run check`. Both pass a third SideDrawer argument that their installed SDK declarations do not accept; Marketplace also cannot resolve `spotifyplus/react/reanimated`. Their package dependencies select registry SDK versions (`latest` and `^0.1.9`), while the runtime builds a new local SDK. Make type resolution use the intended compatible SDK, then run these checks in the build gate. Successful esbuild bundling does not prove type compatibility. Sources: both extension `package.json` files, `src/index.tsx`, Marketplace `src/app.tsx`.

## P1 — Finish the lyrics migration

9. [ ] **Confirmed: repair and validate response selection.** React lyrics submits one query but reads `json.queries[1]`; the older Java parser reads index 0. Verify the actual provider payload and select the matching operation, with schema validation and a clear no-lyrics state. Do not assume an undefined payload is valid input to `transformLyrics`. Source: `scripts/lyrics/src/app.tsx:82`.

10. [ ] **Confirmed: reload lyrics when the track changes.** The fetch effect has an empty dependency array, so metadata/lyrics are loaded only at mount. Subscribe to song changes, reset the previous song's content, and prevent older requests from overwriting a newer song. Cover duplicate occurrences, no active track, rapid skips, and unmount during a request. Source: `scripts/lyrics/src/app.tsx`.

11. [ ] **Confirmed: add bounded fetching, retry, and readable failure states.** The current effect has no cancellation or explicit request deadline, no retry control, and assumes the response shape. Handle offline mode, expired authentication, HTTP errors, absent lyrics, malformed JSON, and provider errors independently. Keep the hard-coded provider client version in one place and verify it on a device; this review did not query the provider. Source: `scripts/lyrics/src/app.tsx`.

12. [ ] **Confirmed: reconnect user lyrics settings to the React implementation.** Java retains font, line spacing, background quality, gradient, animation style, interlude duration, and Apple-style scrolling preferences. The active React lyrics source does not register extension settings or read those Java settings; its font/layout choices are fixed. Decide which settings remain supported and wire them through extension storage/settings events. Sources: `SpotifyPlusSettings.java`, `scripts/lyrics/src/theme/fonts.ts`, `scripts/lyrics/src/Lyrics/lyrics-view.tsx`, `scripts/settings/src/app.tsx`.

13. [ ] **Validate: complete activity/surface lifecycle behavior.** LyricsHook registers on activity `onCreate` and sends close on `finish`; it does not hook destruction. Verify rotation, activity recreation, background/process recovery, close-before-mount, reopening, and returning from another screen. Ensure ReactManager's surface/root references and native hosts are released in every exit path. Sources: `LyricsHook.java`, `ReactManager.java`, `SpotifyNativeBridge.java`.

14. [ ] **Validate: test the lyrics interaction and animation matrix.** Exercise static, line, syllable, interlude, opposite-aligned/background vocals, seeking by line, pause/resume, remote seeks, long songs, and manual scrolling. `lyrics-view.tsx` polls every 250 ms and scrolls to each active line without an explicit user-scroll pause; decide whether auto-follow needs a user-controlled timeout/reset. Check cold-open performance, frame rate, and memory with the native animated background. Sources: `scripts/lyrics/src/Lyrics/lyrics-view.tsx`, `Entities/`, `src/native/`.

## P1/P2 — Make the standalone manager truthful and functional

15. [ ] **Confirmed, P1: replace synthetic hook status with actual health.** `moduleEnabled` defaults true; `refreshStatus()` counts enabled UI entries rather than successful hook initialization. Hook setup exceptions are logged but not reported to the manager. Surface framework availability, Spotify process connection, runtime readiness, per-hook success/failure, and unsupported-version status separately. Sources: `manager/model/AppModels.kt`, `manager/viewmodel/ManagerViewModel.kt`, `module/SpotifyHook.java`.

16. [ ] **Confirmed, P1: wire toggles to real persisted settings and extension state.** Hook/script toggles and enable/disable-all currently change only ViewModel state. The list contains demonstration entries such as a Beautiful Lyrics toggle whose ID is `lastfmUsername`. Replace it with real hook/extension data and define whether each change applies immediately or after Spotify restart. `SettingsSync` exists, but these handlers do not write to it. Sources: `ManagerViewModel.kt`, `SettingsSync.java`, `MainActivity.kt`.

17. [ ] **Confirmed, P1: fix update detection and its UI states.** After stripping `v`, the code strips another first character with `substring(1)`, corrupting version comparison. It also immediately inserts fake `1.0.0 → 1.1.0` update data while the request is pending and substitutes `Hello` for a changelog. Parse the version once, use real release data, close responses, catch malformed payloads, marshal UI updates appropriately, and distinguish loading/error/up-to-date. Source: `ManagerViewModel.kt:106`.

18. [ ] **Confirmed, P1: implement update installation or remove the claim.** `installUpdate()` only changes displayed versions and says installation succeeded. Implement selecting/downloading a release APK and handing off to Android's installer, or make the button open the verified release page. Report success only after actual installation is established. Sources: `ManagerViewModel.kt`, `manager/ui/screens/UpdatesScreen.kt`.

19. [ ] **Confirmed, P2: persist and honor manager preferences.** Auto-check, startup-check, debug logging, and theme only update ViewModel fields. MainActivity always checks updates on creation. The theme composable follows system/dynamic colors and does not receive `uiState.theme`. Implement settings persistence and connect each switch to the behavior it describes. Sources: `ManagerViewModel.kt`, `MainActivity.kt`, `manager/ui/theme/Theme.kt`.

20. [ ] **Confirmed, P2: settle the manager/module communication design.** Manager Node startup is commented out in MainActivity; BridgeService returns early if NodePacketSink is absent. SettingsReceiver has its write logic commented out and is not declared in the manifest. Decide which path is active, finish status/settings messages over it, and retire unused experiments. If the localhost bridge is retained, cover reconnect and bound its unbounded outbound queue. Sources: `MainActivity.kt`, `SettingsReceiver.java`, `manager/bridge/BridgeService.java`, `BridgeClient.java`.

## P2 — Marketplace and extension development

21. [ ] **Confirmed: handle catalog/search request failures safely.** `fetchRepos` and `searchRepos` catch network failure into `null` then dereference `repos.items`. The initial catalog effect lacks a rejection handler. Guard nullable/malformed responses and corrupted cache data; surface retry/error states instead of a crash or permanent Loading screen. Sources: `scripts/marketplace/src/fetch-metadata.ts`, `src/app.tsx`.

22. [ ] **Confirmed: expire or refresh repository-list caching.** Manifest caching has a 15-minute TTL, but `page-N` repository cache does not. A saved first page can indefinitely hide new extensions and preserve stale repository data. Add a list TTL/invalidation and user refresh behavior. Source: `scripts/marketplace/src/fetch-metadata.ts`.

23. [ ] **Confirmed: tolerate optional author URLs.** The schema permits an author without `url`; `formatAuthors` nevertheless calls `sanitizeUrl(author.url)`, which calls `decodeURI(...).trim()`. An omitted or malformed URL can discard an otherwise valid manifest through the outer catch. Make absent URLs valid and handle malformed encoding locally. Source: `scripts/marketplace/src/fetch-metadata.ts`.

24. [ ] **Confirmed: finish catalog pagination and loading behavior.** The app requests only page 1 despite the helper accepting a page argument, and retrieves each repository manifest sequentially. Add paging where needed, bounded concurrent manifest retrieval, response-status/rate-limit handling, and cancellation/stale-result guards. Source: `scripts/marketplace/src/app.tsx`, `fetch-metadata.ts`.

25. [ ] **Validate: test installation, update, rollback, uninstall, and restart.** The installer already pins files to a commit, validates paths, stages writes, and attempts rollback; it is not just a button stub. Verify asset/native APK delivery, API incompatibility, duplicate IDs, an extension failing at load, partial download, interrupted installation, rollback reactivation, and persistence across restart on-device. Add regression tests for the host installer transaction paths, beyond the existing metadata test. Sources: `scripts/marketplace/src/install-extension.ts`, runtime `loader/host-runtime.ts`.

26. [ ] **Confirmed: replace folder-picker timing guesses with a completion event.** Settings refreshes 1.2 and 3 seconds after opening the picker; a later user selection can miss both. Emit completion/cancellation from LocalExtensionHook and update the settings screen from that result. Sources: `scripts/settings/src/app.tsx`, `module/hooks/LocalExtensionHook.java`.

27. [ ] **Confirmed: make local-extension refresh failure preserve the previous cache.** `refreshLocalExtensions()` deletes the existing cache before attempting the new copy. A revoked permission or interrupted copy can leave no working extensions. Stage copies and swap only on success, report errors to the UI, and handle duplicate/sanitized folder names deterministically. Source: `LocalExtensionHook.java`.

28. [ ] **Validate: define developer-mode changes during a running session.** Startup captures developerMode in HostConfig. Confirm enabling/disabling and refreshing actually loads/unloads the intended scripts and updates hot reload permissions; otherwise expose a clear restart requirement. Check revoked storage permissions and large folder scans so synchronous copying does not stall the UI. Sources: `LocalExtensionHook.java`, module `scripting/ScriptManager.java`, runtime `loader/host-runtime.ts`.

29. [ ] **Expansion: improve settings input controls where useful.** Extension settings already render many types, but file/directory settings accept manually typed paths and date/time are text inputs. Native pickers and validated formats would make these practical for Android users. This is polish, unless an extension currently depends on accessible picker results. Source: `scripts/settings/src/settings-components.tsx`.

## P2 — Hook compatibility and service validation

30. [ ] **Confirmed: finish or explicitly limit fingerprint adoption.** A resolver/cache and tests exist, and ContextMenuHook uses a named/DexKit mapping for its model. Most surrounding obfuscated dependencies and other hooks remain hard-coded. Prioritize independent fingerprints and validators for startup/player/network/navigation/service/UI dependencies if cross-version support is intended. The mere presence of the resolver does not establish broad compatibility. Sources: `module/fingerprint/`, `ContextMenuHook.java`, other active hooks.

31. [ ] **Confirmed: update the compatibility documentation to the actual implementation.** `docs/modern-api.md` says active hooks have no fingerprint lookup, whereas ContextMenuHook now resolves its model through the new resolver. Document the exact supported version and partial fingerprint coverage, and add a runtime mismatch message instead of relying solely on logs. Sources: `docs/modern-api.md`, `docs/fingerprints.md`, `readme.md`.

32. [ ] **Validate: run the full Spotify service checklist.** Cold-start and use player commands before opening lyrics; test queue duplicate occurrences/stale revisions; use a disposable playlist for ordering and row-ID edits; verify library pagination/membership, authenticated search, profile and metadata, clipboard foreground restrictions, Connect transfer out/back, and offline/account-denied failures. Check emoji and URI normalization over JNI. Sources: `SpotifyServices.java`, `SpotifyMetadataModels.java`, `docs/modern-api.md`.

33. [ ] **Validate: verify multi-extension events and menu behavior.** Test independent listeners, failed listener isolation, unload cleanup, native versus API-driven changes, repeated occurrences of the same song, remote seeking, context-menu entity filters, exceptions/slow predicates, and the visibility timeout. Current tests cover some logic, but live Spotify callbacks require a hooked device. Sources: `PlayerHook.java`, `ContextMenuHook.java`, runtime event/registry code.

34. [ ] **Confirmed: recover cleanly from runtime startup failure.** `ScriptManager.start()` sets `nodeStarted = true` before asset extraction and returns on extraction failure without resetting it. Later calls cannot retry. Track starting/ready/failed states separately, expose the failure, and permit recovery before Node has actually launched. Also verify module upgrades remove stale extracted assets, since the copier overwrites present files but does not synchronize deletions. Source: module `scripting/ScriptManager.java`.

35. [ ] **Validate: decide the fate of animated album artwork.** Its setting defaults enabled, but its hook is commented out in the modern loader. Update and validate it before enabling, or stop exposing the setting as an available feature. Sources: `SpotifyPlusSettings.java`, `SpotifyLoader.java`, `hooks/AnimatedAlbumArtwork.java`.

## P2/P3 — React UI targets

36. [ ] **Validate, P2: test the adapters that already exist.** Current implemented coverage is Home/Search/Library/Playlist/Album/Artist pages, Now Playing page, mini-player root, and context-menu header/action. Exercise replacement/overlay, Original restoration, context updates, priority conflicts, unload, two simultaneous instances, back navigation, accessibility, dark/light themes, font scaling, orientation, and native render failure. Use the ComposeHeaderProbe instrumentation instructions and add equivalent checks for the other surfaces. Source: `docs/ui-extensions.md`, `module/hooks/*UITarget*.java`, `UITargets.java`.

37. [ ] **Expansion, P3: implement the remaining named targets as needed.** The public catalog is broader than installed adapters. Remaining areas include page headers/controls/sections/recycled items, queue, lyrics targets, navigation, mini-player subregions, and context-menu root/action-list regions. Keep unsupported results explicit in `inspect()` and avoid promising availability merely because a string is typed. Sources: runtime `ui/target-catalog.ts`, `ui/target-api.ts`, `docs/ui-extensions.md`.

38. [ ] **Expansion, P3: finish selector discovery and an extension-native adapter SDK.** General Compose-tag discovery and extension-owned native adapters are documented as unimplemented. Only a verified resource alias for Now Playing content currently resolves. Define ownership, lifecycle, failure isolation, and supported layout boundaries before extending discovery. Source: `docs/ui-extensions.md`.

## P2/P3 — Release and repository housekeeping

39. [ ] **Confirmed, P2: checkpoint the current work with all new sources/tests.** The checkout contains many modified files and new fingerprint, service, UI, and test files. Make focused commits that include those files together; a patch containing only tracked changes would omit essential implementations. Review generated/binary assets separately. No staging or commits were made by this audit.

40. [ ] **Confirmed, P2: synchronize version and release metadata.** Android/npm identify `0.11.0.0`, Settings About hard-codes `0.10.0`, ManagerUiState retains an old latest-version default, and Android versionCode is 1. Use a shared source where practical and increment versionCode for actual Android releases. Verify signing, release notes, artifacts, ABI contents, and the supported Spotify version. Sources: `app/build.gradle.kts`, runtime `package.json`, `scripts/settings/src/app.tsx`, `manager/model/AppModels.kt`.

41. [ ] **Validate, P2: verify reproducibility of extension builds.** Settings and lyrics declare `spotifyplus: latest`; Marketplace declares `^0.1.9`. All must be compatible with the runtime's locally generated SDK. Document the native plugin build and verify wrapper/SDK/NDK/native-library requirements on a fresh checkout. Keep stable public import paths in example extensions. Sources: extension package files, `readme.md`, `scripts/ASSETS.md`.

42. [ ] **Confirmed/Validate, P2: resolve native alignment and triage substantive lint warnings.** Lint reports the bundled DexKit 2.0.0 `arm64-v8a/libdexkit.so` as not 16 KB aligned. Verify or replace that artifact for the devices the project intends to support and inspect the remaining native libraries in the actual APK. Triage static activity/view references, locale-dependent protocol strings, drawing allocations, dynamic-code loading, world-readable/writable files, accessibility, and the exported bridge receiver. Some warnings concern legacy or intentional hook behavior; do not treat all 269 warnings as independent bugs or blindly suppress them. Source: `app/build/reports/lint-results-debug.html` and `.xml`.

43. [ ] **Confirmed, P3: trim experiments and document the two SDKs.** Test/bookmark/demo scripts, the large commented-out Java lyrics renderer, retired hooks, manager Node experiments, and template tests remain. Decide what ships and what belongs in development fixtures. The Java AAR SDK currently exposes only the basic native player/context interface, while the npm SDK has a much larger API; document that distinction and rebuild the native plugin against its intended AAR. Sources: runtime `scripts/`, `LyricsHook.java`, `spotifyplus-sdk/`, `scripts/lyrics/src/native/lib/`.

## Suggested restart order

1. Checkpoint the current implementation and fix SDK declarations/repeat modes; make the verification command catch both.
2. Wire the lyrics JS/native build and fix fetching, track changes, settings, and lifecycle.
3. Make manager status/toggles/updates reflect real behavior.
4. Repair marketplace request/cache/author handling and local folder completion.
5. Validate APIs, lyrics, and implemented UI targets on Spotify 9.1.82.2160; verify fresh debug/release packaging.
6. Expand fingerprints/UI targets only after the current supported feature set is dependable.

## Useful starting points

- [SDK packaging](../app/src/main/assets/nodejs-src/tools/build-sdk-package.mjs), [public API](../app/src/main/assets/nodejs-src/loader/script-api.ts), [native services](../app/src/main/java/com/lenerd/spotifyplus/module/scripting/SpotifyServices.java)
- [Build tasks](../app/build.gradle.kts), [lyrics fetch/UI](../scripts/lyrics/src/app.tsx), [manager behavior](../app/src/main/java/com/lenerd/spotifyplus/manager/viewmodel/ManagerViewModel.kt)
- [Marketplace fetching](../scripts/marketplace/src/fetch-metadata.ts), [folder import](../app/src/main/java/com/lenerd/spotifyplus/module/hooks/LocalExtensionHook.java)
- [Device API checklist](modern-api.md), [UI implementation and device checks](ui-extensions.md), [local lint report](../app/build/reports/lint-results-debug.html)
