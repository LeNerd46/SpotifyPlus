import { useSyncExternalStore } from "react";
import { playbackClock } from "./playback-clock";

export function usePlaybackTime(): number {
    return useSyncExternalStore(playbackClock.subscribe, playbackClock.getTime, playbackClock.getTime);
}