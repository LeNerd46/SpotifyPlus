import fs from 'node:fs/promises';
import path from 'node:path';
import { transformAsync } from '@babel/core';
import unicodePropertyRegexModule from '@babel/plugin-transform-unicode-property-regex';

const unicodePropertyRegex = unicodePropertyRegexModule.default ?? unicodePropertyRegexModule;

/** The Android Node build rejects Unicode property escapes even though it supports /u. */
export function spotifyPlusDependencyRegexPlugin() {
    return {
        name: 'spotifyplus-dependency-regex',
        setup(build) {
            build.onLoad({ filter: /[/\\]node_modules[/\\].*\.[cm]?js$/ }, async args => {
                const source = await fs.readFile(args.path, 'utf8');
                if (!/\\[pP]\{/.test(source)) return null;
                const result = await transformAsync(source, {
                    babelrc: false,
                    configFile: false,
                    filename: args.path,
                    plugins: [unicodePropertyRegex],
                    sourceMaps: 'inline',
                });
                return { contents: result.code, loader: 'js', resolveDir: path.dirname(args.path), watchFiles: [args.path] };
            });
        },
    };
}
