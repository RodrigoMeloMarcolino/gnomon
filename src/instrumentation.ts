import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { NodeSDK } from '@opentelemetry/sdk-node';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';

let sdk: NodeSDK | undefined;

function writeTelemetryWarning(error: unknown): void {
  const message = error instanceof Error ? error.message : 'unknown telemetry error';
  process.stdout.write(
    `${JSON.stringify({ level: 'warn', context: 'telemetry', msg: 'OTLP disabled after startup failure', error: message })}\n`,
  );
}

export function initializeTelemetry(
  environment: NodeJS.ProcessEnv = process.env,
): NodeSDK | undefined {
  const endpoint = environment.OTEL_EXPORTER_OTLP_ENDPOINT;
  if (!endpoint) {
    return undefined;
  }

  try {
    sdk = new NodeSDK({
      resource: resourceFromAttributes({
        [ATTR_SERVICE_NAME]: environment.OTEL_SERVICE_NAME ?? 'gnomon',
      }),
      traceExporter: new OTLPTraceExporter({ url: endpoint }),
    });
    sdk.start();
    return sdk;
  } catch (error) {
    writeTelemetryWarning(error);
    sdk = undefined;
    return undefined;
  }
}

export async function shutdownTelemetry(): Promise<void> {
  if (!sdk) {
    return;
  }

  try {
    await sdk.shutdown();
  } catch (error) {
    writeTelemetryWarning(error);
  }
}
