"""Validate translation coverage and argument contracts without an Android runtime."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
ARGUMENT = re.compile(r"%(\d+)\$([sd])")


def catalog(folder):
    result = {}
    for path in sorted(folder.glob("*.xml")):
        for entry in ET.parse(path).getroot():
            if entry.tag not in {"string", "plurals"}:
                continue
            name = entry.attrib["name"]
            assert name not in result, f"Duplicate resource: {name}"
            result[name] = entry
    return result


base = catalog(ROOT / "values")
english = catalog(ROOT / "values-en")
required = {name for name, entry in base.items() if entry.get("translatable") != "false"}
assert required == set(english), f"Missing: {required - set(english)}; extra: {set(english) - required}"
for name in required:
    source, target = base[name], english[name]
    assert source.tag == target.tag, name
    if source.tag == "string":
        assert sorted(ARGUMENT.findall(source.text or "")) == sorted(ARGUMENT.findall(target.text or "")), name
    else:
        fallback = source.find("item[@quantity='other']")
        assert fallback is not None, name
        assert target.find("item[@quantity='one']") is not None, name
        assert target.find("item[@quantity='other']") is not None, name
        for item in target:
            assert sorted(ARGUMENT.findall(fallback.text or "")) == sorted(ARGUMENT.findall(item.text or "")), name
assert {node.attrib["{http://schemas.android.com/apk/res/android}name"] for node in
        ET.parse(ROOT / "xml/locales_config.xml").getroot()} == {"tr", "en"}
print(f"Turkish / English: {len(required)} matching resources; placeholders and plurals valid.")
