"""Rebuild this project's strings using the upstream app's own wording.

For every UI element that exists in both apps we reuse the upstream author's
exact text (it is their own secondary-development copy, covered by GPL-3.0).
Strings for features this build does not implement are left as-is, and strings
for the new delivery/cooldown UI have no upstream equivalent.

Values are pulled from upstream-strings.json (produced by dump-upstream-strings.py),
so nothing is retyped by hand and format specifiers stay byte-exact.

This file is the single source of truth for the string table: running it twice
produces the same result.
"""
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

PROJ = Path(__file__).resolve().parents[2]
RES = PROJ / "app" / "src" / "main" / "res"
UPSTREAM_JSON = PROJ / "recovered" / "resources" / "upstream-strings.json"

UPSTREAM = json.loads(UPSTREAM_JSON.read_text(encoding="utf-8"))

# my key -> upstream key. Keys not listed here keep this project's own text.
MAPPING = {
    # app identity
    "app_name": "app_name",

    # main list
    "btn_manage": "btn_manage",
    "menu_settings": "settings",

    # settings categories
    "pref_cat_account_language": "pref_cat_account_language",
    "pref_cat_appearance": "pref_cat_appearance",
    "pref_cat_floating": "pref_cat_floating",
    "pref_cat_background": "pref_cat_background",
    "pref_cat_data": "pref_cat_data",

    # account & language
    "language_selection_summary": "language_selection_summary",
    "dialog_blur_title": "dialog_blur_title",
    "dialog_blur_summary": "dialog_blur_summary",

    # floating panel (titles and behaviour-neutral summaries only)
    "auto_hide_floating_panel_title": "auto_hide_floating_panel_title",
    "auto_hide_floating_panel_summary": "auto_hide_floating_panel_summary",
    "click_outside_to_hide_summary": "click_outside_to_hide_summary",
    "icon_only_floating_panel_title": "icon_only_floating_panel_title",
    "volume_keys_toggle_floating_title": "volume_keys_toggle_floating_title",
    "shake_toggle_floating_title": "shake_toggle_floating_title",
    "custom_floating_icon_title": "custom_floating_icon_title",

    # background
    "foreground_service_title": "foreground_service_title",
    "boot_startup_title": "boot_startup_title",
    "hide_recent_tasks_title": "hide_recent_tasks_title",
    "lock_recent_tasks_title": "lock_recent_tasks_title",
    "lock_recent_tasks_summary": "lock_recent_tasks_instructions",
    "battery_whitelist_title": "battery_whitelist_title",

    # data
    "badge_usage_analysis_summary": "badge_usage_analysis_summary",

    # accounts
    "accounts_title": "am_title",
    "accounts_add": "am_btn_add",
    "account_delete_blocked": "am_has_data",
    "action_save": "am_btn_save",
    "action_cancel": "am_btn_cancel",
    "action_delete": "am_btn_delete",
    "action_edit": "am_btn_edit",

    # notes
    "badge_notes": "hint_note",

    # nfc
    "nfc_unavailable": "nfc_not_supported",
    "nfc_disabled": "nfc_disabled_title",

    # qr
    "qr_scan_hint": "qr_scan_tip",
    "qr_bad_payload": "data_parse_failed",

    # usage
    "usage_by_account": "xml_account_filter",

    # toasts
    "toast_saved": "save_success",
    "toast_imported": "import_success",
    "toast_import_failed": "data_parse_failed",
    "floating_service_channel": "foreground_service_channel_name",
}

