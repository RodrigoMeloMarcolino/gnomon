import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';

import type { Environment } from '../config/environment';
import { createNestTypeOrmOptions } from './typeorm-options';

@Module({
  imports: [
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService<Environment, true>) =>
        createNestTypeOrmOptions({
          DATABASE_URL: config.get('DATABASE_URL', { infer: true }),
          DATABASE_SSL_MODE: config.get('DATABASE_SSL_MODE', { infer: true }),
          DATABASE_POOL_MAX: config.get('DATABASE_POOL_MAX', { infer: true }),
        }),
    }),
  ],
})
export class DatabaseModule {}
