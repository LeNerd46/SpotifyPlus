export interface FetchGlobals {
    fetch?: (...args: any[]) => Promise<any>;
    Headers?: any;
    Request?: any;
    Response?: any;
}

export interface FetchGlobalResolution {
    globals: FetchGlobals;
    fallbackError?: unknown;
}

export function resolveFetchGlobals(
    loadFallback?: () => any,
    hostGlobals: Record<string, any> = globalThis as any,
): FetchGlobalResolution {
    const globals: FetchGlobals = {};
    if (typeof hostGlobals.fetch === 'function') {
        globals.fetch = hostGlobals.fetch.bind(hostGlobals);
        globals.Headers = hostGlobals.Headers;
        globals.Request = hostGlobals.Request;
        globals.Response = hostGlobals.Response;
        return { globals };
    }

    if (!loadFallback) return { globals };

    try {
        const loaded = loadFallback();
        const fetchImpl = loaded?.default ?? loaded;
        if (typeof fetchImpl === 'function') globals.fetch = fetchImpl;
        globals.Headers = loaded?.Headers;
        globals.Request = loaded?.Request;
        globals.Response = loaded?.Response;
        return { globals };
    } catch (fallbackError) {
        return { globals, fallbackError };
    }
}
