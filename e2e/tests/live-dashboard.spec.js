import { test, expect } from '@playwright/test';

async function signIn(page, username) {
    await page.goto('/login.xhtml');
    await page.getByLabel('Username', { exact: true }).fill(username);
    await page.getByLabel('Password', { exact: true }).fill(process.env.DEMO_PASSWORD || 'Demo123!');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/dashboard.xhtml/);
    await expect(page.locator('#connection-status')).toHaveAttribute('data-state', 'connected', { timeout: 20_000 });
}

test('login, shared push, user-specific push, offline recovery and logout', async ({ browser, request }) => {
    const aliceContext = await browser.newContext();
    const bobContext = await browser.newContext();
    const alice = await aliceContext.newPage();
    const bob = await bobContext.newPage();
    const receivedFrames = [];
    bob.on('websocket', socket => socket.on('framereceived', event => receivedFrames.push(event.payload)));
    await signIn(alice, 'alice');
    await signIn(bob, 'bob');
    await expect(bob.getByRole('button', { name: 'Apply change', exact: true })).toHaveCount(0);

    // Stop background changes so version assertions describe this command only.
    const pause = alice.getByRole('button', { name: 'Pause simulator', exact: true });
    if (await pause.isVisible()) await pause.click();
    await expect(alice.getByRole('button', { name: 'Start simulator', exact: true })).toBeVisible();
    const before = Number(await bob.getByTestId('data-version').textContent());
    await alice.getByRole('button', { name: 'Send one update', exact: true }).click();
    await expect.poll(async () => Number(await bob.getByTestId('data-version').textContent())).toBeGreaterThan(before);
    expect(receivedFrames.some(frame => String(frame).includes('dashboard-changed'))).toBeTruthy();

    await alice.getByRole('button', { name: 'Send me a notification', exact: true }).click();
    await expect(alice.getByText(/Your private notification #/).first()).toBeVisible();
    await expect(bob.getByText(/Your private notification #/)).toHaveCount(0);
    expect(receivedFrames.some(frame => String(frame).includes('private-notification'))).toBeFalsy();

    const unauthorized = await request.post('/api/ingest', { data: { nodeId: 'node-2', load: 88, status: 'DEGRADED' } });
    expect(unauthorized.status()).toBe(401);

    const beforeOffline = Number(await alice.getByTestId('data-version').textContent());
    await aliceContext.setOffline(true);
    const accepted = await request.post('/api/ingest', {
        headers: { 'X-Ingest-Key': 'local-e2e-ingestion-key-not-for-deployment' },
        data: { nodeId: 'node-2', load: 88, status: 'DEGRADED' },
    });
    expect(accepted.status()).toBe(202);
    await aliceContext.setOffline(false);
    await expect(alice.locator('#connection-status')).toHaveAttribute('data-state', 'connected', { timeout: 30_000 });
    await expect.poll(async () => Number(await alice.getByTestId('data-version').textContent()), { timeout: 30_000 }).toBeGreaterThan(beforeOffline);

    await alice.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(alice).toHaveURL(/\/login.xhtml/);
    await alice.goto('/app/dashboard.xhtml');
    await expect(alice.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible();
    await aliceContext.close();
    await bobContext.close();
});

test('invalid credentials cannot enter the protected dashboard', async ({ page }) => {
    await page.goto('/login.xhtml');
    await page.getByLabel('Username', { exact: true }).fill('alice');
    await page.getByLabel('Password', { exact: true }).fill('incorrect-password');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page).not.toHaveURL(/\/app\/dashboard.xhtml/);
    await expect(page.getByText('Sign-in failed', { exact: true })).toBeVisible();
    await page.goto('/app/dashboard.xhtml');
    await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible();
});
