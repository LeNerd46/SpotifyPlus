# React UI targets

## Implementation status

The React target API, isolated surfaces, native `Original`, diagnostics, and persistent extension ordering are implemented for Spotify **9.1.82.2160**. This delivery prioritizes usable screen coverage over exhaustive layout testing. A type name is not an availability guarantee: always use `inspect()`.

| Targets | Operations | Boundary |
| --- | --- | --- |
| `home.page`, `search.page`, `library.page` | replace, overlay | Spotify Tome page wrapper |
| `playlist.page`, `album.page`, `artist.page` | replace, overlay | Spotify Tome page wrapper |
| `artist.discography.page`, `settings.page`, `profile.page` | replace, overlay | Tome page identity/parameters; settings includes native section pages |
| `nowPlaying.page` | replace, overlay | XML activity content container |
| `lyrics.page` | replace, overlay | Tome full-screen lyrics page or activity ComposeView; activity snackbar stays outside |
| `navigation.drawer` | replace, before, after, overlay | Drawer Compose root with native profile, actions, and content parts |
| `contextMenu.root` | replace, before, after, overlay | Loaded menu Compose root with header and native action parts |
| `contextMenu.header`, `contextMenu.action` | replace, before, after, overlay | Spotify Compose element renderers |
| `miniPlayer.root` | replace, overlay | Non-embedded mini-player content inside its fragment root |

Page adapters retain fragment roots and original page-view identity. Use a replacement layout containing `Original` to wrap existing Spotify content. Page insertions are not advertised. Bindings target the observed phone layouts; unsupported container variants are skipped. Attached native content moves between module-owned slots without a window-detach transition, because Spotify's Home page loader destroys its content on ordinary detachment.

Queue, the navigation bar, and individual page headers, controls, sections and recycled items remain unavailable. The extension-owned native adapter SDK and general Compose-tag discovery are not implemented. Selector syntax is typed, but only explicit aliases resolve: currently resource ID `com.spotify.music:id/content` on screen `nowPlaying.page`.

Open Spotify Plus Settings → **UI extension order** to move extensions up or down. Order persists across restarts and takes effect immediately. Extensions with active UI registrations appear; unlisted extensions follow the saved order deterministically.

## Extension API

```tsx
import { SpotifyPlus } from 'spotifyplus';
import { View, Text } from 'spotifyplus/react';

const registration = SpotifyPlus.UI.replace('contextMenu.header', ({ context, Original }) => (
  <View style={{ padding: 16 }}>
    <Original />
    <Text>{context.uri ?? context.title}</Text>
  </View>
));

// Optional early removal. Extension unload removes registrations automatically.
registration.dispose();
```

`replace`, `insertBefore`, `insertAfter`, and `overlay` return disposable handles. Returning `null` from a replacement hides that region. Insertions receive context, but may not mount `Original`. The first replacement in extension priority order wins; insertions coexist. `inspect(target)` reports supported operations, active instance count, and replacement conflicts. `listTargets()` reports installed adapters.

Each live target has its own `context.instanceId`. Context changes update the existing component, preserving React state. Each mount has a separate native surface identity; commits and events from disposed mounts cannot reach new instances. Context values such as URI may be `null` when Spotify does not expose them at the boundary. Use the existing Player API for playback actions.

`Original` is live native content, not a screenshot. Mount it once within its target. While continuously mounted, it receives Spotify's composition updates. Removing it or changing replacement ownership can reset native composition state. Moving it to another target or mounting it twice is unsupported and restores the native target with an error.

Existing `Surfaces`, lyrics, drawer and context-menu action APIs retain their existing entry points.

### Drawer and menu parts

`context.parts` lists the live parts of `navigation.drawer` and `contextMenu.root`. Each entry has an opaque `id`, a `semanticId` (native action ID, destination, or content identifier), a localized `title` when available, `kind: 'action' | 'content'`, and `enabled`. Drawer content includes Spotify's profile and embedded elements such as messaging when Spotify supplies them. The list represents the currently loaded native model; opening another menu or updating the drawer can change it. `UI.listInstances(target)` returns current contexts even without a replacement registration.

```ts
const drawers = await SpotifyPlus.UI.listInstances('navigation.drawer');
const parts = drawers[0]?.context.parts ?? [];
```

Use `NativePart` to render selected rows with their native icons, badges, interaction, and internal state. Mount each part once in its own instance. Use a scrolling container for a long custom layout. `Original` retains the entire original layout; avoid duplicating its rows with `NativePart`.

`NativePart` uses the original row renderer directly. Individual `contextMenu.header`/`contextMenu.action` contributions apply inside the full `Original` menu, rather than replacing a row explicitly leased as a native part.

```tsx
SpotifyPlus.UI.replace('navigation.drawer', ({ context, NativePart }) => (
  <ScrollView style={{ flex: 1 }}>
    <View style={{ width: '100%' }}>
      <Text>My drawer</Text>
      {(context.parts ?? []).map(part => <NativePart key={part.id} id={part.id} />)}
    </View>
  </ScrollView>
));
```

Import `ScrollView`, `View`, `Text`, and `Button` from `spotifyplus/react`. A `ScrollView` takes one wrapper `View` containing its rows. For an entirely custom button, `UI.invokeAction(instanceId, partId)` invokes the part's live native click handler, including dismissal, navigation, and secondary dialogs/menus. For example, invoking Spotify's Sleep timer action opens its native duration picker. Its timers continue through Spotify's action/effect pipeline. This also works when that row is omitted from the custom layout.

