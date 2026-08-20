import { Controller, Get, HttpStatus } from '@nestjs/common';
import { ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';

import { ApiHttpException } from '../../shared/api/api-http.exception';
import { GetLiveness } from '../application/get-liveness';
import { GetReadiness } from '../application/get-readiness';
import type { HealthStatus, ReadinessStatus } from '../domain/health';

@ApiTags('operations')
@Controller()
export class HealthController {
  constructor(
    private readonly getLiveness: GetLiveness,
    private readonly getReadiness: GetReadiness,
  ) {}

  @Get('health')
  @ApiOperation({ summary: 'Process liveness' })
  @ApiResponse({ status: 200, schema: { example: { status: 'ok' } } })
  health(): HealthStatus {
    return this.getLiveness.execute();
  }

  @Get('ready')
  @ApiOperation({ summary: 'Dependency readiness' })
  @ApiResponse({ status: 200, schema: { example: { status: 'ok', checks: { database: 'up' } } } })
  @ApiResponse({ status: 503, description: 'A required dependency is unavailable' })
  async ready(): Promise<ReadinessStatus> {
    const result = await this.getReadiness.execute();
    if (result.status === 'error') {
      throw new ApiHttpException(
        HttpStatus.SERVICE_UNAVAILABLE,
        'service_unavailable',
        'Service unavailable',
        { checks: result.checks },
      );
    }
    return result;
  }
}
