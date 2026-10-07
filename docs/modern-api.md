# Modern API: Spotify 9.1.82.2160

The `modern-api` branch targets **Spotify Android 9.1.82.2160**. Its active Spotify hooks use the class and method names from that release. There is no fingerprint lookup or cross-version fallback in these hooks. The retired legacy context-menu hook and diagnostic TestHook are no longer installed.

The SDK build exports these interfaces from the `spotifyplus` npm package and mirrors the runtime package into the APK. Build from `app/src/main/assets/nodejs-src` with `npm run build`. This does not publish to npm.

## APIs

Import `SpotifyPlus` from `spotifyplus`. Local operations return directly through the native C++/JNI bridge: player controls and state (except `playContext`), all Queue methods, library membership and edits (except `list`), playlist creation and edits (except `get`), Connect device snapshots, clipboard methods, and storage reads. Value getters return their value and commands return `void`; use `try/catch` for readiness, validation, revision, and service errors. Local RPCs wait for Spotify's acknowledgement, with the existing 15-second timeout; background playback, state events, and account synchronization may follow later. Calls do not enter the asynchronous API executor.

Promises remain for search, HTTP metadata/profile requests, `Library.list` (waits for a loaded stream), `Playlists.get` (decorated playlist loading), `Player.playContext` (may load a context), and `Connect.transfer` (coordinates devices). Native UI launch/inspection/action calls remain asynchronous because their Android main-thread work can load content and call extension handlers on Node. `Events.emit` awaits possibly asynchronous listeners, and `request` waits for a host response.

Remove `await` from local calls and replace `.then()`/`.catch()` chains with direct reads and `try/catch`. Existing `await` expressions still accept direct values, but they do not make local calls asynchronous.

| API | Operations |
| --- | --- |
| `Player` | `play`, `pause`, `togglePlay`, `skipNext`, `skipPrevious`, `seek`, `playContext`, `getState`, `setShuffle`, `toggleShuffle`, `setRepeat`, `cycleRepeat`; existing current-track/progress getters |
| `Queue` | `get`, `add`, `remove`, `move`, `clear` |
| `Library` | `list`, `contains`, `save`, `remove`, `like`, `unlike`, `isLiked` |
| `Playlists` | `create`, `delete`, `get`, `move`, `addTracks`, `removeTracks`, `moveTracks` |
| `Search` | `search(query, { limit?, locale? })` using Spotify's authenticated search service |
| `User` | `getCurrent()` returns username, display name, profile URI and images |
| `Connect` | `getDevices`, `getCurrentDevice`, `transfer` |
| `Platform.Clipboard` | `readText`, `writeText`, `clear` |
| `Internal` | `getTrack(uri)` now resolves track metadata asynchronously; missing tracks return null |

Entity arguments accept a Spotify URI, an `https://open.spotify.com/...` URL, or an object containing a `uri` (including library/search/playlist results). All indices are zero-based. Seek positions and playback progress use milliseconds. Repeat modes are `off`, `context`, and `track`.

```ts
import { SpotifyPlus } from 'spotifyplus';

const albums = await SpotifyPlus.Library.list({ type: 'album', offset: 0, limit: 25 });
if (albums.items.length) await SpotifyPlus.Player.playContext(albums.items[0], 3);
SpotifyPlus.Player.setShuffle(true);
SpotifyPlus.Player.setRepeat('context');
SpotifyPlus.Player.seek(45_000);

const results = await SpotifyPlus.Search.search('Miles Davis', { limit: 20 });
const track = results.items.find(item => item.uri.startsWith('spotify:track:'));
if (track) {
    SpotifyPlus.Queue.add(track);
    SpotifyPlus.Library.like(track);
}

const queue = SpotifyPlus.Queue.get();
if (queue.next.length > 1) SpotifyPlus.Queue.move(queue, 1, 0);

const playlist = SpotifyPlus.Playlists.create('Extension picks');
if (track) SpotifyPlus.Playlists.addTracks(playlist, [track]);
const page = await SpotifyPlus.Playlists.get(playlist, { limit: 50 });
if (page.items.length > 1) {
    SpotifyPlus.Playlists.moveTracks(playlist, [page.items[1].rowId], page.items[0].rowId);
}

const devices = SpotifyPlus.Connect.getDevices();
const phone = devices.find(device => device.isLocal);
if (phone) await SpotifyPlus.Connect.transfer(phone);
const currentDevice = SpotifyPlus.Connect.getCurrentDevice();
const user = await SpotifyPlus.User.getCurrent();
SpotifyPlus.Platform.Clipboard.writeText(user.uri);
```

