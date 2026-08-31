import React from "react";
import { ScrollView, View, Text, Pressable } from "../../ui/components";
import { MarketplaceItem } from "./types";

interface Props {
    item: MarketplaceItem;
    onBack: () => void;
}

export default function MarketplaceDetail({ item, onBack }: Props) {
    return (
        <ScrollView style={{ flex: 1, backgroundColor: "#0b0b0b" }}>
            <View style={{ paddingTop: 56, paddingBottom: 32, paddingHorizontal: 20 }}>
                <Pressable onPress={onBack}>
                    <View style={{ alignSelf: "flex-start", backgroundColor: "#17171b", borderWidth: 1, borderColor: "#27272a", borderRadius: 999, paddingHorizontal: 14, paddingVertical: 10, marginBottom: 20 }}>
                        <Text style={{ color: "#ffffff", fontSize: 13, fontWeight: "700" }}>← Back</Text>
                    </View>
                </Pressable>

                <View style={{ backgroundColor: "#141416", borderWidth: 1, borderColor: "#232329", borderRadius: 28, padding: 22, marginBottom: 18 }}>
                    <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 18 }}>
                        <View style={{ width: 64, height: 64, borderRadius: 20, backgroundColor: item.accentColor, alignItems: "center", justifyContent: "center", marginRight: 16 }}>
                            <Text style={{ color: "#ffffff", fontSize: 26, fontWeight: "800" }}>{item.title.slice(0, 1)}</Text>
                        </View>

                        <View style={{ flex: 1 }}>
                            <Text style={{ color: "#ffffff", fontSize: 28, fontWeight: "800", marginBottom: 4 }}>{item.title}</Text>
                            <Text style={{ color: "#9ca3af", fontSize: 15, marginBottom: 8 }}>by {item.author}</Text>
                            <View style={{ flexDirection: "row", flexWrap: "wrap" }}>
                                {item.tags.map(tag => (
                                    <View key={tag} style={{ backgroundColor: "#1d1d22", borderRadius: 999, paddingHorizontal: 10, paddingVertical: 6, marginRight: 8, marginBottom: 8 }}>
                                        <Text style={{ color: "#c4c4cc", fontSize: 12, fontWeight: "600" }}>{tag}</Text>
                                    </View>
                                ))}
                            </View>
                        </View>
                    </View>

                    <Text style={{ color: "#d4d4d8", fontSize: 15, lineHeight: 23, marginBottom: 18 }}>{item.description}</Text>

                    <View style={{ flexDirection: "row", marginBottom: 18 }}>
                        <View style={{ flex: 1, backgroundColor: "#101013", borderRadius: 18, padding: 14, marginRight: 8 }}>
                            <Text style={{ color: "#71717a", fontSize: 11, marginBottom: 4 }}>Downloads</Text>
                            <Text style={{ color: "#ffffff", fontSize: 16, fontWeight: "800" }}>{item.downloads}</Text>
                        </View>
                        <View style={{ flex: 1, backgroundColor: "#101013", borderRadius: 18, padding: 14, marginHorizontal: 4 }}>
                            <Text style={{ color: "#71717a", fontSize: 11, marginBottom: 4 }}>Version</Text>
                            <Text style={{ color: "#ffffff", fontSize: 16, fontWeight: "800" }}>{item.version}</Text>
                        </View>
                        <View style={{ flex: 1, backgroundColor: "#101013", borderRadius: 18, padding: 14, marginLeft: 8 }}>
                            <Text style={{ color: "#71717a", fontSize: 11, marginBottom: 4 }}>Rating</Text>
                            <Text style={{ color: "#ffffff", fontSize: 16, fontWeight: "800" }}>{item.rating}</Text>
                        </View>
                    </View>

                    <View style={{ flexDirection: "row" }}>
                        <Pressable onPress={() => console.log("Install:", item.id)} style={{ flex: 1, marginRight: 8 }}>
                            <View style={{ backgroundColor: "#1ed760", borderRadius: 18, paddingVertical: 14, alignItems: "center", justifyContent: "center" }}>
                                <Text style={{ color: "#04110a", fontSize: 15, fontWeight: "800" }}>Install</Text>
                            </View>
                        </Pressable>

                        <Pressable onPress={() => console.log("GitHub:", item.githubUrl)} style={{ flex: 1, marginLeft: 8 }}>
                            <View style={{ backgroundColor: "#1a1a1f", borderWidth: 1, borderColor: "#2f2f38", borderRadius: 18, paddingVertical: 14, alignItems: "center", justifyContent: "center" }}>
                                <Text style={{ color: "#ffffff", fontSize: 15, fontWeight: "700" }}>GitHub</Text>
                            </View>
                        </Pressable>
                    </View>
                </View>

                <View style={{ backgroundColor: "#141416", borderWidth: 1, borderColor: "#232329", borderRadius: 28, padding: 22, marginBottom: 18 }}>
                    <Text style={{ color: "#ffffff", fontSize: 21, fontWeight: "800", marginBottom: 12 }}>README</Text>
                    <Text style={{ color: "#d4d4d8", fontSize: 14, lineHeight: 22 }}>{item.readme}</Text>
                </View>

                <View style={{ backgroundColor: "#141416", borderWidth: 1, borderColor: "#232329", borderRadius: 28, padding: 22 }}>
                    <Text style={{ color: "#ffffff", fontSize: 21, fontWeight: "800", marginBottom: 12 }}>What this includes</Text>
                    {item.features.map(feature => (
                        <View key={feature} style={{ flexDirection: "row", marginBottom: 12 }}>
                            <Text style={{ color: "#4ade80", fontSize: 14, marginRight: 10 }}>•</Text>
                            <Text style={{ flex: 1, color: "#d4d4d8", fontSize: 14, lineHeight: 21 }}>{feature}</Text>
                        </View>
                    ))}
                </View>
            </View>
        </ScrollView>
    );
}