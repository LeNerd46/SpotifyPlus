import type React from 'react';

/** Names are stable; inspect() reports the capabilities of the installed Spotify build. */
export type UITargetName =
    | 'home.page' | 'home.header' | 'home.section' | 'home.item'
    | 'search.page' | 'search.field' | 'search.filters' | 'search.results' | 'search.item'
    | 'library.page' | 'library.header' | 'library.filters' | 'library.list' | 'library.item'
    | `${'playlist' | 'album' | 'artist'}.${'page' | 'header' | 'actions' | 'section' | 'list' | 'item'}`
    | 'artist.discography.page' | 'settings.page' | 'profile.page'
    | `nowPlaying.${'page' | 'header' | 'artwork' | 'trackInfo' | 'controls' | 'seekBar' | 'card'}`
    | `miniPlayer.${'root' | 'artwork' | 'trackInfo' | 'controls'}`
    | `queue.${'page' | 'header' | 'content' | 'item'}`
    | `lyrics.${'page' | 'header' | 'content' | 'line'}`
    | `contextMenu.${'root' | 'header' | 'actions' | 'action'}`
    | 'navigation.bar' | 'navigation.item' | 'navigation.drawer'
    | `${string}:${string}`;

export type UIOperation = 'replace' | 'before' | 'after' | 'overlay';
export type UISelector = { screen: UITargetName } & (
    { resourceId: string; composeTag?: never } | { composeTag: string; resourceId?: never }
);
export type UITarget = UITargetName | UISelector;
/** IDs belong to this live instance and may change when Spotify updates its model. */
export interface UIPart {
    readonly id: string;
    readonly semanticId: string;
    readonly kind: 'action' | 'content';
    readonly title: string | null;
    readonly enabled: boolean;
}
export interface UIInstance {
    readonly target: string;
    readonly context: UITargetContext;
}
export interface UITargetContext {
    readonly instanceId: string;
    readonly uri: string | null;
    readonly pageUri: string | null;
    readonly title?: string | null;
    readonly subtitle?: string | null;
    readonly actionId?: string;
    readonly enabled?: boolean;
    /** Native menu actions/drawer rows, including content regions such as messaging. */
    readonly parts?: readonly UIPart[];
}
export interface UIComponentProps {
    readonly context: UITargetContext;
    /** Mount once, within a replacement of this target. Native interaction remains native. */
    readonly Original: React.ComponentType;
    /** Render a listed part with Spotify's live renderer, preserving its native behavior. */
    readonly NativePart: React.ComponentType<{ id: string }>;
}
export interface UIRegistration {
    readonly id: string;
    dispose(): void;
}
export interface UITargetInfo {
    name: string;
    available: boolean;
    operations: UIOperation[];
    reason?: string;
    instances: number;
    conflicts: Array<{ instanceId: string; winner: string; suppressed: string[] }>;
}
export interface UIApi {
    replace(target: UITarget, component: React.ComponentType<UIComponentProps>): UIRegistration;
    insertBefore(target: UITarget, component: React.ComponentType<UIComponentProps>): UIRegistration;
    insertAfter(target: UITarget, component: React.ComponentType<UIComponentProps>): UIRegistration;
    overlay(target: UITarget, component: React.ComponentType<UIComponentProps>): UIRegistration;
    inspect(target: UITarget): Promise<UITargetInfo>;
    listTargets(): Promise<UITargetInfo[]>;
    listInstances(target: UITarget): Promise<UIInstance[]>;
    /** Run the live native click handler. It may navigate, dismiss, or open another menu. */
    invokeAction(instanceId: string, partId: string): Promise<void>;
}
