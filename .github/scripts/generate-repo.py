import json
import hashlib
import re
import shutil
from pathlib import Path

REPO_DIR = Path("repo")
APK_DIR = REPO_DIR / "apk"
SRC_DIR = Path("src")

# Selected popular ID extensions
SELECTED = [
    "bacakomik", "doujindesu", "hentaicrot", "hwago", "kiryuu",
    "klikmanga", "komikav", "komikcast", "komikdewasa", "komikindoco",
    "komikindoid", "komikindo", "komiknesia", "komikstation", "komiktap",
    "komiku", "komikzoid", "kumapoi", "mangacan", "mangasusu",
    "mangatale", "mikoroku", "shinigami", "tooncubus", "westmanga",
]


def compute_source_id(name: str, lang: str, version_id: int = 1) -> int:
    key = f"{name.lower()}/{lang}/{version_id}"
    md5 = hashlib.md5(key.encode()).digest()
    val = 0
    for i in range(8):
        val = (val << 8) | md5[i]
    return val & 0x7FFFFFFFFFFFFFFF


def parse_build_gradle(ext_dir: Path) -> dict | None:
    bg = ext_dir / "build.gradle.kts"
    if not bg.exists():
        return None
    text = bg.read_text(encoding="utf-8")

    name_m = re.search(r'name\s*=\s*"([^"]+)"', text)
    vc_m = re.search(r"versionCode\s*=\s*(\d+)", text)
    cw_m = re.search(r"contentWarning\s*=\s*ContentWarning\.(\w+)", text)
    lang_m = re.search(r'lang\s*=\s*"([^"]+)"', text)
    base_m = re.search(r'baseUrl\s*[=(]\s*"([^"]+)"', text)
    id_m = re.search(r"id\s*=\s*(\d+)L", text)
    lib_m = re.search(r'libVersion\s*=\s*"([^"]+)"', text)

    if not name_m or not base_m:
        return None

    cw_map = {"SAFE": 1, "MIXED": 2, "NSFW": 3}
    lang = lang_m.group(1) if lang_m else "all"
    lib_version = lib_m.group(1) if lib_m else "1.4"
    version_code = int(vc_m.group(1)) if vc_m else 0

    return {
        "name": name_m.group(1),
        "lang": lang,
        "baseUrl": base_m.group(1),
        "versionCode": version_code,
        "contentWarning": cw_map.get(cw_m.group(1), 1) if cw_m else 1,
        "libVersion": lib_version,
        "explicitId": int(id_m.group(1)) if id_m else None,
    }


def find_apk(ext_dir: Path) -> Path | None:
    debug_dir = ext_dir / "build" / "outputs" / "apk" / "debug"
    if not debug_dir.exists():
        return None
    apks = list(debug_dir.glob("*.apk"))
    return apks[0] if apks else None


def main():
    REPO_DIR.mkdir(parents=True, exist_ok=True)
    APK_DIR.mkdir(parents=True, exist_ok=True)

    extensions = []

    for ext_name in SELECTED:
        src_dir = SRC_DIR / "id" / ext_name
        if not src_dir.exists():
            print(f"SKIP: {ext_name} (not found)")
            continue

        meta = parse_build_gradle(src_dir)
        if not meta:
            print(f"SKIP: {ext_name} (no metadata)")
            continue

        apk = find_apk(src_dir)
        if not apk:
            print(f"SKIP: {ext_name} (no APK found)")
            continue

        # Parse version from APK filename: tachiyomi-{suffix}-v{versionName}[-debug].apk
        apk_name = apk.name
        version_match = re.search(r"-v([\d.]+)", apk_name)
        version_name = version_match.group(1) if version_match else "1.4.0"
        version_code_match = re.search(r"\.(\d+)$", version_name)
        version_code = version_code_match.group(1) if version_code_match else "0"

        # Copy APK to repo
        dest_apk = APK_DIR / f"tachiyomi-id.{ext_name}-v{version_name}.apk"
        shutil.copy2(apk, dest_apk)

        source_id = meta["explicitId"] or compute_source_id(meta["name"], meta["lang"])

        extensions.append({
            "name": f"Tachiyomi: {meta['name']}",
            "pkg": f"eu.kanade.tachiyomi.extension.id.{ext_name}",
            "apk": dest_apk.name,
            "lang": meta["lang"],
            "code": int(version_code),
            "version": version_name,
            "nsfw": meta["contentWarning"],
            "sources": [{
                "name": meta["name"],
                "lang": meta["lang"],
                "id": str(source_id),
                "baseUrl": meta["baseUrl"],
            }],
        })

        print(f"OK: {meta['name']} ({version_name})")

    # Write index.min.json
    index_path = REPO_DIR / "index.min.json"
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(extensions, f, ensure_ascii=False, separators=(",", ":"))

    # Write repo.json
    repo_json = {
        "meta": {
            "name": "Albar19",
            "shortName": "Albar19",
            "website": "https://github.com/Albar19/Extensions-komik",
        }
    }
    with open(REPO_DIR / "repo.json", "w", encoding="utf-8") as f:
        json.dump(repo_json, f, indent=2)

    print(f"\nDone! {len(extensions)} extensions in index.min.json")


if __name__ == "__main__":
    main()
