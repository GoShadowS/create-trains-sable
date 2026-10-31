"""Prints a Markdown summary of the JUnit reports Gradle leaves in build/test-results/test.

CI posts the result into the commit comment, so the number of tests that ran, and the name of
any that failed, is visible without opening the runner log.
"""

import glob
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    total = failed = skipped = 0
    lines = []
    failures = []
    for path in sorted(glob.glob("build/test-results/test/TEST-*.xml")):
        root = ET.parse(path).getroot()
        name = (root.get("name") or path).split(".")[-1]
        tests = int(root.get("tests", 0))
        bad = int(root.get("failures", 0)) + int(root.get("errors", 0))
        skip = int(root.get("skipped", 0))
        total, failed, skipped = total + tests, failed + bad, skipped + skip
        lines.append("- %s: %d run, %d failed" % (name, tests, bad))
        for case in root.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                message = (problem.get("message") or problem.get("type") or "").splitlines()
                failures.append("  - `%s.%s`: %s" % (name, case.get("name"),
                                                     message[0] if message else "failed"))

    if total == 0:
        print("- no test reports found")
    else:
        print("- tests: **%d run, %d failed, %d skipped**" % (total, failed, skipped))
        for line in lines:
            print("  " + line)
        for line in failures:
            print(line)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
