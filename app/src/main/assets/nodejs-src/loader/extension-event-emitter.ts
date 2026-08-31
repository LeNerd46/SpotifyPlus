export type ExtensionEventHandler<TPayload = unknown> = (payload: TPayload) => void | Promise<void>;

interface RegisteredHandler {
    handler: ExtensionEventHandler;
    once: boolean;
}

export type ExtensionEventErrorHandler = (
    eventName: string,
    error: unknown,
) => void;

/** An event emitter whose listeners belong to exactly one extension. */
export class ExtensionEventEmitter {
    private readonly handlers = new Map<string, Set<RegisteredHandler>>();

    constructor(private readonly reportError?: ExtensionEventErrorHandler) { }

    on<TPayload = unknown>(eventName: string, handler: ExtensionEventHandler<TPayload>): void {
        this.addHandler(eventName, handler as ExtensionEventHandler, false);
    }

    once<TPayload = unknown>(eventName: string, handler: ExtensionEventHandler<TPayload>): void {
        this.addHandler(eventName, handler as ExtensionEventHandler, true);
    }

    off<TPayload = unknown>(eventName: string, handler: ExtensionEventHandler<TPayload>): void {
        const handlers = this.handlers.get(eventName);
        if (!handlers) return;

        for (const registered of handlers) {
            if (registered.handler === handler) handlers.delete(registered);
        }

        if (handlers.size === 0) this.handlers.delete(eventName);
    }

    async emit<TPayload = unknown>(eventName: string, payload?: TPayload): Promise<void> {
        const handlers = this.handlers.get(eventName);
        if (!handlers) return;

        for (const registered of Array.from(handlers)) {
            if (registered.once) handlers.delete(registered);

            try {
                await Promise.resolve(registered.handler(payload));
            } catch (error) {
                this.reportError?.(eventName, error);
            }
        }

        if (handlers.size === 0) this.handlers.delete(eventName);
    }

    clear(): void {
        this.handlers.clear();
    }

    private addHandler(eventName: string, handler: ExtensionEventHandler, once: boolean): void {
        let handlers = this.handlers.get(eventName);
        if (!handlers) {
            handlers = new Set<RegisteredHandler>();
            this.handlers.set(eventName, handlers);
        }

        for (const registered of handlers) {
            if (registered.handler === handler && registered.once === once) return;
        }

        handlers.add({ handler, once });
    }
}
