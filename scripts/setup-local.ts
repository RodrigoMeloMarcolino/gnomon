import { randomBytes } from 'node:crypto';
import { constants, access, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

async function exists(path: string): Promise<boolean> {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

function secret(): string {
  return randomBytes(32).toString('base64url');
}

async function main(): Promise<void> {
  const target = resolve('.env');
  if (await exists(target)) {
    process.stdout.write('.env already exists; no changes were made.\n');
    return;
  }

  const postgresPassword = secret();
  const redisPassword = secret();
  const keycloakPassword = secret();
  const template = await readFile(resolve('.env.example'), 'utf8');
  const contents = template
    .replaceAll('__GENERATED_POSTGRES_PASSWORD__', postgresPassword)
    .replaceAll('__URL_ENCODED_POSTGRES_PASSWORD__', encodeURIComponent(postgresPassword))
    .replaceAll('__GENERATED_REDIS_PASSWORD__', redisPassword)
    .replaceAll('__URL_ENCODED_REDIS_PASSWORD__', encodeURIComponent(redisPassword))
    .replaceAll('__GENERATED_KEYCLOAK_PASSWORD__', keycloakPassword);

  await writeFile(target, contents, { encoding: 'utf8', flag: 'wx', mode: 0o600 });
  process.stdout.write('.env created with random local-only credentials.\n');
}

void main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : 'unknown setup error';
  process.stderr.write(`Local setup failed: ${message}\n`);
  process.exitCode = 1;
});
