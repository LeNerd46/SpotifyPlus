import React from 'react';
import { randomUUID } from 'crypto';
import { createRoot, RenderRoot, clearCommitListener } from './renderer';
import { BUILTIN_TARGETS } from './target-catalog';
import type { UIApi, UIComponentProps, UIOperation, UIRegistration, UISelector, UITarget, UITargetContext, UITargetInfo } from './target-api';

export interface NativeUITarget {
    id: string;
    target: string;
    context: UITargetContext;
    operations: UIOperation[];
    layout?: 'fill' | 'intrinsic';
    selectors?: UISelector[];
    nativeContent?: boolean;
}
interface Registration {
    id: string;
    owner: string;
    generation: number;
    target: UITarget;
    operation: UIOperation;
    component: React.ComponentType<UIComponentProps>;
    sequence: number;
}
interface MountedTarget {
    descriptor: NativeUITarget;
    revision: number;
    pending: boolean;
    failed?: boolean;
    surfaceId?: string;
    root?: RenderRoot;
    nativePart?: UIComponentProps['NativePart'];
}
export interface UITargetTransport {
    request<T>(operation: string, data: object): Promise<T>;
    registerSurface(id: string): void;
    unregisterSurface(id: string): void;
    report(owner: string, error: unknown): void;
}

const FillLayout = React.createContext(false);
const Original = () => React.createElement('NativeOriginal', { width: '100%', ...(React.useContext(FillLayout) ? { height: '100%' } : {}) });
const NoOriginal = () => { throw new Error('Original is only valid inside UI.replace'); };

class ContributionBoundary extends React.Component<{
    children?: React.ReactNode; original: boolean; failed: (error: unknown) => void;
}, { failed: boolean }> {
    state = { failed: false };
    static getDerivedStateFromError() { return { failed: true }; }
    componentDidCatch(error: unknown) { this.props.failed(error); }
    render() { return this.state.failed ? (this.props.original ? React.createElement(Original) : null) : this.props.children; }
}

/** One React root per native target instance, with keyed, extension-owned contributions. */
export class UITargetRegistry {
    private readonly registrations = new Map<string, Registration>();
    private readonly targets = new Map<string, MountedTarget>();
    private order: string[] = [];
    private sequence = 0;
    private readonly generations = new Map<string, number>();
    constructor(private readonly transport: UITargetTransport) { }

    forExtension(owner: string, generation: number): UIApi {
        this.generations.set(owner, generation);
        const register = (operation: UIOperation, target: UITarget, component: React.ComponentType<UIComponentProps>): UIRegistration => {
            if (this.generations.get(owner) !== generation) throw new Error('This extension generation has unloaded');
            validateTarget(target);
            if (typeof component !== 'function' && (typeof component !== 'object' || component === null)) throw new TypeError('Expected a React component');
            const id = `${owner}:${generation}:${randomUUID()}`;
            this.registrations.set(id, { id, owner, generation, target: structuredClone(target), operation, component, sequence: this.sequence++ });
            void this.transport.request<UITargetInfo>('ui.inspect', { target }).then(info => {
                if (!this.registrations.has(id)) return;
                if (!info.available || !info.operations.includes(operation)) this.transport.report(owner,
                    new Error(`UI ${operation} unavailable for ${JSON.stringify(target)}: ${info.reason ?? 'operation is not supported by this boundary'}`));
            }).catch(error => { if (this.registrations.has(id)) this.transport.report(owner, error); });
            this.refresh();
            return Object.freeze({ id, dispose: () => { if (this.registrations.delete(id)) this.refresh(); } });
        };
        return {
            replace: (target, component) => register('replace', target, component),
            insertBefore: (target, component) => register('before', target, component),
            insertAfter: (target, component) => register('after', target, component),
            overlay: (target, component) => register('overlay', target, component),
            inspect: async target => {
                validateTarget(target);
                const info = await this.transport.request<UITargetInfo>('ui.inspect', { target });
                return this.withConflicts(info);
            },
            listTargets: async () => {
                const available = new Map((await this.transport.request<UITargetInfo[]>('ui.list', {})).map(info => [info.name, info]));
                for (const name of BUILTIN_TARGETS) if (!available.has(name)) available.set(name, {
                    name, available: false, operations: [], instances: 0, conflicts: [],
                    reason: 'No verified adapter for this region in the installed Spotify build',
                });
                return [...available.values()].map(info => this.withConflicts(info));
            },
            listInstances: async target => {
                validateTarget(target);
                const snapshot = await this.transport.request<NativeUITarget[]>('ui.snapshot', {});
                return snapshot.filter(entry => matchesTarget(target, entry))
                    .map(entry => ({ target: entry.target, context: structuredClone(entry.context) }));
            },
            invokeAction: async (instanceId, partId) => {
                if (this.generations.get(owner) !== generation) throw new Error('This extension generation has unloaded');
                if (typeof instanceId !== 'string' || !instanceId || typeof partId !== 'string' || !partId)
                    throw new TypeError('Expected a live instanceId and part ID');
                await this.transport.request('ui.invokeAction', { instanceId, partId });
            },
        };
    }