# Deliberately NOT mapped, because the upstream text describes behaviour this
# build does not have, and shipping it would make the UI lie about the settings:
#
#   icon_only_floating_panel_summary    upstream: icons-but-no-labels layout
#                                       here:     panel stays collapsed
#   volume_keys_toggle_floating_summary upstream: vol- / vol+ hide and restore
#                                       here:     both keys together toggle
#   shake_toggle_floating_summary       upstream: show-all / hide-all
#                                       here:     expand / collapse
#   custom_floating_icon_summary        upstream: 10s long-press on the panel
#                                       here:     pick an image in settings
#   number_invalid                      upstream: a password length rule
#   toast_deleted                       upstream: "account deleted"
#   toast_exported                      upstream: template needing a file path
#   toast_overlay_denied                upstream: a dialog title, not a message
#
# Their own wording is stated here instead.
OVERRIDES = {
    "icon_only_floating_panel_summary":
        ("Keep just the small icon visible.", "只保留小图标。"),
    "volume_keys_toggle_floating_summary":
        ("Show or hide the panel by pressing both volume keys.",
         "同时按两个音量键显示或隐藏面板。"),
    "shake_toggle_floating_summary":
        ("Shake the phone to show or hide the panel.", "摇动手机显示或隐藏面板。"),
    "custom_floating_icon_summary":
        ("Pick an image for the floating button.", "为悬浮按钮选择一张图片。"),
    "number_invalid":
        ("Enter a number between 1 and 600", "请输入 1 到 600 之间的数字"),
    "toast_deleted": ("Deleted", "已删除"),
    "toast_exported": ("Backup written", "备份已写入"),
    "toast_overlay_denied":
        ('Grant "display over other apps" to use the floating panel.',
         "请授予「显示在其他应用上层」权限以使用悬浮窗。"),
}

# Upstream strings that advertise the premium tier. This build has no paywall,
# so the trailing membership annotation is removed rather than shipped.
STRIP_PREMIUM = re.compile(r"\s*[【\[]\s*(会员功能|Premium feature)[^】\]]*[】\]]\s*$", re.S)


def load(path: Path) -> dict:
    root = ET.parse(path).getroot()
    return {el.get("name"): (el.text or "") for el in root if el.get("name")}


def esc(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', "&quot;")
    )


def build(locale_src: dict, current: dict, index: int) -> tuple[dict, list]:
    result = dict(current)
    mapped, missing = [], []
    for my_key, upstream_key in MAPPING.items():
        if my_key not in current:
            continue
        value = locale_src.get(upstream_key)
        if not value:
            missing.append(f"{my_key} <- {upstream_key}")
            continue
        result[my_key] = STRIP_PREMIUM.sub("", value).strip()
        mapped.append(my_key)

    for my_key, own in OVERRIDES.items():
        if my_key in current:
            result[my_key] = own[index]

    # Flag format-specifier changes so callers can be checked by hand.
    for key in mapped:
        before = set(re.findall(r"%\d+\$[sd]", current[key]))
        after = set(re.findall(r"%\d+\$[sd]", result[key]))
        if before != after:
            missing.append(f"FORMAT {key}: {sorted(before)} -> {sorted(after)}")
    return result, missing


def write_xml(path: Path, values: dict) -> None:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key in values:
        lines.append(f'    <string name="{key}">{esc(values[key])}</string>')
    lines.append("</resources>")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    upstream_zh = UPSTREAM.get("zh", {})
    upstream_default = UPSTREAM.get("default", {})

    cur_default = load(RES / "values" / "strings.xml")
    # The Chinese qualifier was renamed from zh-rCN to zh on the first run;
    # accept either so re-running this script is idempotent.
    old_zh_path = RES / "values-zh-rCN" / "strings.xml"
    cur_zh_path = old_zh_path if old_zh_path.exists() else RES / "values-zh" / "strings.xml"
    cur_zh = load(cur_zh_path)

    new_default, missing_d = build(upstream_default, cur_default, 0)
    new_zh, missing_z = build(upstream_zh, cur_zh, 1)

    write_xml(RES / "values" / "strings.xml", new_default)

    # The upstream app's own Chinese lives in `zh`, not `zh-rCN`, so the
    # qualifier matches upstream (and then also covers zh-rTW / zh-rHK via
    # locale fallback).
    out_dir = RES / "values-zh"
    out_dir.mkdir(parents=True, exist_ok=True)
    write_xml(out_dir / "strings.xml", new_zh)
    if old_zh_path.exists():
        old_zh_path.unlink()
        (RES / "values-zh-rCN").rmdir()

    print(f"default: {len(new_default)} strings, {len(MAPPING)} mapped from upstream")
    print(f"zh     : {len(new_zh)} strings, {len(MAPPING)} mapped from upstream")
    print(f"own wording kept for {len(OVERRIDES)} keys")
    for label, missing in (("default", missing_d), ("zh", missing_z)):
        if missing:
            print(f"\n[{label}] needs attention ({len(missing)}):")
            for item in missing:
                print(f"  {item}")


if __name__ == "__main__":
    main()
