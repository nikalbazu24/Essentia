"""Decode upstream BSD test fixtures from the exact libarchive revision we build."""
import binascii
from pathlib import Path
source=Path('work/archive-host/_deps/libarchive-src/libarchive/test')
target=Path('app/src/test/resources/archives');target.mkdir(parents=True,exist_ok=True)
for name in ['test_read_format_rar5_stored.rar','test_read_format_rar5_compressed.rar']:
    lines=(source/(name+'.uu')).read_bytes().splitlines()
    start=next(i for i,s in enumerate(lines) if s.startswith(b'begin '))+1
    data=b''.join(binascii.a2b_uu(line) for line in lines[start:] if line!=b'end' and line)
    (target/name).write_bytes(data)
    print(name,len(data))
