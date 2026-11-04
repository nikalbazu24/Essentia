"""Fail publication if the Android beta loses an engine or its offline assets."""
import json,sys,zipfile
from pathlib import Path
apk=Path(sys.argv[1]);manifest=json.loads(Path(sys.argv[2]).read_text())
with zipfile.ZipFile(apk) as archive:
    names=set(archive.namelist())
    required={'AndroidManifest.xml','classes.dex','lib/arm64-v8a/libmkxp-z.so',
              'lib/arm64-v8a/libmkxp-z-legacy.so','lib/arm64-v8a/libessentia-archive.so',
              'assets/essentia_catalog.json','assets/essentia_preload/essentia_games.rb',
              'assets/essentia_preload/essentia_live.rb','assets/licenses/libarchive.txt',
              'assets/licenses/commons-compress.txt','assets/licenses/xz.txt'}
    missing=required-names
    if missing:raise SystemExit('APK incomplete: '+', '.join(sorted(missing)))
    if any(name.startswith('lib/') and not name.startswith('lib/arm64-v8a/') for name in names):
        raise SystemExit('Unexpected ABI in Android release')
    if archive.testzip() is not None:raise SystemExit('APK ZIP integrity check failed')
if not manifest['versionName'].startswith('2.0-beta.') or manifest['versionCode']<=152:
    raise SystemExit('Updater must advance to 2.0 beta')
print('APK OK: modern + classic engines, RAR reader, offline catalog, Android arm64, 2.0 updater version')
