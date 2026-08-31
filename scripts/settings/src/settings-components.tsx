import React, { useEffect, useRef, useState } from 'react';
import { SpotifyPlus } from 'spotifyplus';
import { Pressable, Slider, Text, TextInput, ToggleButton, View } from 'spotifyplus/react';
import { ExtensionHeader, ExtensionOption, ExtensionSetting, ExtensionSettingItem } from './settings';
import Animated from 'spotifyplus/react/animated';

interface SettingProps<T extends ExtensionSetting> {
    setting: T;
    onChange: (setting: ExtensionSetting) => void;
}

interface SettingCopyProps {
    label: string;
    description?: string;
    disabled?: boolean;
}

const SettingCopy = ({ label, description, disabled }: SettingCopyProps) => (
    <View style={{ flex: 1, paddingRight: 16 }}>
        <Text style={{ color: disabled ? '#777777' : '#ffffff', fontSize: 16, fontWeight: '500' }}>
            {label}
        </Text>

        {description && (
            <Text style={{ color: disabled ? '#555555' : '#b3b3b3', fontSize: 13, lineHeight: 19, marginTop: 4 }}>
                {description}
            </Text>
        )}
    </View>
);

interface SettingFieldProps {
    value: string;
    placeholder?: string;
    disabled?: boolean;
    keyboardType?: 'default' | 'numeric' | 'decimal-pad';
    maxLength?: number;
    onChangeText: (value: string) => void;
}

const SettingField = ({ value, placeholder, disabled, keyboardType, maxLength, onChangeText }: SettingFieldProps) => (
    <TextInput
        value={value}
        placeholder={placeholder}
        placeholderTextColor='#777777'
        editable={!disabled}
        keyboardType={keyboardType}
        maxLength={maxLength}
        singleLine
        onChangeText={onChangeText}
        style={{
            minHeight: 44,
            backgroundColor: disabled ? '#191919' : '#2a2a2a',
            borderColor: disabled ? '#333333' : '#535353',
            borderWidth: 1,
            borderRadius: 8,
            color: disabled ? '#777777' : '#ffffff',
            fontSize: 14,
            paddingHorizontal: 12,
            marginTop: 12
        }}
    />
);

interface SmallButtonProps {
    label: string;
    disabled?: boolean;
    primary?: boolean;
    onPress: () => void;
}

const SmallButton = ({ label, disabled, primary, onPress }: SmallButtonProps) => (
    <Pressable
        disabled={disabled}
        onPress={onPress}
        style={{
            minHeight: 36,
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: disabled ? '#252525' : primary ? '#ffffff' : '#333333',
            borderRadius: 999,
            paddingHorizontal: 14
        }}
    >
        <Text style={{ color: disabled ? '#666666' : primary ? '#000000' : '#ffffff', fontSize: 13, fontWeight: '700' }}>
            {label}
        </Text>
    </Pressable>
);

export const ExtensionSettingDivider = () => (
    <View style={{ height: 1, backgroundColor: '#333333', marginHorizontal: 14, marginVertical: 4 }} />
);

export const ExtensionSettingHeader = ({ setting }: { setting: ExtensionHeader }) => (
    <View style={{ paddingHorizontal: 14, paddingTop: 18, paddingBottom: 8 }}>
        <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700' }}>
            {setting.label}
        </Text>

        {setting.description && (
            <Text style={{ color: '#b3b3b3', fontSize: 13, lineHeight: 19, marginTop: 4 }}>
                {setting.description}
            </Text>
        )}
    </View>
);

interface SpotifyToggleProps {
    value: boolean;
    onValueChange: (value: boolean) => void;
    disabled?: boolean;
}

