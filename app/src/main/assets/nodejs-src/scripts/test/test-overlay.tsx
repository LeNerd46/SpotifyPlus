import React, { useEffect, useMemo, useState } from 'react';
import {
    View,
    Text,
    TextInput,
    Image,
    Button,
    ScrollView,
    HorizontalScrollView,
    ActivityIndicator,
    Slider,
    Switch,
    CheckBox,
    RadioButton,
    Row,
    Column,
    Space,
} from '../../ui/components';

function pad2(value: number) {
    return value.toString().padStart(2, '0');
}

function formatClock(date: Date) {
    return `${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
}

function formatHex(value: number) {
    return `#${(value >>> 0).toString(16).padStart(8, '0')}`;
}

const palette = ['#1db954', '#ff6b6b', '#4dabf7', '#ffd43b', '#b197fc', '#ffa94d'];

export default function RendererTestScreen() {
    const [tick, setTick] = useState(0);
    const [progress, setProgress] = useState(0);
    const [spinnerVisible, setSpinnerVisible] = useState(true);
    const [switchEnabled, setSwitchEnabled] = useState(false);
    const [checkEnabled, setCheckEnabled] = useState(true);
    const [radioEnabled, setRadioEnabled] = useState(false);
    const [textValue, setTextValue] = useState('Spotify Plus test input');
    const [marqueeIndex, setMarqueeIndex] = useState(0);

    useEffect(() => {
        const timer = setInterval(() => {
            setTick(value => value + 1);
            setProgress(value => (value + 7) % 101);
            setSpinnerVisible(value => !value);
            setSwitchEnabled(value => !value);
            setCheckEnabled(value => !value);
            setRadioEnabled(value => !value);
            setMarqueeIndex(value => (value + 1) % palette.length);
            setTextValue(value => `${value.split(' • ')[0]} • tick ${Date.now() % 10000}`);
        }, 1000);

        return () => clearInterval(timer);
    }, []);

    const backgroundColor = palette[marqueeIndex];
    const cardColor = palette[(marqueeIndex + 2) % palette.length];
    const accentColor = palette[(marqueeIndex + 4) % palette.length];
    const clock = useMemo(() => formatClock(new Date()), [tick]);

    return (
        <ScrollView
            style={{
                width: 'match_parent',
                height: 'match_parent',
                backgroundColor: '#0f1115',
                padding: 16,
            }}
            fillViewport
            smoothScrollingEnabled
        >
            <Column style={{ width: 'match_parent' }}>
                <View
                    style={{
                        backgroundColor,
                        padding: 16,
                        marginBottom: 16,
                    }}
                >
                    <Text
                        style={{
                            color: '#ffffff',
                            fontSize: 24,
                            fontWeight: '700',
                        }}
                    >
                        Spotify Plus Renderer Test
                    </Text>

                    <Text
                        style={{
                            color: '#ffffff',
                            opacity: 0.92,
                            marginTop: 6,
                            fontSize: 14,
                        }}
                    >
                        Tick {tick} • {clock}
                    </Text>

                    <Text
                        numberOfLines={1}
                        ellipsizeMode='tail'
                        style={{
                            color: '#ffffff',
                            marginTop: 10,
                            fontSize: 13,
                        }}
                    >
                        This line is intentionally pretty long so you can verify text truncation, single line handling, and live text updates without any user interaction.
                    </Text>
                </View>

                <View style={{ backgroundColor: '#171a21', padding: 14, marginBottom: 16 }}>
                    <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700' }}>Text + spacing</Text>

                    <Text style={{ color: '#cdd6f4', marginTop: 10, fontSize: 15 }}>
                        Normal body text
                    </Text>

                    <Text style={{ color: '#ffffff', marginTop: 8, fontSize: 16, fontWeight: '700' }}>
                        Bold text
                    </Text>

                    <Text style={{ color: '#ffffff', marginTop: 8, fontSize: 16, fontStyle: 'italic' }}>
                        Italic text
                    </Text>

                    <Text
                        style={{
                            color: '#ffffff',
                            marginTop: 8,
                            fontSize: 16,
                            fontWeight: '700',
                            fontStyle: 'italic',
                            letterSpacing: 1.25,
                        }}
                    >
                        Bold italic with letter spacing
                    </Text>

                    <Text
                        style={{
                            color: accentColor,
                            marginTop: 12,
                            fontSize: 14,
                            textAlign: 'center',
                            lineHeight: 22,
                        }}
                    >
                        Center aligned text using a React-Native-ish style API.
                    </Text>

                    <Space style={{ height: 10 }} />

                    <Text
                        selectable
                        style={{
                            color: '#94e2d5',
                            fontSize: 12,
                        }}
                    >
                        Selectable-ish debug text: {backgroundColor} / {cardColor} / {accentColor}
                    </Text>
                </View>

                <View style={{ backgroundColor: '#171a21', padding: 14, marginBottom: 16 }}>
                    <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700' }}>Inputs + toggles</Text>

                    <TextInput
                        value={textValue}
                        placeholder="Type-like field"
                        placeholderTextColor="#7f849c"
                        style={{
                            marginTop: 12,
                            backgroundColor: '#232833',
                            color: '#ffffff',
                            padding: 12,
                        }}
                    />

                    <TextInput
                        placeholder="Password field"
                        placeholderTextColor="#7f849c"
                        secureTextEntry
                        style={{
                            marginTop: 12,
                            backgroundColor: '#232833',
                            color: '#ffffff',
                            padding: 12,
                        }}
                    />

                    <Row style={{ marginTop: 14, alignItems: 'center' }}>
                        <Switch
                            value={switchEnabled}
                            style={{ marginRight: 12 }}
                            thumbColor={switchEnabled ? accentColor : '#888888'}
                            trackColor={accentColor}
                        />
                        <Text style={{ color: '#ffffff', fontSize: 14 }}>
                            Animated switch: {String(switchEnabled)}
                        </Text>
                    </Row>

                    <Row style={{ marginTop: 10, alignItems: 'center' }}>
                        <CheckBox value={checkEnabled} style={{ marginRight: 12 }} />
                        <Text style={{ color: '#ffffff', fontSize: 14 }}>
                            Animated checkbox: {String(checkEnabled)}
                        </Text>
                    </Row>

                    <Row style={{ marginTop: 10, alignItems: 'center' }}>
                        <RadioButton value={radioEnabled} style={{ marginRight: 12 }} />
                        <Text style={{ color: '#ffffff', fontSize: 14 }}>
                            Animated radio button: {String(radioEnabled)}
                        </Text>
                    </Row>
                </View>

                <View style={{ backgroundColor: '#171a21', padding: 14, marginBottom: 16 }}>
                    <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700' }}>Progress + loading</Text>

                    <Text style={{ color: '#cdd6f4', marginTop: 12, fontSize: 14 }}>
                        Progress: {progress}%
                    </Text>

                    <Slider
                        thumbTintColor={accentColor}
                        style={{ marginTop: 8 }}
                    />

                    <View style={{ marginTop: 12 }}>
                        <ActivityIndicator color={accentColor} animating={spinnerVisible} />
                    </View>

                    <Text style={{ color: '#cdd6f4', marginTop: 8, fontSize: 13 }}>
                        Spinner visible: {String(spinnerVisible)}
                    </Text>
                </View>

                <View style={{ backgroundColor: '#171a21', padding: 14, marginBottom: 16 }}>
                    <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700' }}>Images + rows</Text>

                    <Row style={{ marginTop: 12, alignItems: 'center' }}>
                        <Image
                            resizeMode="contain"
                            tintColor={accentColor}
                            style={{ width: 40, height: 40, marginRight: 10, backgroundColor: '#232833' }}
                        />
                        <Image
                            resizeMode="contain"
                            tintColor={cardColor}
                            style={{ width: 40, height: 40, marginRight: 10, backgroundColor: '#232833' }}
                        />
                        <Image
                            resizeMode="contain"
                            tintColor={backgroundColor}
                            style={{ width: 40, height: 40, backgroundColor: '#232833' }}
                        />
                    </Row>

                    <Text style={{ color: '#a6adc8', marginTop: 8, fontSize: 12 }}>
                        Image boxes are here to test sizing and tint props. Once you have a numeric resource id handy, pass it to source to test actual image rendering too.
                    </Text>

                    <HorizontalScrollView
                        style={{ marginTop: 14 }}
                        fillViewport={false}
                        smoothScrollingEnabled
                    >
                        <Row>
                            {palette.map((color, index) => (
                                <View
                                    key={color}
                                    style={{
                                        width: 90,
                                        height: 70,
                                        backgroundColor: color,
                                        marginRight: index === palette.length - 1 ? 0 : 10,
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                    }}
                                >
                                    <Text style={{ color: '#000000', fontSize: 12, fontWeight: '700' }}>
                                        {color}
                                    </Text>
                                </View>
                            ))}
                        </Row>
                    </HorizontalScrollView>
                </View>

                <View style={{ backgroundColor: '#171a21', padding: 14, marginBottom: 16 }}>
                    <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700' }}>Buttons</Text>

                    <Row style={{ marginTop: 12 }}>
                        <Button title="Primary" onClick={() => {
                            console.log('Clicked primary button!');
                        }} />
                        <Button title="Secondary" style={{ marginLeft: 8 }} />
                        <Button title={`Tick ${tick}`} style={{ marginLeft: 8 }} />
                    </Row>
                </View>

                <View style={{ backgroundColor: '#171a21', padding: 14, marginBottom: 16 }}>
                    <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700' }}>Layout edge cases</Text>

                    <Row style={{ marginTop: 12 }}>
                        <View style={{ flex: 1, backgroundColor: '#313244', padding: 10, marginRight: 8 }}>
                            <Text style={{ color: '#ffffff' }}>flex: 1</Text>
                        </View>
                        <View style={{ width: 100, backgroundColor: '#45475a', padding: 10 }}>
                            <Text style={{ color: '#ffffff' }}>fixed</Text>
                        </View>
                    </Row>

                    <View
                        style={{
                            marginTop: 12,
                            backgroundColor: '#232833',
                            padding: 12,
                            opacity: spinnerVisible ? 1 : 0.55,
                        }}
                    >
                        <Text style={{ color: '#ffffff' }}>
                            Opacity updates every tick.
                        </Text>
                    </View>

                    <Text style={{ color: '#a6adc8', marginTop: 10, fontSize: 12 }}>
                        Accent color int preview: {formatHex((marqueeIndex * 0x224466 + 0xff000000) >>> 0)}
                    </Text>
                </View>
            </Column>
        </ScrollView>
    );
}
