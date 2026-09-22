// Narrow adapter for the existing Vitest JSON reporter and pre-execution collection.
// No filesystem/process activity, no string replacement or splitting of test titles.
const text = value => typeof value === 'string' && value.length > 0;
const fileKey = value => value.replaceAll('\\', '/').toLowerCase();
const duplicate = values => [...new Set(values.filter((v, i) => values.indexOf(v) !== i))];
const tuple = (file, ancestors, title) => JSON.stringify([fileKey(file), ancestors, title]);
const collectionKey = (file, ancestors, title) => JSON.stringify([fileKey(file), [...ancestors, title].join(' > ')]);

export function assessVitestReport(report, expected, format = 'structured') {
  const issues = [];
  const issue = code => { if (!issues.includes(code)) issues.push(code); };
  const actual = [], wanted = [];
  if (!['structured', 'collection'].includes(format)) issue('INVALID_EXPECTATION_FORMAT');
  if (!Array.isArray(expected) || !expected.length) issue('EMPTY_OR_INVALID_EXPECTED_LIST');
  for (const e of Array.isArray(expected) ? expected : []) {
    if (!e || !text(e.file) || (format === 'collection' ? !text(e.name)
      : !Array.isArray(e.ancestorTitles) || !e.ancestorTitles.every(text) || !text(e.title))) {
      issue('INVALID_EXPECTED_CASE'); continue;
    }
    wanted.push(format === 'collection' ? JSON.stringify([fileKey(e.file), e.name]) : tuple(e.file, e.ancestorTitles, e.title));
  }
  const modules = report?.testResults;
  if (!Array.isArray(modules)) issue('INVALID_REPORT_SCHEMA');
  const moduleFiles = [];
  for (const s of Array.isArray(modules) ? modules : []) {
    if (!s || !text(s.name) || !Array.isArray(s.assertionResults)) { issue('INVALID_MODULE_SCHEMA'); continue; }
    moduleFiles.push(fileKey(s.name));
    if (s.status !== 'passed') issue('MODULE_NOT_PASSED');
    for (const t of s.assertionResults) {
      if (!t || !Array.isArray(t.ancestorTitles) || !t.ancestorTitles.every(text) || !text(t.title) || !text(t.fullName)) {
        issue('INVALID_CASE_SCHEMA'); continue;
      }
      if (t.fullName !== [...t.ancestorTitles, t.title].join(' ')) issue('INCONSISTENT_JSON_FULL_NAME');
      if (!['passed', 'failed', 'skipped', 'pending', 'todo', 'disabled'].includes(t.status)) issue('INVALID_CASE_STATUS');
      if (t.status === 'passed' && Array.isArray(t.failureMessages) && t.failureMessages.length) issue('PASSED_CASE_HAS_FAILURE_MESSAGES');
      actual.push({ file: fileKey(s.name), ancestorTitles: t.ancestorTitles, title: t.title, status: t.status,
        structuredKey: tuple(s.name, t.ancestorTitles, t.title),
        comparisonKey: format === 'collection' ? collectionKey(s.name, t.ancestorTitles, t.title) : tuple(s.name, t.ancestorTitles, t.title) });
    }
  }
  const actualKeys = actual.map(t => t.comparisonKey);
  const missing = wanted.filter(k => !actualKeys.includes(k));
  const extra = actualKeys.filter(k => !wanted.includes(k));
  const duplicateExpected = duplicate(wanted), duplicateActual = duplicate(actual.map(t => t.structuredKey));
  const ambiguousCollectionKeys = format === 'collection' ? duplicate(actualKeys) : [];
  const expectedFiles = [...new Set((Array.isArray(expected) ? expected : []).filter(e => text(e?.file)).map(e => fileKey(e.file)))];
  if (duplicate(moduleFiles).length) issue('DUPLICATE_MODULE');
  if (expectedFiles.some(f => !moduleFiles.includes(f)) || moduleFiles.some(f => !expectedFiles.includes(f))) issue('FILE_INVENTORY_MISMATCH');
  if (missing.length) issue('MISSING_CASE');
  if (extra.length) issue('EXTRA_CASE');
  if (duplicateExpected.length) issue('DUPLICATE_EXPECTED_CASE');
  if (duplicateActual.length) issue('DUPLICATE_ACTUAL_CASE');
  if (ambiguousCollectionKeys.length) issue('AMBIGUOUS_COLLECTION_IDENTITY');
  const passed = actual.filter(t => t.status === 'passed').length;
  const failed = actual.filter(t => t.status === 'failed').length;
  const todo = actual.filter(t => t.status === 'todo').length;
  const pending = actual.filter(t => ['skipped', 'pending', 'disabled'].includes(t.status)).length;
  const unknownStatus = actual.length - passed - failed - todo - pending;
  if (failed) issue('FAILED_CASE');
  if (todo || pending) issue('SKIPPED_OR_PENDING_CASE');
  if (unknownStatus) issue('INVALID_CASE_STATUS');
  for (const [key, value] of Object.entries({ numTotalTests: actual.length, numPassedTests: passed,
    numFailedTests: failed, numPendingTests: pending, numTodoTests: todo })) {
    if (!Number.isInteger(report?.[key]) || report[key] !== value) issue('HEADER_COUNT_MISMATCH');
  }
  // Vitest suite headers include nested describe suites, not only test files.
  const suiteCounts = ['numTotalTestSuites', 'numPassedTestSuites', 'numFailedTestSuites', 'numPendingTestSuites'];
  if (suiteCounts.some(k => !Number.isInteger(report?.[k]) || report[k] < 0)
    || report.numTotalTestSuites !== report.numPassedTestSuites + report.numFailedTestSuites + report.numPendingTestSuites
    || report.numTotalTestSuites < moduleFiles.length) issue('SUITE_HEADER_MISMATCH');
  if (report?.numFailedTestSuites !== 0 || report?.numPendingTestSuites !== 0) issue('SUITE_NOT_PASSED');
  if (report?.success !== true) issue('REPORT_NOT_SUCCESSFUL');
  return { status: issues.length ? 'FAILED' : 'PASSED', format, total: actual.length, expected: wanted.length,
    passed, failed, skipped: pending + todo, unknownStatus, files: moduleFiles.length,
    missing, extra, duplicateExpected, duplicateActual, ambiguousCollectionKeys, issues };
}
