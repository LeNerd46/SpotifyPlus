import React from "react";
import { ScriptView, View, Text } from "./../../ui/components";

export default function ExampleScriptView({ artwork }: { artwork: string }) {
    const [rotation, setRotation] = React.useState(0);
    return (
        <View style={{ flex: 1, backgroundColor: "#000" }}>
            <ScriptView
                style={{
                    position: "absolute",
                    left: 0,
                    top: 0,
                    right: 0,
                    bottom: 0,
                    width: "100%",
                    height: "100%",
                }}
                onFrame={(e) => setRotation((e.time / 80) % 360)}
                displayList={[
                    {
                        type: "image",
                        src: artwork,
                        x: "-10%",
                        y: "-10%",
                        width: "120%",
                        height: "120%",
                        resizeMode: "cover",
                        blurRadius: 32,
                        opacity: 1,
                        rotation,
                        scale: 1.2,
                    },
                    {
                        type: "rect",
                        x: 0,
                        y: 0,
                        width: "100%",
                        height: "100%",
                        fill: "#99000000",
                    },
                    {
                        type: "text",
                        text: "ScriptView works",
                        x: 24,
                        y: 96,
                        width: "80%",
                        height: 80,
                        color: "#ffffff",
                        fontSize: 28,
                        fontWeight: "bold",
                    },
                ]}
            />
            <Text
                style={{
                    marginTop: 48,
                    marginLeft: 24,
                    color: "#fff",
                    fontSize: 24,
                    fontWeight: "bold",
                }}
            >
                Normal React UI over it
            </Text>
        </View>
    );
}