Queue snapshots contain a revision, the current entry, upcoming entries, and history. Edits require the snapshot's revision and reject when it is stale; fetch another snapshot after each edit. Indices address occurrences, preserving duplicate tracks. `Queue.move` uses the final destination index. `Queue.clear` preserves the current track and history.

Playlist row IDs similarly identify occurrences, so removing one duplicate does not remove all copies. `Playlists.moveTracks` places rows before another row, or at the end if omitted. `Playlists.move` changes the playlist's position in the library; its optional second argument is the playlist to precede (omitted means first). `delete` removes a playlist from the user's library, matching Spotify's delete/unfollow semantics. Modification permissions remain enforced by Spotify.

Library pages support `all`, `playlist`, `album`, and `artist` filters. Library and playlist page limits are 1-100; batches accept 1-100 entries. Batch operations are not transactional: a failure can follow earlier successful mutations. Playlist `addedAt` values are Unix seconds. Search returns native result URIs and display snippets, not full entity metadata. Connect volume values are Spotify's raw values. `getCurrentDevice` returns null when no device is active. Transferring to the local device uses Spotify's Pull command.

Clipboard reads return null when no text is available. Android's clipboard access rules apply, including foreground restrictions. The API uses the signed-in Spotify session; it does not require an extension to supply tokens.

Requests run off the Node and Android UI threads with bounded queuing. Individual service waits time out after 15 seconds; the JS request deadline is 30 seconds. Expired queued requests are discarded before execution. A mutation already executing may complete after a timeout, so read the current state before retrying.

## Events

Native events are delivered to every loaded extension that subscribes through `SpotifyPlus.Events`. Each extension receives its own payload copy. A failing listener does not interrupt another extension, and unloading an extension removes its listeners. `Events.emit` remains local to the emitting extension.

```ts
SpotifyPlus.Events.on('songChanged', ({ uri, previousUri }) => {
    SpotifyPlus.log('Track changed', previousUri, uri);
});
SpotifyPlus.Events.on('deviceChanged', ({ device }) => {
    SpotifyPlus.log('Playing on', device?.name ?? 'no device');
});
const onSeek = ({ positionMs }: { positionMs: number }) => SpotifyPlus.log(positionMs);
SpotifyPlus.Events.on('trackSeeked', onSeek);
SpotifyPlus.Events.off('trackSeeked', onSeek);
```

| Name | Payload |
| --- | --- |
| `contextChanged` | `{ uri, previousUri }` |
| `songChanged` | `{ uri, previousUri }`; URIs may be null when playback clears |
| `playPause` | `{ isPlaying, isPaused }` |
| `trackSeeked` | `{ positionMs, previousPositionMs }` |
| `shuffleChanged` | `{ enabled }` |
| `repeatChanged` | `{ mode }` |
| `deviceChanged` | `{ device, previousDeviceId }`; either may be null |

The first state establishes a baseline rather than firing change events. Song changes distinguish repeated occurrences of the same track. Playback changes are observed from player state, including changes outside the extension. Local seeks emit after command acknowledgement; remote seeks are inferred from position discontinuities over 1.5 seconds, so smaller remote seeks cannot currently be distinguished reliably from ordinary state timing. Playback-speed changes are accounted for in that comparison. Connect events come from the live device stream.

## Target bindings

| Function | 9.1.82.2160 binding |
| --- | --- |
| Core player and startup skips | `p.ghw.a(p.nno0)`; `p.ino0`, `p.kno0` |
| Seek | `p.fno0(long)`; acknowledged through Rx Single |
| Context playback | `p.tgw.a(PlayCommand)` with `SkipToTrack.fromIndices` |
| Queue | `p.ihw.a(ContextTrack)`, `p.ihw.b(SetQueueCommand)`, stream field `c` |
| Shuffle/repeat | `p.hhw.a(boolean)`, `p.hhw.c(boolean, boolean)` |
| State | `AutoValue_PlayerState$Builder.build`; `p.ibl0.b/c` |
| Authenticated search | `RetrofitMaker`, `p.xfy0.a(Map, Map)`; request ID, timestamp, entity types; `MainViewResponse.q()` hits |
| Library/playlists/Connect | Captured `p.q1k` Esperanto transport with named protobuf request/response types |
| HTTP hooks | `p.l6w0.g(String)`, `p.y6p.B/c(String, String)` |
| Navigation | `p.zxh0`, `p.txh0.e`, `p.jz60.l`, `p.df5.U` |
| Drawer list transforms | `p.b6y`, `p.yoj0`, `p.z03`: `invokeSuspend` and field `d` |
| Context menu | `p.f1k(p.gyj, List, boolean)` and direct `p.gzj` items |

