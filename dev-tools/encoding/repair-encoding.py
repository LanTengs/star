"""Detect and repair double-encoded text files.

Cause: a file was read as cp936 and rewritten as UTF-8, so every original UTF-8
byte pair became one CJK character. The transform is reversible:

    mojibake.encode('cp936').decode('utf-8') == original

Run with --apply to repair. ASCII-only reporting so the console cannot mislead.
"""
import sys
from pathlib import Path

PROJ = Path(__file__).resolve().parents[2]
SKIP_DIRS = {"build", ".gradle", ".git", "recovered"}
SUFFIXES = {".kt", ".kts", ".xml", ".md", ".pro", ".yml", ".properties"}
NAMES = {"NOTICE", "LICENSE", ".gitignore"}


def reversible(text: str):
    """Return the restored text, or None if this is not a clean double-encoding."""
    if "\ufffd" in text:
        return None
    try:
        restored = text.encode("cp936").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return None
    return restored if restored != text else None


def main() -> None:
    apply = "--apply" in sys.argv
    repaired, already_ok, manual = [], [], []

    for path in sorted(PROJ.rglob("*")):
        if not path.is_file():
            continue
        if any(p in SKIP_DIRS for p in path.relative_to(PROJ).parts):
            continue
        if path.suffix not in SUFFIXES and path.name not in NAMES:
            continue

        text = path.read_text(encoding="utf-8")
        if not any(ord(c) > 127 for c in text):
            continue

        restored = reversible(text)
        rel = str(path.relative_to(PROJ)).replace("\\", "/")

        if restored is None:
            # Either clean, or damaged in a way this transform cannot undo.
            if any("\u4e00" <= c <= "\u9fff" for c in text) and path.suffix in {".kt", ".kts"}:
                manual.append(rel)
            else:
                already_ok.append(rel)
            continue

        if apply:
            path.write_text(restored, encoding="utf-8")
        repaired.append((rel, len(text), len(restored)))

    verb = "repaired" if apply else "would repair"
    print(f"{verb}: {len(repaired)}")
    for rel, before, after in repaired:
        print(f"   {before:>6} -> {after:<6} chars  {rel}")
    print(f"\nclean or non-CJK: {len(already_ok)}")
    if manual:
        print(f"\nneeds a human look ({len(manual)}):")
        for rel in manual:
            print(f"   {rel}")


if __name__ == "__main__":
    main()
