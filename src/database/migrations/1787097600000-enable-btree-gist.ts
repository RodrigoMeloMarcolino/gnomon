import type { MigrationInterface, QueryRunner } from 'typeorm';

export class EnableBtreeGist1787097600000 implements MigrationInterface {
  name = 'EnableBtreeGist1787097600000';

  async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('CREATE EXTENSION IF NOT EXISTS btree_gist');
  }

  async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP EXTENSION IF EXISTS btree_gist');
  }
}