export const ExtensionToggleSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'toggle' }>>) => {
    const progress = useRef(new Animated.Value(setting.value ? 1 : 0)).current;

    useEffect(() => {
        Animated.timing(progress, {
            toValue: setting.value ? 1 : 0,
            duration: 160,
            useNativeDriver: false,
        }).start();
    }, [setting.value]);

    const thumbTranslateX = progress.interpolate({
        inputRange: [0, 1],
        outputRange: [3, 25],
    });

    const backgroundColor = progress.interpolate({
        inputRange: [0, 1],
        outputRange: ['#292929', '#1ED760'],
    });

    const borderColor = progress.interpolate({
        inputRange: [0, 1],
        outputRange: ['#A7A7A7', '#1ED760'],
    });

    return (
        <Pressable onPress={() => !setting.disabled && onChange({ ...setting, value: !setting.value })} disabled={setting.disabled}>
            <View style={{ flexDirection: 'row', alignItems: 'space-between', marginHorizontal: 12 }}>
                <SettingCopy {...setting} />

                <Animated.View
                    style={{
                        width: 52,
                        height: 32,
                        borderRadius: 16,
                        borderWidth: 2,
                        borderColor,
                        backgroundColor,
                        justifyContent: 'center',
                        opacity: setting.disabled ? 0.5 : 1,
                    }}
                >
                    <Animated.View
                        style={{
                            width: 22,
                            height: 22,
                            borderRadius: 11,
                            backgroundColor: setting.value ? '#121212' : '#B3B3B3',
                            transform: [{ translateX: thumbTranslateX }],
                        }}
                    />
                </Animated.View>
            </View>
        </Pressable>
    );
};

export const ExtensionToggleSettingOld = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'toggle' }>>) => {
    const change = () => {
        if (!setting.disabled) {
            onChange({ ...setting, value: !setting.value });
        }
    };

    return (
        <Pressable
            disabled={setting.disabled}
            onPress={change}
            style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 14 }}
        >
            <SettingCopy {...setting} />
            <ToggleButton value={setting.value} enabled={!setting.disabled} />
        </Pressable>
    );
};

export const ExtensionSliderSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'slider' }>>) => {
    const step = setting.step && setting.step > 0 ? setting.step : 1;
    const span = setting.max - setting.min;
    const progress = span <= 0 ? 0 : Math.max(0, Math.min(1, (setting.value - setting.min) / span));

    const setValue = (value: number) => {
        if (setting.disabled) {
            return;
        }

        const clamped = Math.max(setting.min, Math.min(setting.max, value));
        const rounded = Math.round((clamped - setting.min) / step) * step + setting.min;
        onChange({ ...setting, value: Number(rounded.toFixed(10)) });
    };

    return (
        <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 12 }}>
                <SettingCopy {...setting} />
                <Text style={{ color: setting.disabled ? '#777777' : '#ffffff', fontSize: 14, fontWeight: '600' }}>
                    {setting.value}
                </Text>
            </View>

            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <SmallButton label='−' disabled={setting.disabled || setting.value <= setting.min} onPress={() => setValue(setting.value - step)} />

                <Slider progress={setting.value} min={setting.min} max={setting.max} onValueChange={(value) => onChange({ ...setting, value })} style={{ flex: 1, height: 4, backgroundColor: '#535353', borderRadius: 999, overflow: 'hidden', marginHorizontal: 12 }} />

                {/* <View style={{ flex: 1, height: 4, backgroundColor: '#535353', borderRadius: 999, overflow: 'hidden', marginHorizontal: 12 }}>
                    <View
                        style={{
                            width: `${progress * 100}%`,
                            height: 4,
                            backgroundColor: setting.disabled ? '#777777' : '#ffffff',
                            borderRadius: 999
                        }}
                    />
                </View> */}

                <SmallButton label='+' disabled={setting.disabled || setting.value >= setting.max} onPress={() => setValue(setting.value + step)} />
            </View>
        </View>
    );
};

export const ExtensionTextSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'text' }>>) => (
    <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
        <SettingCopy {...setting} />
        <SettingField
            value={setting.value}
            placeholder={setting.placeholder}
            disabled={setting.disabled}
            maxLength={setting.maxLength}
            onChangeText={value => onChange({ ...setting, value })}
        />
    </View>
);

export const ExtensionNumberSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'number' }>>) => {
    const [draft, setDraft] = useState(String(setting.value));
    const step = setting.step && setting.step > 0 ? setting.step : 1;

    useEffect(() => {
        setDraft(String(setting.value));
    }, [setting.value]);

    const change = (value: string) => {
        setDraft(value);

        const parsed = Number(value);
        if (setting.disabled || value.trim() === '' || !Number.isFinite(parsed)) {
            return;
        }

        const minimum = setting.min ?? Number.NEGATIVE_INFINITY;
        const maximum = setting.max ?? Number.POSITIVE_INFINITY;
        const clamped = Math.max(minimum, Math.min(maximum, parsed));
        onChange({ ...setting, value: clamped });
    };

    return (
        <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
            <SettingCopy {...setting} />
            <SettingField
                value={draft}
                placeholder={setting.placeholder}
                disabled={setting.disabled}
                keyboardType='decimal-pad'
                onChangeText={change}
            />

            {setting.step !== undefined && (
                <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 10 }}>
                    <SmallButton
                        label='−'
                        disabled={setting.disabled || (setting.min !== undefined && setting.value <= setting.min)}
                        onPress={() => change(String(setting.value - step))}
                    />
                    <Text style={{ flex: 1, color: '#777777', fontSize: 12, textAlign: 'center' }}>
                        Step: {step}
                    </Text>
                    <SmallButton
                        label='+'
                        disabled={setting.disabled || (setting.max !== undefined && setting.value >= setting.max)}
                        onPress={() => change(String(setting.value + step))}
                    />
                </View>
            )}
        </View>
    );
};

