"""Считает статистику по последней полной переиндексации директории."""

import json
from collections import Counter
from pathlib import Path


def collect_extension_stats(root: Path) -> Counter:
    counts = Counter()
    for file in root.rglob("*"):
        if file.is_file():
            counts[file.suffix.lstrip(".").lower() or "без расширения"] += 1
    return counts


def main() -> None:
    root = Path("/data")
    stats = collect_extension_stats(root)
    print(json.dumps(stats, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
