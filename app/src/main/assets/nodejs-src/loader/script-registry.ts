import React from "react";
import { Logger } from "../core/logger";
import { ContextMenu, SideDrawerItem, Surface } from "../core/models";
import { ScriptManifest } from "./script-manifest";
import { createRoot, RenderRoot } from "../ui/renderer";
import type { ScriptTrust } from "./script-loader";
import { ExtensionEventEmitter } from "./extension-event-emitter";

export type EventHandler = (payload: unknown) => void | Promise<void>;

export interface Script {
    manifest: ScriptManifest;
    directoryPath: string;
    trust: ScriptTrust;
    generation: number;
}

interface RegisteredContextMenu {
    scriptId: string;
    id: string;
    menu: ContextMenu;
}

interface RegisteredSideDrawer {
    scriptId: string;
    id: string;
    item: SideDrawerItem;
}

export type SurfaceRenderer<T extends string = string> = (surface: Surface & { type: T }) => React.ReactElement;

export type RegisteredSurfaceRenderer = {
    scriptId: string;
    surfaceType: string;
    renderer: SurfaceRenderer<any>;
}

export class ScriptRegistry {
    private readonly scripts = new Map<string, Script>();
    private readonly eventHandlers = new Map<string, Map<string, Set<EventHandler>>>();
    private readonly extensionEventEmitters = new Map<string, ExtensionEventEmitter>();
    private readonly menus = new Map<string, RegisteredContextMenu>();
    private readonly sideDrawerItems = new Map<string, RegisteredSideDrawer>();
    private readonly renderers = new Map<string, RegisteredSurfaceRenderer[]>();
    private readonly mountedSurfaces = new Map<string, RenderRoot>();
    private readonly cleanupCallbacks = new Map<string, Set<() => void>>();

    constructor(
        private readonly logger: Logger,
        private readonly reportScriptError?: (scriptId: string, message: string, error: unknown) => void,
    ) { }

    registerScript(script: Script): void {
        if (this.scripts.has(script.manifest.id)) throw new Error(`Duplicate script ID '${script.manifest.id}'`);
        this.scripts.set(script.manifest.id, script);
        this.logger.info(`Registered script ${script.manifest.id}`);
    }

    unregisterScript(scriptId: string): string[] {
        this.scripts.delete(scriptId);

        this.extensionEventEmitters.get(scriptId)?.clear();
        this.extensionEventEmitters.delete(scriptId);

        for (const [eventName, scriptMap] of this.eventHandlers.entries()) {
            scriptMap.delete(scriptId);
            if (scriptMap.size === 0) this.eventHandlers.delete(eventName);
        }

        for (const [id, menu] of this.menus.entries()) {
            if (menu.scriptId === scriptId) this.menus.delete(id);
        }

        for (const [id, item] of this.sideDrawerItems.entries()) {
            if (item.scriptId === scriptId) this.sideDrawerItems.delete(id);
        }

        for (const [surfaceType, entries] of this.renderers.entries()) {
            const remaining = entries.filter(entry => entry.scriptId !== scriptId);
            if (remaining.length === 0) this.renderers.delete(surfaceType);
            else this.renderers.set(surfaceType, remaining);
        }

        const surfaceIds = new Set<string>();
        for (const [key, root] of this.mountedSurfaces.entries()) {
            if (!key.startsWith(`${scriptId}:`)) continue;
            try {
                root.unmount();
            } catch (error) {
                this.logger.error(`Failed to unmount ${key}`, error);
            }

            this.mountedSurfaces.delete(key);
            surfaceIds.add(key.slice(scriptId.length + 1));
        }

        const cleanups = this.cleanupCallbacks.get(scriptId);
        if (cleanups) {
            for (const cleanup of cleanups) {
                try {
                    cleanup();
                } catch (error) {
                    this.logger.error(`Cleanup failed for script ${scriptId}`, error);
                }
            }
            this.cleanupCallbacks.delete(scriptId);
        }

        this.logger.info(`Unregistered script ${scriptId}`);
        return Array.from(surfaceIds);
    }

    getScript(scriptId: string): Script | undefined {
        return this.scripts.get(scriptId);
    }

    getScripts(): Script[] {
        return Array.from(this.scripts.values());
    }

    getExtensionEventEmitter(scriptId: string): ExtensionEventEmitter {
        let emitter = this.extensionEventEmitters.get(scriptId);
        if (!emitter) {
            emitter = new ExtensionEventEmitter((eventName, error) => {
                if (this.reportScriptError) {
                    this.reportScriptError(scriptId, `Handler failed for extension event ${eventName}`, error);
                } else {
                    this.logger.error(`Handler failed for script ${scriptId} on extension event ${eventName}`, error);
                }
            });
            this.extensionEventEmitters.set(scriptId, emitter);
        }

        return emitter;
    }

    async emitToExtension(scriptId: string, eventName: string, payload: unknown): Promise<void> {
        const emitter = this.extensionEventEmitters.get(scriptId);
        if (!emitter) return;

        await emitter.emit(eventName, payload);
    }

