# marketplace

A Spotify Plus script project.

## Scripts

```bash
npm run check
npm test
npm run build
```

Install dependencies with the package manager you used to create the project.

## Entry

The script entry file is declared in `manifest.json`.

## Extension updates

The marketplace checks fresh repository manifests once when its extension loads,
before the drawer is opened. Pages reuse that result for the session. The installed
extensions button shows the number of available updates, and the installed page
lists them first with individual Update buttons. Details also show an Update button
and the installed and available versions. Successful updates or uninstalls immediately
refresh the count and list. Updates are installed only when selected.

Use **Check for updates** on the installed page to refresh manually or retry a
failed check. Failed or incomplete checks are displayed. Only newer numeric versions
are offered, with prerelease ordering and build metadata handled; unknown versions
are not automatically treated as updates. The installer rechecks the pinned manifest
to prevent installing an older version if the repository changes after the check.

## Markdown changelogs

`MarketplaceExtension.changelog` is the Markdown `body` of the repository's latest
GitHub release, fetched from `https://api.github.com/repos/{owner}/{repo}/releases/latest`.
The details page renders that one string inside the changelog card without imposing
releases, dates, or sections:

```ts
changelog: '## What changed\n\n- Added **synchronized lyrics**.\n- Fixed ~~old behavior~~ playback timing.'
```

The marketplace requests the release once per repository and caches its body with
the extension metadata. Manifest changelog fields and files are no longer used.
If no release exists, its body is empty, or the release request fails, the entire
changelog card is omitted. Relative links and images resolve from the repository
root at the release tag. Previous file-based changelog caches are invalidated.

The reusable `Markdown` component accepts `source` and an optional `baseUrl`.
It uses Marked's GitHub Flavored Markdown lexer and SpotifyPlus components, with
nested `<Text>` elements for emphasis, links, inline code, and strikethrough.
Android flattens that text tree into styled spans in one wrapping TextView.
There are no React Native packages, browser APIs, HTML rendering, or WebViews.

Supported syntax includes headings, paragraphs, soft and hard line breaks,
reference links and autolinks, images, ordered and unordered nested lists,
read-only task lists, blockquotes, horizontal rules, fenced/indented code,
and horizontally scrolling tables. Styling follows GitHub's dark Markdown
typography, spacing, heading rules, code backgrounds, and table borders.

Raw HTML is displayed as text, except `<br>` line breaks and omitted comments.
Code is monochrome; GitHub-specific math, footnotes, alerts, emoji shortcodes,
and in-document anchor navigation are not implemented. Images use a bounded
preview area and tables use fixed-width columns to fit the native layout.
