export type PacketType = 'event' | 'command' | 'request' | 'response' | 'error';

const PACKET_TYPES = new Set<PacketType>([
    'event',
    'command',
    'request',
    'response',
    'error',
]);

export interface PacketBase<TType extends PacketType = PacketType, TPayload = unknown> {
    id?: string;
    type: TType;
    name?: string;
    payload: TPayload;
}

export type EventPacket<TPayload = unknown> = PacketBase<'event', TPayload>;
export type CommandPacket<TPayload = unknown> = PacketBase<'command', TPayload>;
export type RequestPacket<TPayload = unknown> = PacketBase<'request', TPayload>;
export type ResponsePacket<TPayload = unknown> = PacketBase<'response', TPayload>;
export type ErrorPacket<TPayload = unknown> = PacketBase<'error', TPayload>;

export type Packet<TPayload = unknown> = | EventPacket<TPayload> | CommandPacket<TPayload> | RequestPacket<TPayload> | ResponsePacket<TPayload> | ErrorPacket<TPayload>;

export interface ErrorPayload {
    message: string;
    stack?: string;
    code?: string;
}

export function isPacket(value: unknown): value is Packet {
    if (!value || typeof value !== 'object') return false;
    const packet = value as Record<string, unknown>;

    if (typeof packet.type !== 'string' || !PACKET_TYPES.has(packet.type as PacketType)) {
        return false;
    }

    if (!('payload' in packet)) {
        return false;
    }

    if ('id' in packet && packet.id !== undefined && typeof packet.id !== 'string') {
        return false;
    }

    if (packet.type === 'event' || packet.type === 'command' || packet.type === 'request') {
        return typeof packet.name === 'string';
    }

    if ('name' in packet && packet.name !== undefined && typeof packet.name !== 'string') {
        return false;
    }

    return true;
}

export function parsePacket(json: string): Packet {
    const parsed = JSON.parse(json) as unknown;
    if (!isPacket(parsed)) throw new Error('Invalid packet');
    return parsed;
}

export function stringify(packet: Packet): string {
    const seen = new WeakSet();

    return JSON.stringify(packet, (_key, value) => {
        if (typeof value === 'object' && value !== null) {
            if (seen.has(value)) return '[Circular]';
            seen.add(value);
        }

        if (typeof value === 'function') return '[Function]';

        return value;
    });
}