interface OptionRowProps {
    option: ExtensionOption;
    selected: boolean;
    disabled?: boolean;
    multiple?: boolean;
    onPress: () => void;
}

const OptionRow = ({ option, selected, disabled, multiple, onPress }: OptionRowProps) => (
    <Pressable
        disabled={disabled}
        onPress={onPress}
        style={{ flexDirection: 'row', alignItems: 'center', minHeight: 48, paddingVertical: 8, borderColor: '#333333', borderTopWidth: 1 }}
    >
        <View
            style={{
                width: 20,
                height: 20,
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: multiple ? 4 : 999,
                borderWidth: 2,
                borderColor: disabled ? '#555555' : selected ? '#1ed760' : '#777777',
                backgroundColor: selected ? '#1ed760' : 'transparent',
                marginRight: 12
            }}
        >
            {selected && (
                <Text style={{ color: '#121212', fontSize: 13, fontWeight: '900' }}>
                    {multiple ? '✓' : '•'}
                </Text>
            )}
        </View>

        <View style={{ flex: 1 }}>
            <Text style={{ color: disabled ? '#777777' : '#ffffff', fontSize: 14 }}>
                {option.label}
            </Text>

            {option.description && (
                <Text style={{ color: disabled ? '#555555' : '#999999', fontSize: 12, lineHeight: 17, marginTop: 2 }}>
                    {option.description}
                </Text>
            )}
        </View>
    </Pressable>
);

export const ExtensionSelectSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'select' }>>) => {
    const [expanded, setExpanded] = useState(false);
    const selected = setting.options.find(option => option.value === setting.value);

    return (
        <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
            <SettingCopy {...setting} />

            <Pressable
                disabled={setting.disabled}
                onPress={() => setExpanded(value => !value)}
                style={{ minHeight: 44, flexDirection: 'row', alignItems: 'center', backgroundColor: setting.disabled ? '#191919' : '#2a2a2a', borderRadius: 8, paddingHorizontal: 12, marginTop: 12 }}
            >
                <Text style={{ flex: 1, color: setting.disabled ? '#777777' : '#ffffff', fontSize: 14 }}>
                    {selected?.label ?? 'Select an option'}
                </Text>
                <Text style={{ color: '#b3b3b3', fontSize: 16 }}>
                    {expanded ? '⌃' : '⌄'}
                </Text>
            </Pressable>

            {expanded && !setting.disabled && (
                <View style={{ marginTop: 6 }}>
                    {setting.options.map(option => (
                        <OptionRow
                            key={option.value}
                            option={option}
                            selected={option.value === setting.value}
                            onPress={() => {
                                onChange({ ...setting, value: option.value });
                                setExpanded(false);
                            }}
                        />
                    ))}
                </View>
            )}
        </View>
    );
};

export const ExtensionMultiSelectSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'multi-select' }>>) => {
    const toggleOption = (value: string) => {
        if (setting.disabled) {
            return;
        }

        const nextValue = setting.value.includes(value)
            ? setting.value.filter(item => item !== value)
            : [...setting.value, value];
        onChange({ ...setting, value: nextValue });
    };

    return (
        <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
            <SettingCopy {...setting} />
            <View style={{ marginTop: 10 }}>
                {setting.options.map(option => (
                    <OptionRow
                        key={option.value}
                        option={option}
                        selected={setting.value.includes(option.value)}
                        disabled={setting.disabled}
                        multiple
                        onPress={() => toggleOption(option.value)}
                    />
                ))}
            </View>
        </View>
    );
};

export const ExtensionRadioSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'radio' }>>) => (
    <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
        <SettingCopy {...setting} />
        <View style={{ marginTop: 10 }}>
            {setting.options.map(option => (
                <OptionRow
                    key={option.value}
                    option={option}
                    selected={setting.value === option.value}
                    disabled={setting.disabled}
                    onPress={() => onChange({ ...setting, value: option.value })}
                />
            ))}
        </View>
    </View>
);

