#!/usr/bin/env bash
# Guards against silently-green 0-test runs: a GMD invocation whose
# instrumentation matches nothing (wrong annotation filter, renamed test
# classes, runner mismatch) exits 0 with 'Starting 0 tests'. Parse the JUnit
# XML, publish the executed count as a ::notice (public, API-readable), and
# FAIL the job when nothing ran. Usage: ci_assert_tests_ran.sh <min-tests>
set -u
MIN="${1:-1}"

count=$(python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
total = 0
for f in glob.glob('app/build/outputs/androidTest-results/**/*.xml', recursive=True):
    try:
        root = ET.parse(f).getroot()
    except Exception:
        continue
    total += len(list(root.iter('testcase')))
print(total)
PY
)

echo "::notice title=executed-test-count::${count} instrumentation tests ran in this job"
if [ "${count}" -lt "${MIN}" ]; then
  echo "::error title=zero-test-run::Only ${count} tests ran (expected >= ${MIN}) - the filter or runner matched nothing; treating as failure."
  # Ground truth: dump what the runner actually reported (suite attributes
  # plus any instrumentation output embedded in the XML) so the cause is
  # diagnosable from public annotations alone.
  python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
files = sorted(glob.glob('app/build/outputs/androidTest-results/**/*.xml', recursive=True))
if not files:
    print("::error title=no-result-xml::No JUnit XML found at all under androidTest-results")
for f in files[:4]:
    try:
        root = ET.parse(f).getroot()
    except Exception as e:
        print(f"::error title=xml-parse::{f}: {e}")
        continue
    bits = [f"file={f}", f"root=<{root.tag} {dict(root.attrib)}>"]
    for suite in ([root] if root.tag == 'testsuite' else root.iter('testsuite')):
        bits.append(f"suite={dict(suite.attrib)}")
    for tag in ('system-out', 'system-err'):
        for node in root.iter(tag):
            txt = (node.text or '').strip()
            if txt:
                bits.append(f"{tag}={txt[:700]}")
    text = '%0A'.join(b.replace('%', '%25').replace('\n', ' ') for b in bits)[:3800]
    print(f"::error title=result-xml-dump::{text}")
PY
  exit 1
fi
exit 0
