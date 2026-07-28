import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const backendDirectory = path.resolve(scriptDirectory, '..');
const migrationsDirectory = path.join(
  backendDirectory,
  'src',
  'database',
  'migrations',
);
const hardeningFileName = '1753920000000-HardenSupabaseBackendOnlyAccess.ts';

const migrationFiles = (await readdir(migrationsDirectory))
  .filter((fileName) => fileName.endsWith('.ts'))
  .sort();

const createdTables = new Set(['familyroot_migrations']);
const createdTablesByFile = new Map();
for (const fileName of migrationFiles) {
  const source = await readFile(
    path.join(migrationsDirectory, fileName),
    'utf8',
  );
  const tables = [...source.matchAll(/CREATE\s+TABLE\s+"([^"]+)"/gi)].map(
    (match) => match[1],
  );
  createdTablesByFile.set(fileName, { source, tables });
  for (const table of tables) {
    createdTables.add(table);
  }
}

const hardeningSource = await readFile(
  path.join(migrationsDirectory, hardeningFileName),
  'utf8',
);
const listMatch = hardeningSource.match(
  /const backendOnlyTables = \[([\s\S]*?)\]\s+as const;/,
);
if (!listMatch) {
  throw new Error('Cannot find backendOnlyTables in the hardening migration');
}

const hardenedTables = new Set(
  [...listMatch[1].matchAll(/'([^']+)'/g)].map((match) => match[1]),
);
const hardeningFileIndex = migrationFiles.indexOf(hardeningFileName);
if (hardeningFileIndex < 0) {
  throw new Error(`Cannot find ${hardeningFileName}`);
}

const baselineTables = new Set(['familyroot_migrations']);
for (const fileName of migrationFiles.slice(0, hardeningFileIndex)) {
  for (const table of createdTablesByFile.get(fileName).tables) {
    baselineTables.add(table);
  }
}

const missingTables = [...baselineTables]
  .filter((table) => !hardenedTables.has(table))
  .sort();
const unknownTables = [...hardenedTables]
  .filter((table) => !createdTables.has(table))
  .sort();

const requiredFragments = [
  'ENABLE ROW LEVEL SECURITY',
  'REVOKE ALL PRIVILEGES ON TABLE',
  "to_regprocedure('public.rls_auto_enable()')",
  'REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM PUBLIC',
  'FROM anon',
  'FROM authenticated',
];
const missingFragments = requiredFragments.filter(
  (fragment) => !hardeningSource.includes(fragment),
);

const failures = [];
if (missingTables.length > 0) {
  failures.push(
    `Tables missing from backend-only hardening: ${missingTables.join(', ')}`,
  );
}
if (unknownTables.length > 0) {
  failures.push(
    `Hardening references tables not created by migrations: ${unknownTables.join(', ')}`,
  );
}
if (missingFragments.length > 0) {
  failures.push(
    `Hardening controls missing from migration: ${missingFragments.join(', ')}`,
  );
}

for (const fileName of migrationFiles.slice(hardeningFileIndex + 1)) {
  const { source, tables } = createdTablesByFile.get(fileName);
  for (const table of tables) {
    const escapedTable = table.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const hasRls = new RegExp(
      `ALTER\\s+TABLE\\s+(?:public\\.)?"${escapedTable}"\\s+ENABLE\\s+ROW\\s+LEVEL\\s+SECURITY`,
      'i',
    ).test(source);
    const hasPublicRevoke = new RegExp(
      `REVOKE\\s+ALL\\s+PRIVILEGES\\s+ON\\s+TABLE\\s+public\\."${escapedTable}"\\s+FROM\\s+PUBLIC`,
      'i',
    ).test(source);
    const hasApiRoleRevokes =
      /FROM\s+anon/i.test(source) && /FROM\s+authenticated/i.test(source);
    if (!hasRls || !hasPublicRevoke || !hasApiRoleRevokes) {
      failures.push(
        `Post-baseline table ${table} in ${fileName} must enable RLS and revoke PUBLIC, anon, and authenticated privileges in the same migration`,
      );
    }
  }
}

if (failures.length > 0) {
  process.stderr.write(`${failures.join('\n')}\n`);
  process.exitCode = 1;
} else {
  process.stdout.write(
    `Database security source check passed for ${createdTables.size} backend-only tables.\n`,
  );
}
