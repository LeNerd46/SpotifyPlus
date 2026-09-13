"""Small, read-only DEX index for auditing Spotify hook targets without a device.

Reads original DEX metadata and instruction operands, not JADX's rewritten Java.
The cache belongs in build/ (never commit Spotify bytecode or extracted strings).
"""
import argparse
import pickle
import struct
from pathlib import Path


class Dex:
    def __init__(self, path):
        self.b = path.read_bytes()
        assert self.b[:4] == b'dex\n', path
        self.strings = []
        n, off = self.u32(56), self.u32(60)
        for i in range(n):
            _, p = self.uleb(self.u32(off + i * 4))
            end = self.b.index(0, p)
            self.strings.append(self.b[p:end].replace(b'\xc0\x80', b'\0').decode('utf-8', 'replace'))
        self.types = [self.strings[self.u32(self.u32(68) + i * 4)] for i in range(self.u32(64))]
        self.protos = []
        for i in range(self.u32(72)):
            p = self.u32(76) + i * 12
            self.protos.append((self.type_list(self.u32(p + 8)), self.types[self.u32(p + 4)]))
        self.fields = []
        for i in range(self.u32(80)):
            c, t, s = struct.unpack_from('<HHI', self.b, self.u32(84) + i * 8)
            self.fields.append((self.types[c], self.strings[s], self.types[t]))
        self.methods = []
        for i in range(self.u32(88)):
            c, p, s = struct.unpack_from('<HHI', self.b, self.u32(92) + i * 8)
            args, ret = self.protos[p]
            self.methods.append((self.types[c], self.strings[s], args, ret))

    def u32(self, off):
        return struct.unpack_from('<I', self.b, off)[0]

    def uleb(self, p):
        v = shift = 0
        while True:
            b = self.b[p]
            p += 1
            v |= (b & 127) << shift
            if b < 128:
                return v, p
            shift += 7

    def type_list(self, off):
        if not off:
            return ()
        return tuple(self.types[struct.unpack_from('<H', self.b, off + 4 + i * 2)[0]] for i in range(self.u32(off)))

    def code(self, off):
        strings, numbers, calls, fields = set(), set(), [], set()
        if not off:
            return strings, numbers, calls, fields
        p, end = off + 16, off + 16 + self.u32(off + 12) * 2
        while p < end:
            op, high = self.b[p:p + 2]
            width = WIDTHS[op]
            if op == 0 and high:
                size = struct.unpack_from('<H', self.b, p + 2)[0]
                if high == 1:
                    width = 4 + size * 2
                elif high == 2:
                    width = 2 + size * 4
                elif high == 3:
                    width = 4 + (size * self.u32(p + 4) + 1) // 2
                else:
                    raise ValueError('Unknown payload')
            elif op in (0x1a, 0x1b):
                idx = self.u32(p + 2) if op == 0x1b else struct.unpack_from('<H', self.b, p + 2)[0]
                strings.add(self.strings[idx])
            elif 0x6e <= op <= 0x72 or 0x74 <= op <= 0x78:
                calls.append(self.methods[struct.unpack_from('<H', self.b, p + 2)[0]])
            elif 0x52 <= op <= 0x6d:
                fields.add(self.fields[struct.unpack_from('<H', self.b, p + 2)[0]])
            elif op == 0x12:
                n = high >> 4
                numbers.add(n if n < 8 else n - 16)
            elif op in (0x13, 0x16):
                numbers.add(struct.unpack_from('<h', self.b, p + 2)[0])
            elif op in (0x14, 0x17):
                numbers.add(struct.unpack_from('<i', self.b, p + 2)[0])
            elif op == 0x18:
                numbers.add(struct.unpack_from('<q', self.b, p + 2)[0])
            elif op in (0x15, 0x19):
                numbers.add(struct.unpack_from('<h', self.b, p + 2)[0] << (16 if op == 0x15 else 48))
            assert width, (hex(op), p)
            p += width * 2
        assert p == end, (p, end)
        return strings, numbers, calls, fields

    def classes(self):
        for i in range(self.u32(96)):
            c, flags, parent, interfaces, source, annotations, data, values = struct.unpack_from('<8I', self.b, self.u32(100) + i * 32)
            result = dict(name=self.types[c], flags=flags, parent=self.types[parent] if parent != 0xffffffff else None,
                          interfaces=self.type_list(interfaces), fields=[], methods=[])
            if data:
                counts = []
                for _ in range(4):
                    n, data = self.uleb(data)
                    counts.append(n)
                for count in counts[:2]:
                    idx = 0
                    for _ in range(count):
                        delta, data = self.uleb(data)
                        access, data = self.uleb(data)
                        idx += delta
                        result['fields'].append((*self.fields[idx], access))
                for count in counts[2:]:
                    idx = 0
                    for _ in range(count):
                        delta, data = self.uleb(data)
                        access, data = self.uleb(data)
                        code, data = self.uleb(data)
                        idx += delta
                        strings, numbers, calls, fields = self.code(code)
                        owner, name, args, ret = self.methods[idx]
                        result['methods'].append(dict(owner=owner, name=name, args=args, ret=ret, flags=access,
                                                      strings=strings, numbers=numbers, calls=calls, fields=fields))
            yield result


# DEX instruction formats encode their length in 16-bit code units.
WIDTHS = [1] * 256
for op in [0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29,
           *range(0x2d, 0x3e), *range(0x44, 0x6e), *range(0x90, 0xb0), *range(0xd0, 0xe3), 0xfe, 0xff]:
    WIDTHS[op] = 2
for op in [0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c,
           *range(0x6e, 0x73), *range(0x74, 0x79), 0xfc, 0xfd]:
    WIDTHS[op] = 3
WIDTHS[0x18] = 5
WIDTHS[0xfa] = WIDTHS[0xfb] = 4


def load(root, cache):
    import json
    cache = Path(cache)
    paths = sorted(Path(root).glob('classes*.dex'))
    if not paths:
        raise FileNotFoundError(f'No classes*.dex found in {root}')
    manifest = {'schema': 1, 'files': [[str(p.resolve()), p.stat().st_size, p.stat().st_mtime_ns] for p in paths]}
    manifest_path = cache.with_suffix('.json')
    if cache.exists() and manifest_path.exists() and json.loads(manifest_path.read_text()) == manifest:
        with cache.open('rb') as f:
            return pickle.load(f)
    result = {}
    for path in paths:
        print('Indexing', path.name, flush=True)
        dex = Dex(path)
        for cls in dex.classes():
            result[cls['name']] = cls
    cache.parent.mkdir(parents=True, exist_ok=True)
    with cache.open('wb') as f:
        pickle.dump(result, f, protocol=5)
    manifest_path.write_text(json.dumps(manifest), encoding='utf-8')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root')
    parser.add_argument('cache')
    args = parser.parse_args()
    print(len(load(args.root, args.cache)), 'classes')
