import { defineConfig } from '@playwright/test';

export default defineConfig({
    testDir: './tests',
    timeout: 60_000,
    fullyParallel: false,
    workers: 1,
    reporter: [['list'], ['html', { open: 'never' }]],
    use: { baseURL: 'http://127.0.0.1:8080', actionTimeout: 15_000, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
    webServer: {
        command: 'java -jar ../target/payara-micro.jar --deploy ../target/websocket-lab.war --contextroot / --port 8080 --nocluster',
        url: 'http://127.0.0.1:8080/api/health',
        timeout: 180_000,
        reuseExistingServer: !process.env.CI,
        env: { DEMO_INGEST_KEY: 'local-e2e-ingestion-key-not-for-deployment' },
    },
});
