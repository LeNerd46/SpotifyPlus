export class CallbackEvent<T extends (...args: any[]) => void> {
    private readonly listeners = new Set<T>();

    add(listener: T) {
        this.listeners.add(listener);
        return () => this.remove(listener);
    }

    remove(listener: T) {
        this.listeners.delete(listener);
    }

    dispatch(...args: Parameters<T>) {
        for (const listener of [...this.listeners]) listener(...args);
    }

    clear() {
        this.listeners.clear();
    }
}