Java services communicate through `SpotifyNativeBridge.requestApi`, the C++ `SpotifyPlus_RequestApi` export, and the Node addon's `requestApi` binding. Correlated replies and broadcasts use the existing native event bridge. JSON is escaped before JNI to preserve supplementary Unicode such as emoji.

## Validation and device checks

Local checks cover Java/C++ compilation, SDK packaging, TypeScript consumers, request correlation/errors/Unicode, URI and index validation, queue occurrence addressing, and event isolation/unloading. Live behavior still requires Spotify 9.1.82.2160 on a hooked Android device.

On that device, cold-start Spotify and exercise skips before opening lyrics; edit a queue containing duplicates and check stale-revision rejection; use a disposable playlist to verify edits and ordering; compare library membership before and after a save/remove; run search; check profile and clipboard data; transfer to another Connect device and back. Load two extensions and verify all event subscriptions for native UI actions as well as API actions, then unload one and confirm it stops receiving events.

## Context menu visibility

Extensions can open Spotify's native UI from React handlers:

```tsx
import { SpotifyPlus } from 'spotifyplus';
import { View, Button } from 'spotifyplus/react';

const report = (error: unknown) => SpotifyPlus.log(String(error));
const item = { uri: 'spotify:album:4aawyAB9vmqN3uQ7FjRGTy' };

<View onLongPress={() => { void SpotifyPlus.ContextMenu.open(item).catch(report); }}>
    <Button text="Open drawer" onPress={() => { void SpotifyPlus.SideDrawer.open().catch(report); }} />
    <Button text="⋮" onPress={() => { void SpotifyPlus.ContextMenu.open(item).catch(report); }} />
    <Button text="Now playing menu" onPress={() => { void SpotifyPlus.ContextMenu.openNowPlaying().catch(report); }} />
</View>;
```

`SideDrawer.open(): Promise<void>` opens the native main-activity drawer, including registered extension entries, even when Home is replaced. `ContextMenu.open(item, { contextUri? }): Promise<void>` accepts a track, album, artist or playlist URI, Spotify URL, or object with a `uri`. The optional parent context accepts the same URI/URL/object format and influences Spotify's context-dependent actions. This invokes `p.vyj.b` with Spotify's default `p.ryj` options; Spotify loads the header and builds its native actions. Existing extension menu entries and React menu targets still apply.

`ContextMenu.openNowPlaying(): Promise<void>` invokes the native Now Playing button's current listener, preserving playback-specific behavior and working while a React replacement retains the original view. Call it from the full Now Playing screen; it rejects when that activity/button is unavailable or disabled. Opening a known track menu elsewhere uses `ContextMenu.open(track)`.

All opening calls run on Android's UI thread and require a foreground Spotify activity. They reject if the native launcher or requested screen is not ready, including early startup or unsupported layouts. Resolution acknowledges the launch action, not completion of Spotify's asynchronous menu-content loading. The bindings target Spotify 9.1.82.2160. Invalid item arguments throw synchronously before bridge dispatch.

Device validation: enable React UI Showcase, cold-start into its custom Home, open the drawer and verify extension entries; open album/artist/playlist menus from the collection and track menus from Search using both long press and ⋮; open ⋮ on the custom Now Playing screen and check the selected track, native actions, extension entries, and dismissal. Repeat after rotating/recreating the activity and changing tracks. Compilation and API tests do not substitute for these native device checks.

`new SpotifyPlus.ContextMenu(name, onClick, shouldAdd?, disabled?, types?).register()`

Existing constructor calls remain valid. `types` accepts `'track' | 'artist' | 'album' | 'playlist'` or a readonly array of those strings. Omit it to allow every context menu (including other or unidentified entity types); an empty array allows none. Java checks the selected entity URI before evaluating visibility.

```ts
new SpotifyPlus.ContextMenu(
    'My action',
    uri => console.log('Selected', uri),
    uri => uri.length > 0,
    false,
    ['track', 'album'],
).register();
```

`shouldAdd(uri, contextUri)` is synchronous and must return the boolean `true` to show the item; `false`, non-boolean results, and exceptions hide it. Omit the callback to show the item in every allowed menu. Visibility is evaluated again on each opening, across registered extensions. Disabled items are hidden. The modern hook supplies the selected entity URI for both callback arguments, or an empty string when unknown; it does not expose a separate parent context URI.

Keep predicates quick and use locally available state. Menu construction waits at most 150 ms for the entire visibility batch. If Node cannot reply in that time, conditional items are hidden for that opening and unconditional, enabled items remain visible. Promises are not supported.