    setOrder(order: string[]) {
        if (!Array.isArray(order) || order.some(id => typeof id !== 'string') || new Set(order).size !== order.length) throw new TypeError('UI order must contain unique extension IDs');
        this.order = [...order];
        this.refresh();
    }

    getOrder(): string[] { return [...this.order]; }
    owners(): string[] {
        const owners = [...new Set([...this.registrations.values()].map(entry => entry.owner))];
        return [...this.order.filter(id => owners.includes(id)), ...owners.filter(id => !this.order.includes(id)).sort()];
    }

    open(descriptor: NativeUITarget) {
        const mounted = this.targets.get(descriptor.id);
        if (mounted) mounted.descriptor = descriptor;
        else {
            const mounted: MountedTarget = { descriptor, revision: 0, pending: false };
            mounted.nativePart = ({ id }) => {
                if (!mounted.descriptor.context.parts?.some(part => part.id === id)) throw new Error('Native part has expired or belongs to another target');
                return React.createElement('NativePart', { key: id, partId: id, width: '100%' });
            };
            this.targets.set(descriptor.id, mounted);
        }
        this.reconcile(this.targets.get(descriptor.id)!);
    }

    close(id: string) {
        const mounted = this.targets.get(id);
        if (!mounted) return;
        this.targets.delete(id);
        this.release(mounted);
    }

    fail(id: string, surfaceId: string, error: unknown) {
        const mounted = this.targets.get(id);
        if (!mounted || mounted.surfaceId !== surfaceId) return;
        mounted.failed = true;
        for (const entry of this.matching(mounted.descriptor)) this.transport.report(entry.owner, error);
        this.release(mounted);
    }

    unregister(owner: string) {
        this.generations.delete(owner);
        for (const [id, registration] of this.registrations) if (registration.owner === owner) this.registrations.delete(id);
        this.refresh();
    }

    private refresh() { for (const mounted of this.targets.values()) this.reconcile(mounted); }
    private matching(target: NativeUITarget) {
        return [...this.registrations.values()].filter(r => matchesTarget(r.target, target) && target.operations.includes(r.operation)).sort((a, b) => {
            const rank = (owner: string) => { const i = this.order.indexOf(owner); return i < 0 ? this.order.length : i; };
            return rank(a.owner) - rank(b.owner) || a.owner.localeCompare(b.owner) || a.sequence - b.sequence;
        });
    }
    private withConflicts(info: UITargetInfo): UITargetInfo {
        const instances = [...this.targets.values()].filter(t => t.descriptor.target === info.name);
        return { ...info, instances: instances.length, conflicts: instances.flatMap(t => {
            const replacements = this.matching(t.descriptor).filter(r => r.operation === 'replace');
            return replacements.length > 1 ? [{ instanceId: t.descriptor.id, winner: replacements[0].owner, suppressed: replacements.slice(1).map(r => r.owner) }] : [];
        }) };
    }

