import React, { useEffect, useMemo, useState } from "react";
import {
    Image,
    ScrollView,
    Text,
    VerticalStackLayout,
    HorizontalStackLayout,
    View,
} from "../../ui/components";
import { SpotifyTrack } from "../../core/models";

interface Props {
    bookmarks: string[];
    SpotifyPlus: any;
    onDeleteTrack?: (uri: string) => void;
}

type TrackMap = Record<string, SpotifyTrack | null | undefined>;

export default function App({ bookmarks, SpotifyPlus, onDeleteTrack }: Props) {
    const [tracks, setTracks] = useState<TrackMap>({});
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let cancelled = false;

        async function loadTracks() {
            if (bookmarks.length === 0) {
                setTracks({});
                return;
            }

            setLoading(true);

            const entries = await Promise.all(
                bookmarks.map(async (uri) => {
                    try {
                        const track = await SpotifyPlus.Internal.getTrack(uri);
                        return [uri, track] as const;
                    } catch (error) {
                        console.log(`Failed to load track for ${uri}`, error);
                        return [uri, null] as const;
                    }
                })
            );

            if (cancelled) return;

            setTracks(Object.fromEntries(entries));
            setLoading(false);
        }

        loadTracks();

        return () => {
            cancelled = true;
        };
    }, [bookmarks, SpotifyPlus]);

    const loadedCount = useMemo(
        () => bookmarks.filter((uri) => tracks[uri] !== undefined).length,
        [bookmarks, tracks]
    );

    function handleOpen(uri: string) {
        SpotifyPlus.openUri(uri);
    }

    function TrackArtwork({ track }: { track?: SpotifyTrack | null }) {
        if (!track?.album?.image) {
            return (
                <View
                    width={60}
                    height={60}
                    borderRadius={12}
                    backgroundColor={"#2A2A2A"}
                    justifyContent={"center"}
                    alignItems={"center"}
                    marginRight={12}
                >
                    <Text color={"#777777"} fontSize={11} fontWeight={"bold"}>
                        ♪
                    </Text>
                </View>
            );
        }

        return (
            <Image
                src={track.album.image}
                width={60}
                height={60}
                marginRight={12}
                borderRadius={12}
                backgroundColor={"#2A2A2A"}
                resizeMode={"cover"}
            />
        );
    }

    function PillButton({
        text,
        onClick,
        backgroundColor,
        textColor,
        marginRight,
    }: {
        text: string;
        onClick?: () => void;
        backgroundColor: string;
        textColor: string;
        marginRight?: number;
    }) {
        return (
            <View
                width={"wrap_content"}
                backgroundColor={backgroundColor}
                paddingHorizontal={12}
                paddingVertical={8}
                borderRadius={999}
                marginRight={marginRight}
                onClick={onClick}
            >
                <Text color={textColor} fontSize={12} fontWeight={"bold"}>
                    {text}
                </Text>
            </View>
        );
    }

    function LoadingCard({ uri }: { uri: string }) {
        return (
            <View
                key={uri}
                width={"match_parent"}
                backgroundColor={"#1A1A1A"}
                padding={14}
                marginBottom={10}
                borderRadius={16}
            >
                <HorizontalStackLayout alignItems={"center"}>
                    <View
                        width={60}
                        height={60}
                        borderRadius={12}
                        backgroundColor={"#2A2A2A"}
                        marginRight={12}
                    />

                    <View width={0} layoutWeight={1} paddingRight={10}>
                        <Text color={"#FFFFFF"} fontSize={15} fontWeight={"600"}>
                            Loading track...
                        </Text>
                        <Text color={"#7A7A7A"} fontSize={12} paddingTop={3}>
                            Fetching metadata
                        </Text>
                    </View>

                    <PillButton
                        text={"..."}
                        backgroundColor={"#2A2A2A"}
                        textColor={"#A0A0A0"}
                    />
                </HorizontalStackLayout>
            </View>
        );
    }

    function ErrorCard({ uri }: { uri: string }) {
        return (
            <View
                key={uri}
                width={"match_parent"}
                backgroundColor={"#1A1A1A"}
                padding={14}
                marginBottom={10}
                borderRadius={16}
            >
                <HorizontalStackLayout alignItems={"center"}>
                    <TrackArtwork />

                    <View width={0} layoutWeight={1} paddingRight={10}>
                        <Text color={"#FFFFFF"} fontSize={15} fontWeight={"600"} numberOfLines={1}>
                            Unknown track
                        </Text>
                        <Text color={"#7A7A7A"} fontSize={12} paddingTop={3} numberOfLines={1}>
                            {uri}
                        </Text>
                    </View>

                    <PillButton
                        text={"Delete"}
                        backgroundColor={"#2A2A2A"}
                        textColor={"#FF7B7B"}
                        onClick={() => onDeleteTrack?.(uri)}
                    />
                </HorizontalStackLayout>
            </View>
        );
    }

    function TrackCard({ uri, track }: { uri: string; track: SpotifyTrack }) {
        return (
            <View
                key={uri}
                width={"match_parent"}
                backgroundColor={"#1A1A1A"}
                padding={14}
                marginBottom={10}
                borderRadius={16}
            >
                <HorizontalStackLayout alignItems={"center"}>
                    <TrackArtwork track={track} />

                    <View width={0} layoutWeight={1} paddingRight={10}>
                        <HorizontalStackLayout alignItems={"center"}>
                            <Text
                                color={"#FFFFFF"}
                                fontSize={15}
                                fontWeight={"600"}
                                numberOfLines={1}
                            >
                                {track.title || "Unknown title"}
                            </Text>

                            {track.explicit ? (
                                <View
                                    backgroundColor={"#333333"}
                                    paddingHorizontal={5}
                                    paddingVertical={2}
                                    borderRadius={4}
                                    marginLeft={6}
                                >
                                    <Text color={"#D8D8D8"} fontSize={10} fontWeight={"bold"}>
                                        E
                                    </Text>
                                </View>
                            ) : null}
                        </HorizontalStackLayout>

                        <Text
                            color={"#B3B3B3"}
                            fontSize={12}
                            paddingTop={3}
                            numberOfLines={1}
                        >
                            {track.artist || "Unknown artist"}
                        </Text>

                        <Text
                            color={"#6F6F6F"}
                            fontSize={11}
                            paddingTop={2}
                            numberOfLines={1}
                        >
                            {track.album?.title || "Unknown album"}
                        </Text>
                    </View>

                    <HorizontalStackLayout alignItems={"center"}>
                        <PillButton
                            text={"Open"}
                            backgroundColor={"#1DB954"}
                            textColor={"#FFFFFF"}
                            marginRight={8}
                            onClick={() => handleOpen(uri)}
                        />

                        <PillButton
                            text={"Delete"}
                            backgroundColor={"#2A2A2A"}
                            textColor={"#FF7B7B"}
                            onClick={() => onDeleteTrack?.(uri)}
                        />
                    </HorizontalStackLayout>
                </HorizontalStackLayout>
            </View>
        );
    }

    return (
        <View backgroundColor={"#121212"} width={"match_parent"} height={"match_parent"}>
            <ScrollView width={"match_parent"} height={"match_parent"} fillViewport>
                <VerticalStackLayout width={"match_parent"} padding={16} paddingTop={44} paddingBottom={28}>
                    <Text fontSize={30} fontWeight={"bold"} color={"#FFFFFF"}>
                        Bookmarks
                    </Text>

                    <Text fontSize={14} color={"#A0A0A0"} paddingTop={4} paddingBottom={18}>
                        {bookmarks.length === 0
                            ? "Your saved tracks will show up here"
                            : loading && loadedCount < bookmarks.length
                                ? `Loading ${loadedCount}/${bookmarks.length} tracks`
                                : `${bookmarks.length} saved track${bookmarks.length === 1 ? "" : "s"}`}
                    </Text>

                    {bookmarks.length === 0 ? (
                        <View
                            width={"match_parent"}
                            backgroundColor={"#1A1A1A"}
                            padding={18}
                            borderRadius={16}
                        >
                            <Text color={"#FFFFFF"} fontSize={16} fontWeight={"bold"}>
                                Nothing here yet
                            </Text>
                            <Text color={"#9A9A9A"} fontSize={13} paddingTop={4}>
                                Bookmark a song from the context menu and it'll appear here.
                            </Text>
                        </View>
                    ) : (
                        bookmarks.map((uri) => {
                            const track = tracks[uri];

                            if (track === undefined) return <LoadingCard key={uri} uri={uri} />;
                            if (track === null) return <ErrorCard key={uri} uri={uri} />;
                            return <TrackCard key={uri} uri={uri} track={track} />;
                        })
                    )}
                </VerticalStackLayout>
            </ScrollView>
        </View>
    );
}