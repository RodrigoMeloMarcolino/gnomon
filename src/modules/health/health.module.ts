import type { DynamicModule, Provider } from '@nestjs/common';
import { Module } from '@nestjs/common';

import { HealthController } from './api/health.controller';
import { GetLiveness } from './application/get-liveness';
import { GetReadiness } from './application/get-readiness';
import type { ReadinessProbe } from './domain/health';

export const READINESS_PROBE = Symbol('READINESS_PROBE');

@Module({})
export class HealthModule {
  static register(readinessProbeProvider: Provider): DynamicModule {
    return {
      module: HealthModule,
      controllers: [HealthController],
      providers: [
        readinessProbeProvider,
        { provide: GetLiveness, useFactory: () => new GetLiveness() },
        {
          provide: GetReadiness,
          inject: [READINESS_PROBE],
          useFactory: (probe: ReadinessProbe) => new GetReadiness(probe),
        },
      ],
    };
  }
}
