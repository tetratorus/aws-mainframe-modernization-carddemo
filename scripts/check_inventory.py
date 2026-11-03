#!/usr/bin/env python3
"""Check that docs/migration/inventory.md names every base-app program, BMS mapset,
VSAM cluster/AIX, CICS file and JCL member found in the source tree."""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "app"
DOC = (ROOT / "docs" / "migration" / "inventory.md").read_text()
HLQ = "AWS.M2.CARDDEMO."


def stems(folder):
    return sorted({p.stem.upper() for p in (APP / folder).iterdir() if p.is_file()})


def cluster_names():
    names = set()
    for jcl in (APP / "jcl").iterdir():
        text = jcl.read_text(errors="replace")
        for m in re.finditer(r"DEFINE\s+(?:CLUSTER|ALTERNATEINDEX)\s*\(\s*NAME\(([^)]+)\)", text):
            names.add(m.group(1).strip())
    return sorted(names)


def csd_files():
    text = (APP / "csd" / "CARDDEMO.CSD").read_text(errors="replace")
    return sorted(set(re.findall(r"DEFINE FILE\((\w+)\)", text)))


def main():
    checks = {
        "program (app/cbl)": stems("cbl"),
        "BMS mapset (app/bms)": stems("bms"),
        "JCL member (app/jcl)": stems("jcl"),
        "CICS file (app/csd)": csd_files(),
        "VSAM cluster/AIX (app/jcl DEFINE)": [n[len(HLQ):] if n.startswith(HLQ) else n for n in cluster_names()],
    }
    missing = []
    for kind, items in checks.items():
        for item in items:
            if not re.search(r"(?<![A-Z0-9.])" + re.escape(item) + r"(?![A-Z0-9])", DOC):
                missing.append(f"{kind}: {item}")
        print(f"{kind}: {len(items)} found in source")
    programs = len(checks["program (app/cbl)"])
    if programs != 31:
        missing.append(f"expected 31 programs in app/cbl, found {programs}")
    if missing:
        print("\nMissing from docs/migration/inventory.md:")
        print("\n".join("  " + m for m in missing))
        return 1
    print("\ninventory.md covers everything above")
    return 0


if __name__ == "__main__":
    sys.exit(main())
