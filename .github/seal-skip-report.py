#!/usr/bin/env python3
"""List every skipped test with its reason, plus a totals line.

Gradle's JUnit XML records skipped tests without a message, so the reason is
recovered from the test source: the @Disabled/@Ignore annotation or the
assume*() call in the method, its class, or its superclass.

Usage: seal-skip-report.py <dir with TEST-*.xml> [...]
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

REASON_RE = re.compile(r"@Disabled|@Ignore|@EnabledIf|@DisabledIf|@EnabledOn|@DisabledOn|assume|Assume")
IDENT_RE = re.compile(r"[A-Za-z_$][\w$]*")


def java_index(base):
    index = {}
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", "node_modules", ".gradle")]
        for name in filenames:
            if name.endswith(".java"):
                index.setdefault(name, []).append(os.path.join(dirpath, name))
    return index


def find_source(index, classname):
    outer = classname.split("$")[0]
    rel = outer.replace(".", "/") + ".java"
    for path in index.get(rel.rsplit("/", 1)[-1], []):
        if path.replace(os.sep, "/").endswith(rel):
            return path
    return None


def read_lines(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            return fh.read().splitlines()
    except OSError:
        return []


def method_reasons(lines, method):
    found = []
    decl = re.compile(r"\b(void|public|protected|private)\b.*\b" + re.escape(method) + r"\s*\(")
    for i, line in enumerate(lines):
        if not decl.search(line):
            continue
        for j in range(max(0, i - 6), i):
            if REASON_RE.search(lines[j]):
                found.append(lines[j].strip())
        depth = 0
        started = False
        for k in range(i, min(len(lines), i + 200)):
            text = lines[k]
            if k > i and REASON_RE.search(text):
                found.append(text.strip())
            depth += text.count("{") - text.count("}")
            if "{" in text:
                started = True
            if started and depth <= 0:
                break
        break
    return found


def class_reasons(lines):
    return [l.strip() for l in lines if REASON_RE.search(l) and not l.strip().startswith("import")]


def superclass(lines):
    for line in lines:
        m = re.search(r"\bclass\s+\w+(?:<[^>]*>)?\s+extends\s+([\w.]+)", line)
        if m:
            return m.group(1)
    return None


def reason_for(index, classname, testname, xml_message):
    parts = []
    if xml_message:
        parts.append(xml_message.strip())
    path = find_source(index, classname)
    if path:
        lines = read_lines(path)
        m = IDENT_RE.match(testname or "")
        method = m.group(0) if m else ""
        src = method_reasons(lines, method) if method else []
        if not src:
            src = class_reasons(lines)
        if not src:
            sup = superclass(lines)
            if sup:
                pkg = classname.rsplit(".", 1)[0]
                sup_path = find_source(index, sup if "." in sup else pkg + "." + sup)
                if sup_path:
                    src = ["(superclass) " + s for s in class_reasons(read_lines(sup_path))]
        parts.extend(src)
    reason = "; ".join(dict.fromkeys(p for p in parts if p))
    if not reason:
        reason = "no assumption text found in source (environment-conditional skip)"
    return reason[:400]


def main():
    roots = sys.argv[1:] or ["."]
    index = java_index(".")
    leg = os.environ.get("SEAL_LEG")
    if leg:
        print("SEAL_LEG: " + leg)
    total = failures = errors = skipped = 0
    lines = []
    for root in roots:
        for dirpath, _, filenames in os.walk(root):
            for name in sorted(filenames):
                if not (name.startswith("TEST-") and name.endswith(".xml")):
                    continue
                try:
                    tree = ET.parse(os.path.join(dirpath, name))
                except ET.ParseError:
                    continue
                for case in tree.getroot().iter("testcase"):
                    total += 1
                    if case.find("failure") is not None:
                        failures += 1
                    if case.find("error") is not None:
                        errors += 1
                    skip = case.find("skipped")
                    if skip is None:
                        continue
                    skipped += 1
                    cls = case.get("classname", "?")
                    test = case.get("name", "?")
                    msg = skip.get("message") or (skip.text or "")
                    lines.append("SKIPPED %s > %s -- reason: %s" % (cls, test, reason_for(index, cls, test, msg)))
    print("==== Skipped test report (%d skipped) ====" % skipped)
    for line in lines:
        print(line)
    print("==== Test totals from JUnit XML: %d tests, %d passed, %d failures, %d errors, %d skipped ===="
          % (total, total - failures - errors - skipped, failures, errors, skipped))


if __name__ == "__main__":
    main()
