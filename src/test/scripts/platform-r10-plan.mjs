// Frozen r10-only budgets. No automatic extension or target fallback.
export const BUDGETS=Object.freeze({
 compileMs:300000,javaGuardsMs:300000,nodeSuiteMs:120000,syntaxMs:15000,versionMs:15000,
 migrationMs:180000,sqlMs:30000,sqlStatementMs:15000,
 mavenEffectiveMs:240000,mavenSuiteMs:240000,surefireForkSeconds:120,surefireExitSeconds:20,
 initializationMs:60000,launcherMs:37000,launcherCommandMs:35000,readyMs:40000,
 cleanupMs:60000,stopCommandMs:32000,fullQueryMs:15000,pidQueryMs:5000,readySqlMs:4000
});
export const JAVA_GATES=Object.freeze([
 ['com.wms.platformverification.PlatformR2IsolationTest',44],
 ['com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest',7],
 ['com.wms.platformverification.PlatformFixtureScopeTest',29],
 ['com.wms.system.platform.service.PlatformMfaFailureFixtureTest',22],
 ['com.wms.system.platform.service.PlatformCompanyDataFixtureTest',18],
 ['com.wms.system.platform.service.PlatformExportTestConfigurationTest',17],
 ['com.wms.platformverification.PlatformFixtureWorkersTest',8],
 ['com.wms.system.tenant.security.SpringSecurityFixtureTest',18]
]);
export const NODE_GATES=Object.freeze([
 ['platform-process-lifecycle.test.mjs',35],['platform-launch-preflight.test.mjs',40],
 ['platform-s1-start-stop.test.mjs',33],['platform-process-ancestry.test.mjs',33],
 ['platform-process-query.test.mjs',16],['platform-process-timing.test.mjs',8],
 ['platform-fixture-isolation.test.mjs',24],['platform-upgrade-sentinels.test.mjs',65],
 ['platform-test-results.test.mjs',67],['platform-r6-stages.test.mjs',57],
 ['platform-report-runner.test.mjs',60],['platform-r7-binding.test.mjs',8],
 ['platform-s2-start-stop.test.mjs',35],['platform-r8-binding.test.mjs',12],['platform-r9-binding.test.mjs',16],
 ['platform-r8-environment.test.mjs',12],['platform-r9-environment.test.mjs',12],
 ['platform-export-fixture.test.mjs',12],['platform-r9-fixture.test.mjs',10],['platform-case-catalog.test.mjs',28],['platform-r10-binding.test.mjs',18],['platform-r10-environment.test.mjs',12]
]);
export const AUTH='--authorized-20260918-r10-arnold-elevated-platform-backend-only';
