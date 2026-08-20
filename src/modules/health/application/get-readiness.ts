import type { ReadinessProbe, ReadinessStatus } from '../domain/health';

export class GetReadiness {
  constructor(private readonly probe: ReadinessProbe) {}

  async execute(): Promise<ReadinessStatus> {
    const database = (await this.probe.check()) ? 'up' : 'down';
    return {
      status: database === 'up' ? 'ok' : 'error',
      checks: { database },
    };
  }
}
