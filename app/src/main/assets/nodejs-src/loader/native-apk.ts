import { inflateRawSync } from 'zlib';

/** Reject placeholders and damaged downloads before replacing an installed extension. */
export function validateNativeApk(bytes: Buffer, fileName: string): void {
    const invalid = (reason: string): never => {
        throw new Error(`Invalid native APK ${fileName}: ${reason}. Rebuild the extension's native release APK.`);
    };
    if (bytes.length < 22 || bytes.readUInt32LE(0) !== 0x04034b50) {
        invalid('the file is not an APK ZIP archive');
    }
    let end = -1;
    for (let offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 65557); offset--) {
        if (bytes.readUInt32LE(offset) === 0x06054b50
            && offset + 22 + bytes.readUInt16LE(offset + 20) === bytes.length) {
            end = offset;
            break;
        }
    }
    if (end < 0) invalid('the ZIP directory is missing or truncated');
    const entries = bytes.readUInt16LE(end + 10);
    let cursor = bytes.readUInt32LE(end + 16);
    const directoryEnd = cursor + bytes.readUInt32LE(end + 12);
    if (directoryEnd !== end || bytes.readUInt16LE(end + 4) !== 0
        || bytes.readUInt16LE(end + 6) !== 0) invalid('unsupported ZIP directory');
    let foundDex = false;
    for (let index = 0; index < entries; index++) {
        if (cursor + 46 > directoryEnd || bytes.readUInt32LE(cursor) !== 0x02014b50) {
            invalid('damaged ZIP directory');
        }
        const nameLength = bytes.readUInt16LE(cursor + 28);
        const next = cursor + 46 + nameLength + bytes.readUInt16LE(cursor + 30) + bytes.readUInt16LE(cursor + 32);
        if (next > directoryEnd) invalid('truncated ZIP entry');
        const name = bytes.toString('utf8', cursor + 46, cursor + 46 + nameLength);
        if (name === 'classes.dex') {
            const local = bytes.readUInt32LE(cursor + 42);
            if (local + 30 > cursor || bytes.readUInt32LE(local) !== 0x04034b50) invalid('damaged DEX entry');
            const start = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28);
            const size = bytes.readUInt32LE(cursor + 20);
            if (start + size > cursor) invalid('truncated DEX data');
            const method = bytes.readUInt16LE(cursor + 10);
            let dex: Buffer;
            try {
                const compressed = bytes.subarray(start, start + size);
                dex = method === 0 ? compressed : method === 8
                    ? inflateRawSync(compressed, { maxOutputLength: 64 * 1024 * 1024 })
                    : invalid('unsupported DEX compression');
            } catch {
                invalid('could not decompress DEX data');
            }
            if (dex!.length < 112 || !/^dex\n\d{3}\0$/.test(dex!.toString('ascii', 0, 8))
                || dex!.length !== bytes.readUInt32LE(cursor + 24)) invalid('invalid DEX data');
            foundDex = true;
        }
        cursor = next;
    }
    if (cursor !== directoryEnd) invalid('damaged ZIP directory size');
    if (!foundDex) invalid('classes.dex is missing');
}
