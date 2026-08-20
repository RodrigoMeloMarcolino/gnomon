import type { HealthStatus } from '../domain/health';

export class GetLiveness {
  execute(): HealthStatus {
    return { status: 'ok' };
  }
}
