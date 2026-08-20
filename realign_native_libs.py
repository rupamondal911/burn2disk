import struct, sys, os, shutil

PAGE = 16384

def realign(data):
    if data[:4] != b'\x7fELF' or data[4] != 2:
        return data  # not ELF64, skip
    e_phoff = struct.unpack_from('<Q', data, 32)[0]
    e_phentsize = struct.unpack_from('<H', data, 54)[0]
    e_phnum = struct.unpack_from('<H', data, 56)[0]
    phdrs = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = \
            struct.unpack_from('<IIQQQQQQ', data[off:off + e_phentsize], 0)
        phdrs.append(dict(type=p_type, flags=p_flags, offset=p_offset, vaddr=p_vaddr,
                          paddr=p_paddr, filesz=p_filesz, memsz=p_memsz, align=p_align, index=i))
    loads = sorted([p for p in phdrs if p['type'] == 1], key=lambda l: l['offset'])
    # Check if already aligned
    all_ok = True
    for seg in loads:
        if seg['offset'] % PAGE != seg['vaddr'] % PAGE or seg['align'] < PAGE:
            all_ok = False
            break
    if all_ok:
        return data
    new_offsets = {}
    cursor = 0
    for seg in loads:
        vmod = seg['vaddr'] % PAGE
        cmod = cursor % PAGE
        candidate = cursor - cmod + vmod if cmod <= vmod else cursor - cmod + vmod + PAGE
        new_offsets[seg['index']] = candidate
        cursor = candidate + seg['filesz']
    new_phoff = (cursor + PAGE - 1) & ~(PAGE - 1)
    total = new_phoff + e_phentsize * e_phnum
    out = bytearray(total)
    out[:64] = data[:64]
    for seg in loads:
        noff = new_offsets[seg['index']]
        out[noff:noff + seg['filesz']] = data[seg['offset']:seg['offset'] + seg['filesz']]
    for p in phdrs:
        if p['type'] == 1 or p['filesz'] == 0:
            continue
        out[p['offset']:p['offset'] + p['filesz']] = data[p['offset']:p['offset'] + p['filesz']]
    for p in phdrs:
        off = new_phoff + p['index'] * e_phentsize
        raw = bytearray(data[e_phoff + p['index'] * e_phentsize:e_phoff + (p['index']+1) * e_phentsize])
        if p['index'] in new_offsets:
            struct.pack_into('<Q', raw, 8, new_offsets[p['index']])
            struct.pack_into('<Q', raw, 48, max(struct.unpack_from('<Q', raw, 48)[0], PAGE))
        out[off:off + e_phentsize] = raw
    struct.pack_into('<Q', out, 32, new_phoff)
    return bytes(out)

if __name__ == '__main__':
    for libdir in sys.argv[1:]:
        for root, dirs, files in os.walk(libdir):
            for f in files:
                if not f.endswith('.so'):
                    continue
                path = os.path.join(root, f)
                with open(path, 'rb') as fh:
                    orig = fh.read()
                result = realign(orig)
                if len(result) != len(orig):
                    with open(path, 'wb') as fh:
                        fh.write(result)
                    print(f'Realigned: {path}')
                else:
                    print(f'OK (no change): {path}')
