// Explicit b4 binding; legacy tool filenames/classes remain, never the old runtime targets.
export const ROOT = 'D:/ERP_WMS/BC warehouse/2G';
export const RUN = ROOT + '/target/platform-browser-invitation-20260920/b4';
export const PREPARATION = ROOT + '/target/platform-b4-preparation-20260920/a1';
export const DELIVERY = ROOT + '/docs/releases/platform-browser-invitation-20260920/b4';
export const SOURCE = PREPARATION + '/snapshot';
export const BUILD = PREPARATION + '/classes';
export const PRIVATE = RUN + '/private';
export const DATA = RUN + '/pgdata';
export const DATABASE = 'p3_platform_browser_b4';
export const ADMIN = 'p3_b4_initializer', MIG = 'p3_b4_migrator', APP = 'p3_b4_app';
export const PG = 'D:/ITsoftware/PostgreSQL/bin';
export const NODE = 'D:/JAVA/NODE/node.exe';
export const JAVA = 'C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe';
export const BACKEND_RUN = ROOT + '/target/platform-db-acceptance-20260919/r11';
export const BACKEND_CLASSES = BACKEND_RUN + '/build/a1/classes';
export const FRONTEND = ROOT + '/target/platform-frontend-acceptance-20260919/f3/snapshot/frontend';
export const PORTS = Object.freeze({ postgres: 55459, backend: 18092, frontend: 15174 });
export const AUTH = '--authorized-b4-invitation-only-20260920';
export const BUDGETS = Object.freeze({ compileMs:300000, nodeSuiteMs:120000, javaGuardsMs:60000, syntaxMs:15000,
 initializationMs:180000, migrationMs:180000, sqlMs:30000, sqlStatementMs:15000,
 launcherMs:37000, launcherCommandMs:35000, readyMs:40000, cleanupMs:60000, stopCommandMs:32000,
 fullQueryMs:15000, pidQueryMs:5000, readySqlMs:4000,
 backendReadyMs:90000, frontendReadyMs:10000, backendStopMs:30000, frontendStopMs:10000 });
