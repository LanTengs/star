"""Dump the upstream app's own strings to UTF-8 files for reuse.

Writes:
  upstream-strings.json  all locales, name -> value
  upstream-strings.txt   readable side-by-side (app-specific names only)
"""
import json
from pathlib import Path

PROJ = Path(__file__).resolve().parents[2]
import re
import xml.etree.ElementTree as ET
from pathlib import Path

PROJ = Path(__file__).resolve().parents[2]

RES = PROJ / "recovered" / "resources"
OUT = RES  # keep derived upstream data inside the gitignored recovered/ tree

# Library / framework resources: not the app author's own UI text.
NOISE = re.compile(
    r"^(abc_|mtrl_|exo_|androidx_|material_|preference_|zxing_|v7_|m3_|character_counter|"
    r"exposed_dropdown|searchview_|side_sheet|item_view_role|password_toggle|status_bar|"
    r"tooltip_|bottomsheet|nav_|range_|fab_|chip_|tab_|textinput_|expand_button|"
    r"image_button|summary_collapsed|seekbar|error_|character_limit|mtrl)"
)


def load(path: Path) -> dict:
    if not path.exists():
        return {}
    out = {}
    try:
        root = ET.parse(path).getroot()
    except Exception as exc:  # noqa: BLE001
        print(f"!! parse failed {path.name}: {exc}")
        return {}
    for el in root:
        name = el.get("name")
        if not name:
            continue
        if el.tag == "plurals":
            out[name] = " | ".join(f"{i.get('quantity')}={i.text or ''}" for i in el)
        else:
            out[name] = el.text or ""
    return out


def main() -> None:
    locales = {}
    for path in sorted(RES.glob("strings-*.xml")):
        locales[path.stem.replace("strings-", "")] = load(path)

    (OUT / "upstream-strings.json").write_text(
        json.dumps(locales, ensure_ascii=False, indent=1), encoding="utf-8"
    )

    zh = locales.get("zh", {})
    default = locales.get("default", {})
    names = sorted(set(zh) | set(default))
    lines = []
    for name in names:
        if NOISE.match(name):
            continue
        z = zh.get(name)
        d = default.get(name)
        if z is None and d is None:
            continue
        lines.append(f"### {name}")
        if z is not None:
            lines.append(f"zh  | {z}")
        if d is not None:
            lines.append(f"en  | {d}")
    (OUT / "upstream-strings.txt").write_text("\n".join(lines), encoding="utf-8")

    app_specific = len(lines) // 1
    print(f"locales: {len(locales)}; zh={len(zh)} default={len(default)}")
    print(f"wrote upstream-strings.json ({sum(len(v) for v in locales.values())} values)")
    print(f"wrote upstream-strings.txt ({len(lines)} lines, app-specific names only)")


if __name__ == "__main__":
    main()
