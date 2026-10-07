import { decodeHTML } from 'entities';
import { Lexer, type Token, type MarkedToken } from 'marked';

// Tokenize only: no browser, HTML renderer, or React Native dependency.
export const parseMarkdown = (source: string): Token[] => new Lexer({ gfm: true, breaks: false }).lex(source);

export interface InlineStyle {
    fontWeight?: 'bold';
    fontStyle?: 'italic';
    fontFamily?: 'monospace';
    textColor?: string;
    backgroundColor?: string;
    textDecorationLine?: 'line-through' | 'underline';
    link?: string;
}

export type InlineNode = string | { style: InlineStyle; children: InlineNode[] };
export type InlinePart =
    | { type: 'text'; children: InlineNode[] }
    | { type: 'image'; source?: string; alt: string; link?: string };

export const markdownColors = {
    text: '#e6edf3', muted: '#9198a1', link: '#58a6ff', border: '#3d444d',
    code: '#151b23', inlineCode: '#343941', tableAlternate: '#292e35',
};

/** Resolve relative repository assets; keep untrusted Markdown out of executable URL schemes. */
export const resolveMarkdownUrl = (value: string, baseUrl?: string, image = false): string | undefined => {
    const decoded = decodeHTML(value).trim();
    if (!decoded || decoded.startsWith('#') || /[\u0000-\u001f\u007f]/.test(decoded)) return undefined;
    try {
        const url = baseUrl ? new URL(decoded, baseUrl) : new URL(decoded);
        if (url.protocol === 'https:' || url.protocol === 'http:' || (!image && url.protocol === 'mailto:')) return url.href;
    } catch { /* A relative URL needs a repository source. */ }
    return undefined;
};

export const inlineParts = (tokens: Token[], baseUrl?: string): InlinePart[] => {
    const parts: InlinePart[] = [];
    const append = (text: string) => {
        if (!text) return;
        let last = parts[parts.length - 1];
        if (!last || last.type !== 'text') {
            last = { type: 'text', children: [] };
            parts.push(last);
        }
        last.children.push(text);
    };
    const wrap = (items: Token[], style: InlineStyle) => {
        for (const part of inlineParts(items, baseUrl)) {
            if (part.type === 'image') parts.push({ ...part, link: style.link ?? part.link });
            else {
                let last = parts[parts.length - 1];
                if (!last || last.type !== 'text') {
                    last = { type: 'text', children: [] };
                    parts.push(last);
                }
                last.children.push({ style, children: part.children });
            }
        }
    };
    const visit = (items: Token[]) => {
        for (const item of items) {
            const token = item as MarkedToken;
            switch (token.type) {
                case 'strong': wrap(token.tokens, { fontWeight: 'bold' }); break;
                case 'em': wrap(token.tokens, { fontStyle: 'italic' }); break;
                case 'del': wrap(token.tokens, { textDecorationLine: 'line-through' }); break;
                case 'link': {
                    const link = resolveMarkdownUrl(token.href, baseUrl);
                    wrap(token.tokens, link ? { link, textColor: markdownColors.link, textDecorationLine: 'underline' } : {});
                    break;
                }
                case 'image': parts.push({
                    type: 'image', source: resolveMarkdownUrl(token.href, baseUrl, true),
                    alt: decodeHTML(token.text),
                }); break;
                case 'codespan': wrap([{ type: 'text', raw: token.text, text: token.text, escaped: true }], { fontFamily: 'monospace', backgroundColor: markdownColors.inlineCode }); break;
                case 'br': append('\n'); break;
                case 'html':
                    if (/^<br\s*\/?\s*>$/i.test(token.text)) append('\n');
                    else if (!/^<!--[\s\S]*-->$/.test(token.text)) append(token.text);
                    break;
                default:
                    if ('tokens' in token && token.tokens) visit(token.tokens);
                    else {
                        const text = 'text' in token ? token.text : token.raw;
                        append(('escaped' in token && token.escaped ? text : decodeHTML(text)).replace(/\r?\n/g, ' '));
                    }
            }
        }
    };
    visit(tokens);
    return parts;
};
