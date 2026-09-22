import test from 'node:test';
import assert from 'node:assert/strict';
import { assessVitestReport } from './platform-vitest-report.mjs';

const file = 'D:/synthetic/a.test.ts';
function fixture(ancestorTitles = ['suite'], title = 'case') {
  return { expected: [{ file, name: [...ancestorTitles, title].join(' > ') }], report: {
    success: true, numTotalTests: 1, numPassedTests: 1, numFailedTests: 0, numPendingTests: 0, numTodoTests: 0,
    numTotalTestSuites: 2, numPassedTestSuites: 2, numFailedTestSuites: 0, numPendingTestSuites: 0,
    testResults: [{ name: file, status: 'passed', assertionResults: [{ ancestorTitles, title,
      fullName: [...ancestorTitles, title].join(' '), status: 'passed', failureMessages: [] }] }] } };
}
const row = f => f.report.testResults[0].assertionResults[0];
const assess = f => assessVitestReport(f.report, f.expected, 'collection');
const rejects = (f, code) => { const r = assess(f); assert.equal(r.status, 'FAILED'); assert.ok(r.issues.includes(code), JSON.stringify(r)); };

test('nested collection names match structured reporter fields', () => { assert.equal(assess(fixture()).status, 'PASSED'); });
test('top-level case has no synthetic ancestor', () => { assert.equal(assess(fixture([], 'top')).status, 'PASSED'); });
test('literal separators inside a title are preserved', () => { assert.equal(assess(fixture(['suite > literal'], 'title > literal')).status, 'PASSED'); });
test('spaces are not globally normalized', () => { const f = fixture(); f.expected[0].name = 'suite >  case'; rejects(f, 'MISSING_CASE'); });
test('same title in a different file is rejected', () => { const f = fixture(); f.expected[0].file = 'D:/synthetic/other/a.test.ts'; rejects(f, 'FILE_INVENTORY_MISMATCH'); });
test('missing case is rejected', () => { const f = fixture(); f.expected.push({ file, name: 'suite > absent' }); rejects(f, 'MISSING_CASE'); });
test('extra case is rejected', () => { const f = fixture(); f.expected[0].name = 'suite > other'; rejects(f, 'EXTRA_CASE'); });
test('duplicate actual case is rejected', () => { const f = fixture(); f.report.testResults[0].assertionResults.push(structuredClone(row(f))); rejects(f, 'DUPLICATE_ACTUAL_CASE'); });
test('duplicate expected case is rejected', () => { const f = fixture(); f.expected.push(structuredClone(f.expected[0])); rejects(f, 'DUPLICATE_EXPECTED_CASE'); });
test('ambiguous flattened collection identity is rejected', () => {
  const f = fixture(['a'], 'b > c');
  f.report.testResults[0].assertionResults.push({ ancestorTitles: ['a', 'b'], title: 'c', fullName: 'a b c', status: 'passed', failureMessages: [] });
  rejects(f, 'AMBIGUOUS_COLLECTION_IDENTITY');
});
test('failed case remains failed even with a success header', () => { const f = fixture(); row(f).status = 'failed'; rejects(f, 'FAILED_CASE'); });
test('skipped case is not counted as passed', () => { const f = fixture(); row(f).status = 'skipped'; rejects(f, 'SKIPPED_OR_PENDING_CASE'); });
test('pending and todo cases are rejected', () => { for (const status of ['pending', 'todo', 'disabled']) { const f = fixture(); row(f).status = status; rejects(f, 'SKIPPED_OR_PENDING_CASE'); } });
test('wrong test header count is rejected', () => { const f = fixture(); f.report.numTotalTests = 2; rejects(f, 'HEADER_COUNT_MISMATCH'); });
test('inconsistent JSON fullName is rejected', () => { const f = fixture(); row(f).fullName = 'suite > case'; rejects(f, 'INCONSISTENT_JSON_FULL_NAME'); });
test('missing structured fields are rejected', () => { const f = fixture(); delete row(f).ancestorTitles; rejects(f, 'INVALID_CASE_SCHEMA'); });
test('malformed report is rejected', () => { assert.equal(assessVitestReport({}, fixture().expected, 'collection').status, 'FAILED'); });
test('false global success is rejected', () => { const f = fixture(); f.report.success = false; rejects(f, 'REPORT_NOT_SUCCESSFUL'); });
test('module failure is not hidden by passed cases', () => { const f = fixture(); f.report.testResults[0].status = 'failed'; rejects(f, 'MODULE_NOT_PASSED'); });
test('duplicate file is rejected', () => { const f = fixture(); f.report.testResults.push(structuredClone(f.report.testResults[0])); rejects(f, 'DUPLICATE_MODULE'); });
test('empty extra module is rejected', () => { const f = fixture(); f.report.testResults.push({ name: 'D:/synthetic/extra.test.ts', status: 'passed', assertionResults: [] }); rejects(f, 'FILE_INVENTORY_MISMATCH'); });
test('structured address expectations do not depend on display separators', () => { const f = fixture(); const e = [{ file, ancestorTitles: ['suite'], title: 'case' }]; assert.equal(assessVitestReport(f.report, e).status, 'PASSED'); });
test('unknown status and nonempty failure messages are rejected', () => { const f = fixture(); row(f).status = 'unknown'; rejects(f, 'INVALID_CASE_STATUS'); const g = fixture(); row(g).failureMessages = ['synthetic']; rejects(g, 'PASSED_CASE_HAS_FAILURE_MESSAGES'); });
test('suite totals include describe but must still be consistent', () => { const f = fixture(); f.report.numPassedTestSuites = 1; rejects(f, 'SUITE_HEADER_MISMATCH'); });
test('invalid expected list cannot accept zero tests', () => { assert.equal(assessVitestReport({}, [], 'collection').status, 'FAILED'); });
