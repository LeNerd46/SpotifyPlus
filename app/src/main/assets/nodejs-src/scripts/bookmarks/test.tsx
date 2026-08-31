import React, { useState } from "react";
import { View, Text, Image, TextInput, Button, ScrollView, HorizontalScrollView, Switch, CheckBox, RadioButton, RadioGroup, ProgressBar, SeekBar, Space, HorizontalStackLayout, VerticalStackLayout } from "../../ui/components";

export default function YogaTestPage() {
    const [clickCount, setClickCount] = useState(0);
    const [longClickCount, setLongClickCount] = useState(0);
    const [focused, setFocused] = useState(false);
    const [inputValue, setInputValue] = useState("Hello from SpotifyPlus");
    const [switchValue, setSwitchValue] = useState(true);
    const [checkValue, setCheckValue] = useState(false);
    const [sliderValue, setSliderValue] = useState(35);
    const [radioValue, setRadioValue] = useState<number | null>(null);
    const [lastEvent, setLastEvent] = useState("Nothing yet");
    const [progressValue, setProgressValue] = useState(35);

    return (
        <ScrollView style={{ flex: 1, backgroundColor: "#0b0b0b" }}>
            <VerticalStackLayout style={{ gap: 16, padding: 16 }}>
                <Text style={{ color: "#ffffff", fontSize: 32, fontWeight: "bold", marginTop: 24 }}>Yoga / Renderer Test</Text>
                <Text style={{ color: "#b3b3b3", fontSize: 14 }}>This page tries to touch as much of the renderer as possible.</Text>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 8 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Event stats</Text>
                        <Text style={{ color: "#e0e0e0" }}>Clicks: {String(clickCount)}</Text>
                        <Text style={{ color: "#e0e0e0" }}>Long clicks: {String(longClickCount)}</Text>
                        <Text style={{ color: focused ? "#1ed760" : "#ff6b6b" }}>Focused: {String(focused)}</Text>
                        <Text style={{ color: "#8ab4f8" }}>Last event: {lastEvent}</Text>
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Press / layout / borders</Text>
                        <View onClick={() => { setClickCount(x => x + 1); setLastEvent("Pressed main test card"); }} onLongClick={() => { setLongClickCount(x => x + 1); setLastEvent("Long pressed main test card"); }} style={[{ backgroundColor: "#232323", padding: 16, borderRadius: 24, borderWidth: 2, borderColor: "#1ed760" }, { borderTopLeftRadius: 32, borderBottomRightRadius: 32 }]}>
                            <Text style={{ color: "#ffffff", fontSize: 18, fontWeight: "600" }}>Tap or long press this box</Text>
                            <Text style={{ color: "#b3b3b3", marginTop: 8 }}>It tests onClick, onLongClick, style arrays, border props, and nested layout.</Text>
                        </View>
                        <HorizontalStackLayout style={{ gap: 12 }}>
                            <View style={{ flex: 1, height: 60, backgroundColor: "#2d2d2d", borderRadius: 16, justifyContent: "center", alignItems: "center" }}>
                                <Text style={{ color: "#ffffff" }}>flex: 1</Text>
                            </View>
                            <View style={{ width: 100, height: 60, backgroundColor: "#3a3a3a", borderRadius: 16, justifyContent: "center", alignItems: "center" }}>
                                <Text style={{ color: "#ffffff" }}>fixed 100</Text>
                            </View>
                        </HorizontalStackLayout>
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Text input</Text>
                        <TextInput value={inputValue} onChangeText={(text: string) => { setInputValue(text); setLastEvent(`Changed text: ${text}`); }} onFocus={() => { setFocused(true); setLastEvent("Input focused"); }} onBlur={() => { setFocused(false); setLastEvent("Input blurred"); }} onSubmitEditing={(e: any) => setLastEvent(`Submit editing: ${e?.text ?? inputValue}`)} placeholder="Type something here" style={{ backgroundColor: "#232323", color: "#ffffff", borderRadius: 16, borderWidth: 1, borderColor: focused ? "#1ed760" : "#444444", paddingHorizontal: 14, paddingVertical: 12, fontSize: 16 }} />
                        <Text style={{ color: "#b3b3b3" }}>Current text: {inputValue}</Text>
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Buttons / toggles</Text>
                        <Button title="Increment progress" onClick={() => { const next = Math.min(progressValue + 10, 100); setProgressValue(next); setSliderValue(next); setLastEvent(`Button pressed, progress = ${next}`); }} />
                        <HorizontalStackLayout style={{ justifyContent: "space-between", alignItems: "center", gap: 12 }}>
                            <Text style={{ color: "#ffffff", flex: 1 }}>Switch value: {String(switchValue)}</Text>
                            <Switch value={switchValue} onValueChange={(value: boolean) => { setSwitchValue(value); setLastEvent(`Switch changed: ${value}`); }} />
                        </HorizontalStackLayout>
                        <HorizontalStackLayout style={{ justifyContent: "space-between", alignItems: "center", gap: 12 }}>
                            <Text style={{ color: "#ffffff", flex: progressValue }}>Checkbox value: {String(checkValue)}</Text>
                            <CheckBox value={checkValue} onValueChange={(value: boolean) => { setCheckValue(value); setLastEvent(`Checkbox changed: ${value}`); }} />
                        </HorizontalStackLayout>
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Radio group</Text>
                        <Text style={{ color: "#b3b3b3" }}>Selected node id: {radioValue == null ? "none" : String(radioValue)}</Text>
                        <RadioGroup checkedId={radioValue} onValueChange={(value: number | null) => { setRadioValue(value); setLastEvent(`Radio group changed: ${value}`); }} style={{ gap: 8 }}>
                            <HorizontalStackLayout style={{ alignItems: "center", gap: 10 }}>
                                <RadioButton />
                                <Text style={{ color: "#ffffff" }}>Option 1</Text>
                            </HorizontalStackLayout>
                            <HorizontalStackLayout style={{ alignItems: "center", gap: 10 }}>
                                <RadioButton />
                                <Text style={{ color: "#ffffff" }}>Option 2</Text>
                            </HorizontalStackLayout>
                            <HorizontalStackLayout style={{ alignItems: "center", gap: 10 }}>
                                <RadioButton />
                                <Text style={{ color: "#ffffff" }}>Option 3</Text>
                            </HorizontalStackLayout>
                        </RadioGroup>
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Progress / slider</Text>
                        <Text style={{ color: "#b3b3b3" }}>Value: {String(sliderValue)}</Text>
                        <ProgressBar progress={progressValue} max={100} style={{ height: 8 }} />
                        <SeekBar progress={sliderValue} min={0} max={100} onValueChange={(value: number) => { setSliderValue(value); setProgressValue(value); setLastEvent(`Slider changed: ${value}`); }} onSlidingStart={() => setLastEvent("Slider start")} onSlidingComplete={(value: number) => setLastEvent(`Slider complete: ${value}`)} />
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Image / sizing / aspect ratio</Text>
                        <Image src="https://image-cdn-fa.spotifycdn.com/image/ab67616d0000b2734dcb6c5df15cf74596ab25a4" style={{ width: "100%", aspectRatio: 1, borderRadius: 20, overflow: "hidden", backgroundColor: "#222222" }} />
                        <Text style={{ color: "#b3b3b3" }}>If the image loads, your image source + sizing path is looking good.</Text>
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Horizontal scroll</Text>
                        <HorizontalScrollView onScroll={(e: any) => setLastEvent(`Horizontal scroll x=${e?.x ?? "?"}`)} style={{ height: 100 }}>
                            <HorizontalStackLayout style={{ gap: 12, paddingRight: 16 }}>
                                <View style={{ width: 140, height: 80, backgroundColor: "#ff6b6b", borderRadius: 16, justifyContent: "center", alignItems: "center" }}><Text style={{ color: "#ffffff", fontWeight: "bold" }}>One</Text></View>
                                <View style={{ width: 140, height: 80, backgroundColor: "#feca57", borderRadius: 16, justifyContent: "center", alignItems: "center" }}><Text style={{ color: "#000000", fontWeight: "bold" }}>Two</Text></View>
                                <View style={{ width: 140, height: 80, backgroundColor: "#48dbfb", borderRadius: 16, justifyContent: "center", alignItems: "center" }}><Text style={{ color: "#000000", fontWeight: "bold" }}>Three</Text></View>
                                <View style={{ width: 140, height: 80, backgroundColor: "#1dd1a1", borderRadius: 16, justifyContent: "center", alignItems: "center" }}><Text style={{ color: "#000000", fontWeight: "bold" }}>Four</Text></View>
                            </HorizontalStackLayout>
                        </HorizontalScrollView>
                    </VerticalStackLayout>
                </View>

                <View style={{ backgroundColor: "#181818", borderRadius: 20, borderWidth: 1, borderColor: "#2a2a2a", padding: 16, marginBottom: 40 }}>
                    <VerticalStackLayout style={{ gap: 12 }}>
                        <Text style={{ color: "#ffffff", fontSize: 22, fontWeight: "bold" }}>Nested flex test</Text>
                        <View style={{ flexDirection: "row", gap: 12 }}>
                            <View style={{ flex: 2, backgroundColor: "#222222", borderRadius: 16, padding: 12 }}>
                                <Text style={{ color: "#ffffff", fontWeight: "bold" }}>Left</Text>
                                <Space style={{ height: 8 }} />
                                <Text style={{ color: "#b3b3b3" }}>This should be wider.</Text>
                            </View>
                            <View style={{ flex: 1, backgroundColor: "#2b2b2b", borderRadius: 16, padding: 12, justifyContent: "center", alignItems: "center" }}>
                                <Text style={{ color: "#ffffff", fontWeight: "bold" }}>Right</Text>
                            </View>
                        </View>
                        <View style={{ backgroundColor: "#232323", padding: 16, borderRadius: 16, alignItems: "center", justifyContent: "center" }}>
                            <Text style={{ color: "#ffffff", textAlign: "center" }}>If this screen looks mostly right, your new layout path is in pretty solid shape.</Text>
                        </View>
                    </VerticalStackLayout>
                </View>
            </VerticalStackLayout>
        </ScrollView>
    );
}