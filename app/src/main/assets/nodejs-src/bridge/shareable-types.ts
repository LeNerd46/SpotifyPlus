const objectToString = Object.prototype.toString;

export function hasObjectTag(value: unknown, tag: string): boolean {
    return value !== null
        && typeof value === 'object'
        && objectToString.call(value) === `[object ${tag}]`;
}

export function isPlainObject(value: unknown): value is Record<string, unknown> {
    if (!value || typeof value !== 'object') {
        return false;
    }
    const prototype = Object.getPrototypeOf(value);
    if (prototype === null) {
        return true;
    }
    const constructor = Object.prototype.hasOwnProperty.call(prototype, 'constructor')
        ? prototype.constructor
        : undefined;
    return typeof constructor === 'function'
        && constructor.name === 'Object'
        && Object.getPrototypeOf(prototype) === null;
}