export const ExtensionColorSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'color' }>>) => (
    <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <SettingCopy {...setting} />
            <View
                style={{
                    width: 34,
                    height: 34,
                    borderRadius: 8,
                    borderColor: '#777777',
                    borderWidth: 1,
                    backgroundColor: isValidHexColor(setting.value, setting.alpha) ? setting.value : '#2a2a2a'
                }}
            />
        </View>
        <SettingField
            value={setting.value}
            placeholder={setting.alpha ? '#RRGGBBAA' : '#RRGGBB'}
            disabled={setting.disabled}
            maxLength={setting.alpha ? 9 : 7}
            onChangeText={value => onChange({ ...setting, value })}
        />
    </View>
);

export const ExtensionButtonSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'button' }>>) => (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 14 }}>
        <SettingCopy {...setting} />
        <SmallButton label={setting.buttonLabel ?? setting.label} disabled={setting.disabled} primary onPress={() => onChange(setting)} />
    </View>
);

interface DateTimeSettingProps {
    setting: Extract<ExtensionSetting, { type: 'date' | 'time' }>;
    onChange: (setting: ExtensionSetting) => void;
}

const ExtensionDateTimeSetting = ({ setting, onChange }: DateTimeSettingProps) => (
    <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
        <SettingCopy {...setting} />
        <SettingField
            value={setting.value}
            placeholder={setting.type === 'date' ? 'YYYY-MM-DD' : 'HH:MM'}
            disabled={setting.disabled}
            maxLength={setting.type === 'date' ? 10 : 5}
            onChangeText={value => onChange({ ...setting, value })}
        />

        {setting.type === 'date' && (setting.min || setting.max) && (
            <Text style={{ color: '#777777', fontSize: 12, marginTop: 6 }}>
                {setting.min ? `From ${setting.min}` : ''}
                {setting.min && setting.max ? ' • ' : ''}
                {setting.max ? `Through ${setting.max}` : ''}
            </Text>
        )}
    </View>
);

export const ExtensionDateSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'date' }>>) => (
    <ExtensionDateTimeSetting setting={setting} onChange={onChange} />
);

export const ExtensionTimeSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'time' }>>) => (
    <ExtensionDateTimeSetting setting={setting} onChange={onChange} />
);

export const ExtensionRangeSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'range' }>>) => {
    const step = setting.step && setting.step > 0 ? setting.step : 1;

    const setValue = (index: 0 | 1, value: number) => {
        if (setting.disabled) {
            return;
        }

        const otherValue = setting.value[index === 0 ? 1 : 0];
        const boundary = index === 0 ? Math.min(setting.max, otherValue) : Math.max(setting.min, otherValue);
        const clamped = index === 0
            ? Math.max(setting.min, Math.min(boundary, value))
            : Math.min(setting.max, Math.max(boundary, value));
        const rounded = Math.round((clamped - setting.min) / step) * step + setting.min;
        const nextValue: [number, number] = [...setting.value];
        nextValue[index] = Number(rounded.toFixed(10));
        onChange({ ...setting, value: nextValue });
    };

    return (
        <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
            <SettingCopy {...setting} />

            <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 12 }}>
                <SmallButton label='−' disabled={setting.disabled || setting.value[0] <= setting.min} onPress={() => setValue(0, setting.value[0] - step)} />
                <Text style={{ flex: 1, color: '#ffffff', fontSize: 14, textAlign: 'center' }}>
                    Minimum: {setting.value[0]}
                </Text>
                <SmallButton label='+' disabled={setting.disabled || setting.value[0] >= setting.value[1]} onPress={() => setValue(0, setting.value[0] + step)} />
            </View>

            <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 10 }}>
                <SmallButton label='−' disabled={setting.disabled || setting.value[1] <= setting.value[0]} onPress={() => setValue(1, setting.value[1] - step)} />
                <Text style={{ flex: 1, color: '#ffffff', fontSize: 14, textAlign: 'center' }}>
                    Maximum: {setting.value[1]}
                </Text>
                <SmallButton label='+' disabled={setting.disabled || setting.value[1] >= setting.max} onPress={() => setValue(1, setting.value[1] + step)} />
            </View>
        </View>
    );
};

