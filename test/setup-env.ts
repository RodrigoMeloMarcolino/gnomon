process.env.NODE_ENV ??= 'test';
process.env.DATABASE_URL ??= 'postgres://gnomon:test-only@localhost:5432/gnomon_test';
process.env.CORS_ORIGINS ??= 'http://localhost:3000';
