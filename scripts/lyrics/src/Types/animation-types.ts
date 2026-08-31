import React from "react";
import Spring from "../Entities/spring";
import { CallbackEvent } from "./signal";

export type TimeValue = {
    time: number;
    value: number;
};

export type TimeValueRange = TimeValue[];
export type Springs = {
    scale: Spring;
    yOffset: Spring;
    glow: Spring;
};

export type LiveText = {
    id: string;
    springs: Springs;
    setStyle?: (style: any) => void;
};

export type LetterVisual = {
    key: string;
    text: string;
    start: number;
    duration: number;
    glowDuration: number;
    liveText: LiveText;
}

export type SyllableVisual = {
    key: string;
    text: string;
    start: number;
    duration: number;
    startScale: number;
    durationScale: number;
    emphasized: boolean;
    isPartOfWord: boolean;
    isStartOfWord: boolean;
    isEndOfWord: boolean;
    liveText: LiveText;
    letters?: LetterVisual[];
}

export type WordGroupVisual = {
    key: string;
    syllables: SyllableVisual[];
}

export type LyricState = 'idle' | 'active' | 'sung';

export type BaseVocals = object;
export interface SyncedVocals extends BaseVocals {
    activityChanged: CallbackEvent<(isActive: boolean) => void>;
    requestedTimeSkip: CallbackEvent<() => void>;

    animate(songTimestamp: number, deltaTime: number, isImmediate?: true): void;
    render(): React.ReactElement;
}