interface PathSettingProps {
    setting: Extract<ExtensionSetting, { type: 'file' | 'directory' }>;
    onChange: (setting: ExtensionSetting) => void;
}

const ExtensionPathSetting = ({ setting, onChange }: PathSettingProps) => (
    <View style={{ paddingHorizontal: 14, paddingVertical: 14 }}>
        <SettingCopy {...setting} />
        <SettingField
            value={setting.value ?? ''}
            placeholder={setting.type === 'file' ? 'Enter a file path' : 'Enter a directory path'}
            disabled={setting.disabled}
            onChangeText={value => onChange({ ...setting, value: value || undefined })}
        />

        {setting.type === 'file' && setting.accept && setting.accept.length > 0 && (
            <Text style={{ color: '#777777', fontSize: 12, marginTop: 6 }}>
                Accepted: {setting.accept.join(', ')}
            </Text>
        )}
    </View>
);

export const ExtensionFileSetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'file' }>>) => (
    <ExtensionPathSetting setting={setting} onChange={onChange} />
);

export const ExtensionDirectorySetting = ({ setting, onChange }: SettingProps<Extract<ExtensionSetting, { type: 'directory' }>>) => (
    <ExtensionPathSetting setting={setting} onChange={onChange} />
);

export const ExtensionInfoSetting = ({ setting }: { setting: Extract<ExtensionSetting, { type: 'info' }> }) => (
    <View style={{ backgroundColor: '#252525', borderRadius: 8, marginHorizontal: 14, marginVertical: 10, padding: 12 }}>
        <Text style={{ color: '#ffffff', fontSize: 14, fontWeight: '600' }}>
            {setting.label}
        </Text>
        <Text style={{ color: '#b3b3b3', fontSize: 13, lineHeight: 19, marginTop: 4 }}>
            {setting.text}
        </Text>
    </View>
);

export const ExtensionLinkSetting = ({ setting }: { setting: Extract<ExtensionSetting, { type: 'link' }> }) => (
    <Pressable
        disabled={setting.disabled}
        onPress={() => SpotifyPlus.Navigation.open(setting.url)}
        style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 14 }}
    >
        <SettingCopy {...setting} />
        <Text style={{ color: setting.disabled ? '#555555' : '#ffffff', fontSize: 14, fontWeight: '700' }}>
            {setting.linkLabel ?? 'Open'} ↗
        </Text>
    </Pressable>
);

interface ExtensionSettingItemRendererProps {
    item: ExtensionSettingItem;
    onChange: (setting: ExtensionSetting) => void;
}

export const ExtensionSettingItemRenderer = ({ item, onChange }: ExtensionSettingItemRendererProps) => {
    switch (item.type) {
        case 'divider':
            return <ExtensionSettingDivider />;
        case 'header':
            return <ExtensionSettingHeader setting={item} />;
        case 'toggle':
            return <ExtensionToggleSetting setting={item} onChange={onChange} />;
        case 'slider':
            return <ExtensionSliderSetting setting={item} onChange={onChange} />;
        case 'text':
            return <ExtensionTextSetting setting={item} onChange={onChange} />;
        case 'number':
            return <ExtensionNumberSetting setting={item} onChange={onChange} />;
        case 'select':
            return <ExtensionSelectSetting setting={item} onChange={onChange} />;
        case 'multi-select':
            return <ExtensionMultiSelectSetting setting={item} onChange={onChange} />;
        case 'radio':
            return <ExtensionRadioSetting setting={item} onChange={onChange} />;
        case 'color':
            return <ExtensionColorSetting setting={item} onChange={onChange} />;
        case 'button':
            return <ExtensionButtonSetting setting={item} onChange={onChange} />;
        case 'date':
            return <ExtensionDateSetting setting={item} onChange={onChange} />;
        case 'time':
            return <ExtensionTimeSetting setting={item} onChange={onChange} />;
        case 'range':
            return <ExtensionRangeSetting setting={item} onChange={onChange} />;
        case 'file':
            return <ExtensionFileSetting setting={item} onChange={onChange} />;
        case 'directory':
            return <ExtensionDirectorySetting setting={item} onChange={onChange} />;
        case 'info':
            return <ExtensionInfoSetting setting={item} />;
        case 'link':
            return <ExtensionLinkSetting setting={item} />;
    }
};

const isValidHexColor = (value: string, alpha?: boolean) => {
    const expression = alpha ? /^#[0-9a-f]{8}$/i : /^#[0-9a-f]{6}$/i;
    return expression.test(value);
};
