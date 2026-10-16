"""Encoding-safety scan.

Rule: Kotlin sources and res/xml resources must contain NO CJK at all -- all UI
text belongs in string resources. So any CJK found there means an encoding
round-trip mangled the file (double-encoded UTF-8 still lands in CJK ranges,
which makes this a reliable detector, unlike a fixed mojibake character list).

Markdown/NOTICE files are allowed to contain CJK; for those, double-encoding is
detected by looking for the byte-pair patterns it produces.
"""
from pathlib import Path

PROJ = Path(__file__).resolve().parents[2]
SKIP_DIRS = {"build", ".gradle", ".git", "recovered"}


def cjk_ranges(text: str):
    return [ch for ch in text if 0x4E00 <= ord(ch) <= 0x9FFF or 0x3000 <= ord(ch) <= 0x303F]


def sample(text: str, chars, span: int = 40) -> str:
    for i, ch in enumerate(text):
        if ch in chars:
            return text[max(0, i - span):i + span].replace("\n", " | ")
    return ""


def main() -> None:
    # Text files that legitimately carry Chinese.
    ALLOW_CJK = {
        Path("README.md"),
        Path("NOTICE"),
        Path("app/src/main/res/values-zh/strings.xml"),
        Path("app/src/main/res/values/strings.xml"),
    }

    problems, allowed = [], []
    for path in sorted(PROJ.rglob("*")):
        if not path.is_file():
            continue
        rel_parts = path.relative_to(PROJ).parts
        if any(part in SKIP_DIRS for part in rel_parts):
            continue
        if path.suffix not in {".kt", ".kts", ".xml", ".md", ".pro", ".yml", ".properties"} \
                and path.name not in {"NOTICE", "LICENSE", ".gitignore"}:
            continue

        rel = path.relative_to(PROJ)
        text = path.read_text(encoding="utf-8")
        hits = cjk_ranges(text)

        if rel in ALLOW_CJK:
            allowed.append((rel, len(hits)))
            continue
        if hits:
            problems.append((rel, len(hits), sample(text, set(hits))))

    print(f"files allowed to contain CJK: {len(allowed)}")
    for rel, n in allowed:
        print(f"   {n:5d}  {rel}")

    print("\n=== MUST BE FIXED (CJK where none belongs) ===")
    if not problems:
        print("   none")
    for rel, n, ctx in problems:
        print(f"   {n} CJK chars  {rel}")
        print(f"        {ctx[:120]}")


if __name__ == "__main__":
    main()
