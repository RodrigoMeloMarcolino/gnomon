import { Module } from '@nestjs/common';
import { DataSource } from 'typeorm';

import { ConfigModule } from './config/config.module';
import { DatabaseModule } from './database/database.module';
import { LoggingModule } from './logging/logging.module';
import { HealthModule, READINESS_PROBE } from './modules/health/health.module';
import { TypeOrmReadinessProbe } from './modules/health/infrastructure/typeorm-readiness.probe';

@Module({
  imports: [
    ConfigModule,
    LoggingModule,
    DatabaseModule,
    HealthModule.register({
      provide: READINESS_PROBE,
      inject: [DataSource],
      useFactory: (dataSource: DataSource) => new TypeOrmReadinessProbe(dataSource),
    }),
  ],
})
export class AppModule {}
