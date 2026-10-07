import React, { useMemo, useState } from 'react';
import { SpotifyPlus } from 'spotifyplus';
import { HorizontalScrollView, Image, Pressable, Text, View } from 'spotifyplus/react';
import type { Token, MarkedToken, Tokens } from 'marked';
import { inlineParts, markdownColors as colors, parseMarkdown, type InlineNode } from './markdown-model';

export interface MarkdownProps {
    source: string;
    baseUrl?: string;
}

const openLink = ({ url }: { url: string }) => SpotifyPlus.Navigation.open(url);

/** Render GFM directly into SpotifyPlus's Android-backed components. */
export const Markdown = ({ source, baseUrl }: MarkdownProps) => {
    const tokens = useMemo(() => parseMarkdown(source), [source]);
    return <View><Blocks tokens={tokens} baseUrl={baseUrl} /></View>;
};

type InlineProps = {
    tokens: Token[]; baseUrl?: string; size?: number; bold?: boolean;
    color?: string; align?: 'left' | 'center' | 'right';
};

const Inline = ({ tokens, baseUrl, size = 16, bold = false, color = colors.text, align = 'left' }: InlineProps) => (
    <View>
        {inlineParts(tokens, baseUrl).map((part, index) => {
            if (part.type === 'image') return <MarkdownImage key={`${index}-${part.source}`} {...part} />;
            return <Text key={index}
                textColor={color} fontSize={size} fontWeight={bold ? 'bold' : 'normal'}
                selectable allowFontPadding={false} textAlign={align} style={{ lineHeight: Math.round(size * 1.5) }}>
                <InlineChildren nodes={part.children} />
            </Text>;
        })}
    </View>
);

const InlineChildren = ({ nodes }: { nodes: InlineNode[] }) => nodes.map((node, index) => {
    if (typeof node === 'string') return node;
    const { link, ...props } = node.style;
    return <Text key={index} {...props} onPress={link ? () => openLink({ url: link }) : undefined}>
        <InlineChildren nodes={node.children} />
    </Text>;
});

const MarkdownImage = ({ source, alt, link }: { source?: string; alt: string; link?: string }) => {
    const [failed, setFailed] = useState(false);
    const content = source && !failed
        ? <Image source={source} accessibilityLabel={alt} resizeMode='contain' onError={() => setFailed(true)}
            style={{ width: '100%', height: 180, marginVertical: 8 }} />
        : <Text textColor={colors.muted} fontSize={14}>{alt || 'Image unavailable'}</Text>;
    return link ? <Pressable onPress={() => openLink({ url: link })}>{content}</Pressable> : <View>{content}</View>;
};

const Blocks = ({ tokens, baseUrl, tight = false }: { tokens: Token[]; baseUrl?: string; tight?: boolean }) => (
    <View>
        {tokens.filter(token => token.type !== 'space' && token.type !== 'def').map((token, index, blocks) => (
            <View key={index} style={{ marginBottom: index === blocks.length - 1 ? 0 : tight ? 4 : 16 }}>
                <Block token={token} baseUrl={baseUrl} />
            </View>
        ))}
    </View>
);

const Block = ({ token: input, baseUrl }: { token: Token; baseUrl?: string }): React.ReactNode => {
    const token = input as MarkedToken;
    switch (token.type) {
        case 'heading': {
            const size = [32, 24, 20, 16, 14, 14][token.depth - 1];
            return <View style={{ paddingBottom: token.depth <= 2 ? 8 : 0, borderBottomWidth: token.depth <= 2 ? 1 : 0, borderColor: colors.border }}>
                <Inline tokens={token.tokens} baseUrl={baseUrl} size={size} bold color={token.depth === 6 ? colors.muted : colors.text} />
            </View>;
        }
        case 'paragraph':
        case 'text': return <Inline tokens={token.tokens ?? [token]} baseUrl={baseUrl} />;
        case 'hr': return <View style={{ height: 3, backgroundColor: colors.border, marginVertical: 8 }} />;
        case 'code': return <View style={{ backgroundColor: colors.code, padding: 16, borderRadius: 6 }}>
            <HorizontalScrollView>
                <Text text={token.text} fontFamily='monospace' fontSize={13} textColor={colors.text} selectable allowFontPadding={false} style={{ lineHeight: 20 }} />
            </HorizontalScrollView>
        </View>;
        case 'blockquote': return <View style={{ borderLeftWidth: 4, borderColor: colors.border, paddingLeft: 16 }}>
            <Blocks tokens={token.tokens} baseUrl={baseUrl} />
        </View>;
        case 'list': return <View>
            {token.items.map((item: Tokens.ListItem, index: number) => (
                <View key={index} style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8, marginBottom: index === token.items.length - 1 ? 0 : token.loose ? 16 : 4 }}>
                    <Text fontSize={16} textColor={item.task && item.checked ? colors.link : colors.muted} allowFontPadding={false}
                        accessibilityLabel={item.task ? item.checked ? 'Checked' : 'Unchecked' : undefined}
                        style={{ minWidth: 20, textAlign: 'right', lineHeight: 24 }}>
                        {item.task ? item.checked ? '☑' : '☐' : token.ordered ? `${Number(token.start) + index}.` : '•'}
                    </Text>
                    <View style={{ flex: 1, minWidth: 0 }}><Blocks tokens={item.tokens} baseUrl={baseUrl} tight={!item.loose} /></View>
                </View>
            ))}
        </View>;
        case 'table': return <MarkdownTable table={token as Tokens.Table} baseUrl={baseUrl} />;
        case 'html': return <Inline tokens={[token]} baseUrl={baseUrl} />;
        default: return <Text textColor={colors.text}>{token.raw}</Text>;
    }
};

const MarkdownTable = ({ table, baseUrl }: { table: Tokens.Table; baseUrl?: string }) => (
    <HorizontalScrollView>
        <View style={{ borderTopWidth: 1, borderLeftWidth: 1, borderColor: colors.border }}>
            {[table.header, ...table.rows].map((row, rowIndex) => (
                <View key={rowIndex} style={{ flexDirection: 'row', backgroundColor: rowIndex % 2 === 0 ? 'transparent' : colors.tableAlternate }}>
                    {row.map((cell, cellIndex) => (
                        <View key={cellIndex} style={{ width: 160, paddingHorizontal: 13, paddingVertical: 6, borderBottomWidth: 1, borderRightWidth: 1, borderColor: colors.border }}>
                            <Inline tokens={cell.tokens} baseUrl={baseUrl} bold={rowIndex === 0} align={table.align[cellIndex] ?? 'left'} />
                        </View>
                    ))}
                </View>
            ))}
        </View>
    </HorizontalScrollView>
);

export default Markdown;
