"""Use the APK metadata as the only authority for updater versions."""
import json, os, pathlib, sys
metadata = json.loads(pathlib.Path(sys.argv[1]).read_text())
elements = metadata["elements"]
if len(elements) != 1:
    raise SystemExit("Expected one universal APK")
entry = elements[0]
manifest = dict(versionCode=entry["versionCode"], versionName=entry["versionName"],
                signed=os.environ.get("ESSENTIA_SIGNED") == "true", apk="essentia.apk",
                commit=os.environ["GITHUB_SHA"])
pathlib.Path(sys.argv[2]).write_text(json.dumps(manifest, indent=2) + "\n")