    private reconcile(mounted: MountedTarget) {
        if (mounted.failed) return;
        const entries = this.matching(mounted.descriptor);
        if (!entries.length) { this.release(mounted); return; }
        if (mounted.root) { this.render(mounted, entries); return; }
        if (mounted.pending) return;
        mounted.pending = true;
        const revision = ++mounted.revision;
        void this.transport.request<{ surfaceId: string }>('ui.attach', { instanceId: mounted.descriptor.id }).then(async result => {
            if (revision !== mounted.revision || this.targets.get(mounted.descriptor.id) !== mounted) {
                await this.transport.request('ui.detach', { instanceId: mounted.descriptor.id, surfaceId: result.surfaceId });
                mounted.pending = false;
                if (this.targets.get(mounted.descriptor.id) === mounted) this.reconcile(mounted);
                return;
            }
            mounted.pending = false;
            mounted.surfaceId = result.surfaceId;
            this.transport.registerSurface(result.surfaceId);
            mounted.root = createRoot(result.surfaceId);
            this.render(mounted, this.matching(mounted.descriptor));
        }).catch(error => {
            mounted.pending = false;
            if (revision !== mounted.revision) {
                if (this.targets.get(mounted.descriptor.id) === mounted) this.reconcile(mounted);
                return;
            }
            for (const entry of entries) this.transport.report(entry.owner, error);
            this.release(mounted);
        });
    }

    private render(mounted: MountedTarget, entries: Registration[]) {
        const replacement = entries.find(r => r.operation === 'replace');
        const contribution = (entry: Registration) => React.createElement(ContributionBoundary, {
            key: entry.id, original: entry.operation === 'replace',
            failed: (error: unknown) => this.transport.report(entry.owner, error),
        }, React.createElement(entry.component, { context: structuredClone(mounted.descriptor.context), Original: entry.operation === 'replace' ? Original : NoOriginal,
            NativePart: mounted.nativePart! }));
        mounted.root!.render(React.createElement(FillLayout.Provider, { value: mounted.descriptor.layout === 'fill' }, React.createElement('View', {
            width: '100%', ...(mounted.descriptor.layout === 'fill' ? { height: '100%' } : {}),
        },
            ...entries.filter(r => r.operation === 'before').map(contribution),
            replacement ? contribution(replacement) : mounted.descriptor.nativeContent === false ? null : React.createElement(Original, { key: 'original' }),
            ...entries.filter(r => r.operation === 'after').map(contribution),
            ...entries.filter(r => r.operation === 'overlay').map(entry => React.createElement('View', {
                key: entry.id, position: 'absolute', left: 0, top: 0, right: 0, bottom: 0,
            }, contribution(entry))),
        )));
    }

    private release(mounted: MountedTarget) {
        ++mounted.revision;
        const surfaceId = mounted.surfaceId;
        const root = mounted.root;
        mounted.root = undefined;
        mounted.surfaceId = undefined;
        if (!surfaceId) return;
        mounted.pending = true;
        try { root?.unmount(); }
        finally {
            clearCommitListener(surfaceId);
            this.transport.unregisterSurface(surfaceId);
            void this.transport.request('ui.detach', { instanceId: mounted.descriptor.id, surfaceId })
                .catch(error => this.transport.report('UI', error))
                .finally(() => {
                    mounted.pending = false;
                    if (this.targets.get(mounted.descriptor.id) === mounted) this.reconcile(mounted);
                });
        }
    }
}

function matchesTarget(selector: UITarget, target: NativeUITarget): boolean {
    if (typeof selector === 'string') return selector === target.target;
    return target.selectors?.some(alias => alias.screen === selector.screen
        && alias.resourceId === selector.resourceId && alias.composeTag === selector.composeTag) ?? false;
}

function validateTarget(target: UITarget) {
    if (typeof target === 'string' && target.length > 0) return;
    if (target && typeof target === 'object' && typeof target.screen === 'string' && target.screen.length > 0) {
        const resource = 'resourceId' in target;
        const tag = 'composeTag' in target;
        if (resource !== tag && typeof (resource ? target.resourceId : target.composeTag) === 'string' && (resource ? target.resourceId : target.composeTag)!.length > 0) return;
    }
    throw new TypeError('Expected a named UI target or a screen-scoped resourceId/composeTag selector');
}