```tsx
SpotifyPlus.UI.replace('contextMenu.root', ({ context, NativePart }) => (
  <ScrollView style={{ flex: 1 }}>
    <View style={{ width: '100%' }}>
      {(context.parts ?? []).map(part => part.kind === 'content'
        ? <NativePart key={part.id} id={part.id} />
        : <Button key={part.id} text={part.title ?? part.semanticId} disabled={!part.enabled}
            onPress={() => SpotifyPlus.UI.invokeAction(context.instanceId, part.id)
              .catch(error => console.error(error))} />)}
    </View>
  </ScrollView>
));
```

Always use the current `part.id`; do not store it between model updates or reuse it in another menu. Closed instances, expired IDs, disabled/content parts, and unloaded callers reject action invocation. A native action must have its handler ready; a hidden native element can be composed to obtain it without blocking Android or Node. Resolution acknowledges dispatch, not completion of an asynchronous native flow. For extension-owned submenus, a button can set React state and render another list in the same replacement; invoke a native part when the next menu belongs to Spotify.

Custom pages can open native UI using `SpotifyPlus.SideDrawer.open()`, `SpotifyPlus.ContextMenu.open(item, { contextUri? })`, and `SpotifyPlus.ContextMenu.openNowPlaying()`. These return promises; catch failures in your press/long-press handlers. Item menus accept track, album, artist and playlist URIs, URLs, or objects with a `uri`. The Now Playing helper invokes that screen's native button action. The bundled showcase demonstrates drawer, ⋮ and long-press controls. See `modern-api.md` for readiness requirements and device checks.

To expand a custom mini-player, use `SpotifyPlus.Navigation.openSpotify('spotify:now-playing')`. On Spotify 9.1.82.2160 this invokes Spotify's native Now Playing activity intent factory (`p.ig5.C`) with shared-element transitions disabled, since the replacement has no native transition source. `spotify:now-playing-view` uses the same path. This navigation launches the full screen, where any `nowPlaying.page` replacement applies.

## Native implementation

The menu adapters intercept `p.axj.f`/`h`/`e` and the drawer's `p.d711.d1` renderer, using Spotify's own `p.q7a1.a` AndroidView boundary. A nested Spotify `ComposeView` inherits the remembered parent composition context and invokes the original rendering function with a fresh composer. Part rendering retains the original action/element models and drawer instrumentation. The intercepted composer is never passed to JavaScript or retained for an asynchronous callback. Module Compose classes are not used at this boundary.

The native fallback remains present during asynchronous React startup. A native content slot acquires its view after all operations in a commit have been applied. Unsupported bindings leave the original renderer active. Native commit failures dispose the failed host and restore the original. JavaScript render failures are isolated by contribution boundaries.

## Validation

From `app/src/main/assets/nodejs-src`, run the runtime build before `npm run test:ui` and `npm run test:sdk-types`. Tests cover independent instances, retained React state, replacement priority, stale callbacks, unload during pending attachment, close-before-attachment, parts isolation/discovery, and action expiry. `:app:testDebugUnitTest` covers page route classification, including legacy playlist/profile ambiguity and artist subpages.

Device checks on a Samsung SM-S931U running Android 16 with Spotify 9.1.82.2160 passed replacement markers for all seven new page targets. The drawer rebuilt 16 native parts, including messaging; native profile navigation, a custom Settings button, and switching to/from `Original` passed. A Now Playing menu rebuilt 21 parts; its custom Sleep timer button opened Spotify's native duration picker without selecting a duration, and native Go to artist navigation and original-menu restoration passed. These checks verify opening/dismissal of the picker, not timer expiration or every secondary menu variant.

For the page/parts debug fixture, create `spotifyplus-ui-parts-probe` in Spotify's external app files directory and restart Spotify. It marks the seven new pages, rebuilds the drawer/menu from native parts, and supplies custom buttons for Settings and Sleep timer. Its Home overlay can reopen Now Playing without starting playback. No action runs automatically. Check each page, the drawer's messaging region, native and custom action buttons, the sleep-duration picker, back/dismissal, model refresh, and switching to `Original`. Expand the native menu sheet using its drag handle when necessary. Remove the marker and restart to restore normal layouts. Release builds ignore both probe markers.

The debug integration fixture is opt-in: create the empty file `spotifyplus-ui-probe` in Spotify's external app files directory, then restart Spotify with the debug module enabled. Remove the file and restart to disable it. Release builds never load the fixture.

Build/install the debug app and `debugAndroidTest` APKs, open an album context menu, and run:

```sh
adb shell "am instrument --user 0 -w -e subtitle 'Harry Styles' -e class com.lenerd.spotifyplus.ui.ComposeHeaderProbeTest com.lenerd.spotifyplus.test/androidx.test.runner.AndroidJUnitRunner"
```

Replace the subtitle argument with the opened menu's actual native subtitle. This test checks React accessibility, insertion order, overlay mounting, state updates, removal/restoration of `Original`, and native recovery after a deliberate render failure. It preserves existing accessibility services. A separate manual check exercises Spotify's native Go to artist action and menu dismissal. These checks do not constitute validation of unimplemented targets or other device/layout variants.
