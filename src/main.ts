import 'reflect-metadata';

import { bootstrap } from './bootstrap';
import { initializeTelemetry, shutdownTelemetry } from './instrumentation';

initializeTelemetry();

void bootstrap().catch(async (error: unknown) => {
  const message = error instanceof Error ? error.message : 'unknown startup error';
  process.stdout.write(
    `${JSON.stringify({ level: 'fatal', context: 'bootstrap', msg: 'Application startup failed', error: message })}\n`,
  );
  await shutdownTelemetry();
  process.exitCode = 1;
});
