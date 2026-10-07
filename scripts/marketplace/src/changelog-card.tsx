import React from 'react';
import { Text, View } from 'spotifyplus/react';
import Colors from './colors';
import Markdown from './markdown';

const ChangelogCard = ({ source, baseUrl }: { source?: string; baseUrl?: string }) => {
    if (!source?.trim()) return null;

    return (
        <View style={{ marginTop: 32, padding: 24, borderRadius: 12, backgroundColor: Colors.surfaceContainer, borderWidth: 1, borderColor: Colors.outlineVariant }}>
            <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold' style={{ marginBottom: 16 }}>
                Changelog
            </Text>
            <Markdown source={source} baseUrl={baseUrl} />
        </View>
    );
};

export default ChangelogCard;
