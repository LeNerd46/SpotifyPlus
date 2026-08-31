import React, { useMemo, useState } from "react";
import { ScrollView, View, Text, TextInput } from "../../ui/components";
import MarketplaceCard from "./MarketplaceCard";
import MarketplaceDetail from "./MarketplaceDetail";
import { MARKETPLACE_ITEMS } from "./marketplace-data";
import { MarketplaceItem } from "./types";

export default function Marketplace() {
    const [query, setQuery] = useState("");
    const [selectedItem, setSelectedItem] = useState<MarketplaceItem | null>(null);

    const filteredItems = useMemo(() => {
        const lower = query.trim().toLowerCase();
        if (!lower) return MARKETPLACE_ITEMS;
        return MARKETPLACE_ITEMS.filter(item =>
            item.title.toLowerCase().includes(lower) ||
            item.author.toLowerCase().includes(lower) ||
            item.description.toLowerCase().includes(lower) ||
            item.tags.some(tag => tag.toLowerCase().includes(lower))
        );
    }, [query]);

    if (selectedItem) {
        return <MarketplaceDetail item={selectedItem} onBack={() => setSelectedItem(null)} />;
    }

    return (
        <ScrollView style={{ flex: 1, backgroundColor: "#0b0b0b" }}>
            <View style={{ paddingTop: 56, paddingBottom: 32, paddingHorizontal: 20 }}>
                <View style={{ marginBottom: 20 }}>
                    <Text style={{ color: "#ffffff", fontSize: 34, fontWeight: "800", marginBottom: 8 }}>Marketplace</Text>
                    <Text style={{ color: "#a1a1aa", fontSize: 15, lineHeight: 22 }}>Discover scripts, tweaks, and extensions built for SpotifyPlus.</Text>
                </View>

                <View style={{ backgroundColor: "#151518", borderWidth: 1, borderColor: "#232329", borderRadius: 18, paddingHorizontal: 16, paddingVertical: 4, marginBottom: 20 }}>
                    <TextInput value={query} onChangeText={setQuery} placeholder={"Search scripts, authors, or tags"} placeholderTextColor={"#6b7280"} style={{ color: "#ffffff", fontSize: 15, minHeight: 46 }} />
                </View>

                <View style={{ flexDirection: "row", marginBottom: 20 }}>
                    <View style={{ flex: 1, backgroundColor: "#141416", borderWidth: 1, borderColor: "#232329", borderRadius: 18, padding: 16, marginRight: 8 }}>
                        <Text style={{ color: "#71717a", fontSize: 12, marginBottom: 6 }}>Scripts</Text>
                        <Text style={{ color: "#ffffff", fontSize: 20, fontWeight: "800" }}>{MARKETPLACE_ITEMS.length}</Text>
                    </View>
                    <View style={{ flex: 1, backgroundColor: "#141416", borderWidth: 1, borderColor: "#232329", borderRadius: 18, padding: 16, marginLeft: 8 }}>
                        <Text style={{ color: "#71717a", fontSize: 12, marginBottom: 6 }}>Installs</Text>
                        <Text style={{ color: "#ffffff", fontSize: 20, fontWeight: "800" }}>128k+</Text>
                    </View>
                </View>

                <View style={{ marginBottom: 12 }}>
                    <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "700" }}>Trending</Text>
                    <Text style={{ color: "#8b8b93", fontSize: 14, marginTop: 4 }}>{filteredItems.length} result{filteredItems.length === 1 ? "" : "s"}</Text>
                </View>

                <View>
                    {filteredItems.map(item => (
                        <MarketplaceCard key={item.id} item={item} onPress={() => setSelectedItem(item)} />
                    ))}

                    {filteredItems.length === 0 ? (
                        <View style={{ backgroundColor: "#141416", borderWidth: 1, borderColor: "#232329", borderRadius: 22, padding: 22, marginTop: 8 }}>
                            <Text style={{ color: "#ffffff", fontSize: 18, fontWeight: "700", marginBottom: 6 }}>Nothing matched</Text>
                            <Text style={{ color: "#8b8b93", fontSize: 14, lineHeight: 21 }}>Try a different title, author, or tag. You could also browse trending scripts instead.</Text>
                        </View>
                    ) : null}
                </View>
            </View>
        </ScrollView>
    );
}