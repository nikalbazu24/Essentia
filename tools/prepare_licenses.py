"""Ship notices from the exact native source and resolved archive JARs."""
import zipfile
from pathlib import Path
out=Path('app/src/main/assets/licenses');out.mkdir(parents=True,exist_ok=True)
native=Path('work/archive-android/_deps/libarchive-src/COPYING')
(out/'libarchive.txt').write_bytes(native.read_bytes())
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
for group,artifact,version in [('org.apache.commons','commons-compress','1.28.0'),('org.tukaani','xz','1.12')]:
    jars=list((cache/group/artifact/version).glob('*/*.jar'))
    if not jars:raise SystemExit('Missing dependency notice source: '+artifact)
    chunks=[]
    with zipfile.ZipFile(jars[0]) as jar:
        for name in jar.namelist():
            if name.upper().startswith('META-INF/') and any(word in name.upper() for word in ['LICENSE','NOTICE','COPYING']):
                chunks.append(name+'\n'+jar.read(name).decode('utf8'))
    if not chunks:raise SystemExit('Missing license notices: '+artifact)
    (out/(artifact+'.txt')).write_text('\n\n'.join(chunks),encoding='utf8')
print('Native and Java archive license notices prepared')
