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
  exit 1
fi
exit 0
