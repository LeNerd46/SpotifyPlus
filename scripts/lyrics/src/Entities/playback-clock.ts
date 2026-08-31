import { SpotifyPlus } from "spotifyplus";

type Listener = (time: number) => void;

class PlaybackClock {
    private currentTime = 0;
    private listeners = new Set<Listener>();
    private running = false;

    private syncTimer: any = null;
    private timer: any = null;

    private lastProgressSeconds = 0;
    private lastSyncTimestamp = 0;
    private isPlaying = false;

    public getTime = (): number => this.currentTime;

    public subscribe = (listener: Listener) => {
        this.listeners.add(listener);
        listener(this.currentTime);

        if (!this.running) {
            this.start();
        }

        return () => {
            this.listeners.delete(listener);
            if (this.listeners.size === 0) {
                this.stop();
            }
        };
    }

    private emit() {
        for (const listener of this.listeners) {
            listener(this.currentTime);
        }
    }

    private start() {
        if (this.running) return;
        this.running = true;
        this.isPlaying = true;

        this.syncFromSpotify();

        this.syncTimer = setInterval(() => {
            this.syncFromSpotify();
        }, 250);

        this.timer = setInterval(() => {
            this.update();
            this.emit();
        }, 12);
    }

    private update = () => {
        if (!this.isPlaying) return;

        const now = Date.now();
        const deltaTime = (now - this.lastSyncTimestamp) / 1000;
        this.currentTime = this.lastProgressSeconds + deltaTime;
    }

    private syncFromSpotify = async () => {
        try {
            const time = await SpotifyPlus.Player.getProgress();
            if (time == null) return;

            this.lastProgressSeconds = time / 1000;
            this.lastSyncTimestamp = Date.now();
            this.currentTime = this.lastProgressSeconds;
            this.emit();
        } catch (e) {
            SpotifyPlus.log('Playback clock sync failed', e);
        }
    }

    private stop() {
        this.running = false;

        if (this.syncTimer != null) {
            clearInterval(this.syncTimer);
            this.syncTimer = null;
        }

        if (this.timer != null) {
            clearInterval(this.timer);
            this.timer = null;
        }
    }
}

export const playbackClock = new PlaybackClock();