    on(scriptId: string, eventName: string, handler: EventHandler): void {
        let scriptMap = this.eventHandlers.get(eventName);
        if (!scriptMap) {
            scriptMap = new Map<string, Set<EventHandler>>();
            this.eventHandlers.set(eventName, scriptMap);
        }

        let handlers = scriptMap.get(scriptId);
        if (!handlers) {
            handlers = new Set<EventHandler>();
            scriptMap.set(scriptId, handlers);
        }

        handlers.add(handler);
        this.logger.info(`Script ${scriptId} subscribed to ${eventName}`);
    }

    off(scriptId: string, eventName: string, handler: EventHandler): void {
        const scriptMap = this.eventHandlers.get(eventName);
        const handlers = scriptMap?.get(scriptId);
        if (!handlers) return;

        handlers.delete(handler);
        if (handlers.size === 0) scriptMap?.delete(scriptId);
        if (scriptMap && scriptMap.size === 0) this.eventHandlers.delete(eventName);
    }

    async emit(eventName: string, payload: unknown): Promise<void> {
        const scriptMap = this.eventHandlers.get(eventName);
        if (!scriptMap) return;

        for (const [scriptId, handlers] of scriptMap.entries()) {
            await this.emitToHandlers(scriptId, eventName, handlers, payload);
        }
    }

    async emitToScript(scriptId: string, eventName: string, payload: unknown): Promise<void> {
        const handlers = this.eventHandlers.get(eventName)?.get(scriptId);
        if (!handlers) return;

        await this.emitToHandlers(scriptId, eventName, handlers, payload);
    }

    private async emitToHandlers(
        scriptId: string,
        eventName: string,
        handlers: Set<EventHandler>,
        payload: unknown,
    ): Promise<void> {
        for (const handler of Array.from(handlers)) {
            try {
                await Promise.resolve(handler(payload));
            } catch (error) {
                if (this.reportScriptError) {
                    this.reportScriptError(scriptId, `Handler failed for event ${eventName}`, error);
                } else {
                    this.logger.error(`Handler failed for script ${scriptId} on event ${eventName}`, error);
                }
            }
        }
    }

    registerContextMenu(scriptId: string, id: string, menu: ContextMenu): void {
        this.menus.set(id, { scriptId, id, menu });
    }

    emitContextMenuPress(scriptId: string, id: string, uri: string): void {
        const menu = this.menus.get(id);
        menu?.menu.onClick(uri);
    }

    registerSideDrawer(scriptId: string, id: string, item: SideDrawerItem): void {
        this.sideDrawerItems.set(id, { scriptId, id, item });
    }

    getSideDrawerItems() {
        return this.sideDrawerItems;
    }

    emitSideDrawerPress(scriptId: string, id: string): boolean {
        console.log('Made it here!');
        const item = this.sideDrawerItems.get(id);
        console.log(`Item Found: ${item?.item.name}`);
        const result = item?.item.onClick();
        console.log('Called result!');
        if (result && React.isValidElement(result)) {
            console.log('Item is valid react element');
            this.mountSurface(scriptId, { id: 'sideDrawer', type: 'sideDrawer' }, result);
            return true;
        }

        return false;
    }

    registerSurfaceRenderer<T extends string>(scriptId: string, surfaceType: T, renderer: SurfaceRenderer<T>): void {
        const existing = this.renderers.get(surfaceType) ?? [];
        existing.push({ scriptId, surfaceType, renderer });
        this.renderers.set(surfaceType, existing);
    }

    addCleanup(scriptId: string, cleanup: () => void): void {
        const existing = this.cleanupCallbacks.get(scriptId) ?? new Set<() => void>();
        existing.add(cleanup);
        this.cleanupCallbacks.set(scriptId, existing);
    }

    getSurfaceRenderers(surfaceType: string): RegisteredSurfaceRenderer[] {
        return this.renderers.get(surfaceType) ?? [];
    }

    mountSurface(scriptId: string, surface: Surface, element: React.ReactElement): void {
        const key = `${scriptId}:${surface.id}`;
        const existing = this.mountedSurfaces.get(key);
        if (existing) existing.unmount();

        const root = createRoot(surface.type);
        root.render(element);
        this.mountedSurfaces.set(key, root);
    }

    trackMountedRoot(scriptId: string, surfaceId: string, root: RenderRoot): void {
        const key = `${scriptId}:${surfaceId}`;
        const existing = this.mountedSurfaces.get(key);
        if (existing) existing.unmount();
        this.mountedSurfaces.set(key, root);
    }

    unmountSurface(scriptId: string, surfaceId: string): void {
        const key = `${scriptId}:${surfaceId}`;
        const root = this.mountedSurfaces.get(key);
        root?.unmount();
        this.mountedSurfaces.delete(key);
    }

    unmountAllSurfaces(surfaceId: string): void {
        this.mountedSurfaces.forEach((root, key) => {
            if (key.endsWith(`:${surfaceId}`)) {
                root.unmount();
                this.mountedSurfaces.delete(key);
            }
        });
    }
}
