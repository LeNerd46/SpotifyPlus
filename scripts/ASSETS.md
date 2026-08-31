# Extension assets

Extensions can bundle fonts, images, audio, JSON, and other files inside their own directory. Every file an extension uses must match a relative glob in `manifest.json`:

```json
{
    "assets": [
        "assets/**/*"
    ]
}
```

Asset patterns and `native.apk` are relative to the directory containing the manifest's `main` file. For example, `"main": "dist/index.js"` and `"assets": ["assets/**/*"]` resolve assets from `dist/assets`. The build command copies matched source files beside the compiled extension. A pattern that matches no files fails the build so packages cannot silently ship without required assets.

## Loading assets

```tsx
import { SpotifyPlus } from 'spotifyplus';
import { Image, Text } from 'spotifyplus/react';

const cover = SpotifyPlus.Assets.image('assets/images/cover.png');
const lyricsFont = SpotifyPlus.Assets.fontFamily([
    {
        source: 'assets/fonts/lyrics-regular.ttf',
        weight: 400,
    },
    {
        source: 'assets/fonts/lyrics-bold.ttf',
        weight: 700,
    },
]);

export const Example = () => (
    <>
        <Image source={cover} />
        <Text style={{ fontFamily: lyricsFont, fontWeight: 700 }}>
            Custom typography
        </Text>
    </>
);
```

Use `Assets.resolve(path)` for a generic native asset reference, `Assets.image(path)` and `Assets.font(path)` for format validation, or these data helpers when JavaScript needs the contents:

```ts
const template = SpotifyPlus.Assets.readText('assets/template.txt');
const config = SpotifyPlus.Assets.readJson<Config>('assets/config.json');
const bytes = SpotifyPlus.Assets.readBytes('assets/data.bin');
```

Paths must be relative, cannot contain traversal segments, and remain confined to the installed extension directory after symlink resolution. Native references are opaque and are invalidated when the extension unloads. TTF, OTF, and TTC fonts are supported. Native image views support PNG, JPEG, GIF, WebP, and BMP files; `resolve` can still expose other declared formats to compatible consumers.

`spotifyplus dev` watches declared asset file types and includes current assets in hot reloads. Hot reload asset payloads are limited to 20 MB; larger packages should be rebuilt and installed normally.
