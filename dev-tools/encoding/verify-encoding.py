"""Acceptance test for the encoding repair.

Checks exact UTF-8 byte sequences (so no display/console encoding can influence
the result) and asserts the project-wide invariants:
  * no CJK in Kotlin sources
  * no CJK in res/xml
  * no U+FEFF anywhere
"""
from pathlib import Path

PROJ = Path(__file__).resolve().parents[2]
SKIP_DIRS = {"build", ".gradle", ".git", "recovered"}

CASES = [
    # (relative path, substring, must_be_present)
    ("README.md", "徽章收藏", True),
    ("README.md", "寰界珷鏀惰棌", False),
    ("README.md", "com.lanteng.star.app", True),
    ("app/build.gradle.kts", 'see README "signing".', True),
    ("app/build.gradle.kts", "绛惧悕", False),
    ("app/src/main/res/values-zh/strings.xml", "Star徽章助手", True),
    ("app/src/main/res/values-zh/arrays.xml", "季节", True),
    ("app/src/main/java/com/lanteng/star/app/data/Store.kt", "季节", False),
    ("app/src/main/java/com/lanteng/star/app/data/Store.kt", "R.array.default_categories", True),
    ("app/src/main/java/com/lanteng/star/app/ui/BadgeAdapter.kt", r"\u00B7", True),
    ("app/src/main/res/xml/preferences.xml", "Account & language", True),
    ("NOTICE", "useSkyBadge", True),
]

failures = 0
print("byte-level checks")
for rel, needle, expected in CASES:
    data = (PROJ / rel).read_bytes()
    got = needle.encode("utf-8") in data
    ok = got == expected
    failures += 0 if ok else 1
    print(f"  {'PASS' if ok else 'FAIL'}  present={got!s:<5} expected={expected!s:<5}  {rel}")

print("\nproject invariants")
cjk_in_sources, feff, cjk_in_res_xml = [], [], []
for path in sorted(PROJ.rglob("*")):
    if not path.is_file():
        continue
    rel = path.relative_to(PROJ)
    if any(p in SKIP_DIRS for p in rel.parts):
        continue
    if path.suffix not in {".kt", ".kts", ".xml", ".md", ".pro", ".yml", ".properties"} \
            and path.name not in {"NOTICE", "LICENSE", ".gitignore"}:
        continue
    text = path.read_text(encoding="utf-8")
    # values-zh/* is where Chinese belongs; values/arrays.xml holds the
    # language display name by design.
    allowed = rel.parts[:1] == ("app",) and "values-zh" in rel.parts
    if not allowed and "values" in rel.parts and rel.name == "arrays.xml":
        allowed = True

    if path.suffix in {".kt", ".kts"}:
        hits = [c for c in text if 0x4E00 <= ord(c) <= 0x9FFF]
        if hits:
            cjk_in_sources.append(str(rel).replace("\\", "/"))
    if "xml" in path.suffix and "values-zh" not in rel.parts and not allowed:
        hits = [c for c in text if 0x4E00 <= ord(c) <= 0x9FFF]
        if hits:
            cjk_in_res_xml.append(str(rel).replace("\\", "/"))
    if "\ufeff" in text:
        feff.append(str(rel).replace("\\", "/"))


def verdict(name, items):
    global failures
    if items:
        failures += len(items)
        print(f"  FAIL  {name}: {', '.join(items)}")
    else:
        print(f"  PASS  {name}")


verdict("no CJK in Kotlin sources", cjk_in_sources)
verdict("no CJK in res/xml", cjk_in_res_xml)
verdict("no U+FEFF anywhere", feff)

print(f"\n{'ALL CHECKS PASSED' if failures == 0 else str(failures) + ' CHECK(S) FAILED'}")
raise SystemExit(1 if failures else 0)
