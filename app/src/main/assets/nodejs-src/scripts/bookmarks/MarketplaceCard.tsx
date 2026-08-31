import React from "react";
import { View, Text, Pressable } from "../../ui/components";
import { MarketplaceItem } from "./types";

interface Props {
    item: MarketplaceItem;
    onPress: () => void;
}

export default function MarketplaceCard({ item, onPress }: Props) {
    return (
        <Pressable onPress={onPress}>
            <View style={{ backgroundColor: "#141416", borderWidth: 1, borderColor: "#232329", borderRadius: 22, padding: 18, marginBottom: 14 }}>
                <View style={{ flexDirection: "row", alignItems: "flex-start", marginBottom: 14 }}>
                    <View style={{ width: 48, height: 48, borderRadius: 16, backgroundColor: item.accentColor, alignItems: "center", justifyContent: "center", marginRight: 14 }}>
                        <Text style={{ color: "#ffffff", fontSize: 18, fontWeight: "800" }}>{item.title.slice(0, 1)}</Text>
                    </View>

                    <View style={{ flex: 1 }}>
                        <Text style={{ color: "#ffffff", fontSize: 18, fontWeight: "700", marginBottom: 4 }}>{item.title}</Text>
                        <Text style={{ color: "#9ca3af", fontSize: 13, marginBottom: 8 }}>by {item.author}</Text>
                        <Text style={{ color: "#d4d4d8", fontSize: 14, lineHeight: 21 }}>{item.description}</Text>
                    </View>
                </View>

                <View style={{ flexDirection: "row", flexWrap: "wrap", marginBottom: 14 }}>
                    {item.tags.map(tag => (
                        <View key={tag} style={{ backgroundColor: "#1d1d22", borderRadius: 999, paddingHorizontal: 10, paddingVertical: 6, marginRight: 8, marginBottom: 8 }}>
                            <Text style={{ color: "#c4c4cc", fontSize: 12, fontWeight: "600" }}>{tag}</Text>
                        </View>
                    ))}
                </View>

                <View style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
                    <View style={{ flexDirection: "row" }}>
                        <View style={{ marginRight: 18 }}>
                            <Text style={{ color: "#71717a", fontSize: 11, marginBottom: 2 }}>Downloads</Text>
                            <Text style={{ color: "#ffffff", fontSize: 14, fontWeight: "700" }}>{item.downloads}</Text>
                        </View>
                        <View style={{ marginRight: 18 }}>
                            <Text style={{ color: "#71717a", fontSize: 11, marginBottom: 2 }}>Version</Text>
                            <Text style={{ color: "#ffffff", fontSize: 14, fontWeight: "700" }}>{item.version}</Text>
                        </View>
                        <View>
                            <Text style={{ color: "#71717a", fontSize: 11, marginBottom: 2 }}>Rating</Text>
                            <Text style={{ color: "#ffffff", fontSize: 14, fontWeight: "700" }}>{item.rating}</Text>
                        </View>
                    </View>

                    <View style={{ backgroundColor: "#1ed76022", borderWidth: 1, borderColor: "#1ed76055", borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8 }}>
                        <Text style={{ color: "#4ade80", fontSize: 12, fontWeight: "700" }}>View</Text>
                    </View>
                </View>
            </View>
        </Pressable>
    );
}