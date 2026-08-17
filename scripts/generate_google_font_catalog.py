import concurrent.futures
import json
import re
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

TREE_URL = "https://api.github.com/repos/google/fonts/git/trees/main?recursive=1"
RAW_BASE = "https://raw.githubusercontent.com/google/fonts/main/"
USER_AGENT = "MirrorDash-Catalog-Generator"

NAME_RE = re.compile(r'^name: "(.*)"$')
CATEGORY_RE = re.compile(r'^category: "(.*)"$')
LICENSE_RE = re.compile(r'^license: "(.*)"$')
SUBSET_RE = re.compile(r'^subsets: "(.*)"$')
FILENAME_RE = re.compile(r'^  filename: "(.*)"$')


def fetch_text(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8")


def fetch_json(url: str):
    return json.loads(fetch_text(url))


def parse_metadata(path: str):
    raw = fetch_text(RAW_BASE + path)
    family = None
    category = "UNKNOWN"
    license_name = "UNKNOWN"
    subsets = []
    filenames = []
    for line in raw.splitlines():
        if family is None:
            match = NAME_RE.match(line)
            if match:
                family = match.group(1)
                continue
        match = CATEGORY_RE.match(line)
        if match:
            category = match.group(1)
            continue
        match = LICENSE_RE.match(line)
        if match:
            license_name = match.group(1)
            continue
        match = SUBSET_RE.match(line)
        if match:
            subsets.append(match.group(1))
            continue
        match = FILENAME_RE.match(line)
        if match:
            filenames.append(match.group(1))
    if family is None or not filenames:
        raise RuntimeError(f"Unable to parse {path}")
    return {
        "family": family,
        "category": category,
        "license": license_name,
        "subsets": sorted(set(subsets)),
        "filenames": filenames,
    }


def preferred_filename(filenames):
    def score(name: str):
        lower = name.lower()
        return (
            "italic" in lower,
            "[" not in lower,
            "regular" not in lower,
            lower,
        )

    return sorted(filenames, key=score)[0]


def main(output_path: str):
    tree = fetch_json(TREE_URL)
    family_dirs = defaultdict(set)
    metadata_paths = []
    for node in tree["tree"]:
        path = node["path"]
        if path.count("/") < 2:
            continue
        root, slug, filename = path.split("/", 2)
        if root not in {"ofl", "apache", "ufl"}:
            continue
        if filename == "METADATA.pb":
            metadata_paths.append(path)

    entries = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=24) as executor:
        futures = {executor.submit(parse_metadata, path): path for path in metadata_paths}
        for future in concurrent.futures.as_completed(futures):
            path = futures[future]
            metadata = future.result()
            preferred = preferred_filename(metadata["filenames"])
            _, slug, _ = path.split("/", 2)
            entries.append(
                {
                    "id": f"google:{slug}",
                    "family": metadata["family"],
                    "category": metadata["category"],
                    "license": metadata["license"],
                    "subsets": metadata["subsets"],
                    "styleCount": len(metadata["filenames"]),
                    "preferredPath": f"{path.rsplit('/', 1)[0]}/{preferred}",
                }
            )

    entries.sort(key=lambda item: item["family"].lower())
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(entries, ensure_ascii=True, separators=(",", ":")))
    print(f"Wrote {len(entries)} entries to {output}")


if __name__ == "__main__":
    destination = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/google_font_catalog.json"
    main(destination)
