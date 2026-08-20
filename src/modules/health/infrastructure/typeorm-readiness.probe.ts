import type { DataSource } from 'typeorm';

import type { ReadinessProbe } from '../domain/health';

export class TypeOrmReadinessProbe implements ReadinessProbe {
  constructor(private readonly dataSource: DataSource) {}

  async check(): Promise<boolean> {
    try {
      await this.dataSource.query('SELECT 1');
      return true;
    } catch {
      return false;
    }
  }
}
