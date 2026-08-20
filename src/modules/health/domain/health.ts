export interface HealthStatus {
  status: 'ok';
}

export interface ReadinessStatus {
  status: 'ok' | 'error';
  checks: {
    database: 'up' | 'down';
  };
}

export interface ReadinessProbe {
  check(): Promise<boolean>;
}
