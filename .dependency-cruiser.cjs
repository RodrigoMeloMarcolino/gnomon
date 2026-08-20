/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      from: {},
      to: { circular: true },
    },
    {
      name: 'domain-is-pure',
      severity: 'error',
      from: { path: '^src/modules/[^/]+/domain/' },
      to: {
        dependencyTypes: ['npm', 'npm-dev', 'npm-optional', 'npm-peer'],
      },
    },
    {
      name: 'domain-does-not-depend-on-outer-layers',
      severity: 'error',
      from: { path: '^src/modules/[^/]+/domain/' },
      to: { path: '^src/modules/[^/]+/(api|application|infrastructure)/' },
    },
    {
      name: 'application-does-not-depend-on-api-or-infrastructure',
      severity: 'error',
      from: { path: '^src/modules/[^/]+/application/' },
      to: { path: '^src/modules/[^/]+/(api|infrastructure)/' },
    },
    {
      name: 'api-does-not-depend-on-infrastructure',
      severity: 'error',
      from: { path: '^src/modules/[^/]+/api/' },
      to: { path: '^src/modules/[^/]+/infrastructure/' },
    },
    {
      name: 'infrastructure-does-not-depend-on-api-or-application',
      severity: 'error',
      from: { path: '^src/modules/[^/]+/infrastructure/' },
      to: { path: '^src/modules/[^/]+/(api|application)/' },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    exclude: { path: '\\.spec\\.ts$' },
    tsConfig: { fileName: 'tsconfig.json' },
    enhancedResolveOptions: { exportsFields: ['exports'] },
    reporterOptions: {
      text: { highlightFocused: true },
    },
  },
};
