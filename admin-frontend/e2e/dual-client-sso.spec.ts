import { expect, test, type Page } from '@playwright/test';
import { writeFileSync } from 'node:fs';

const studentBaseUrl = process.env.STUDENT_BASE_URL ?? 'http://127.0.0.1:5173';
const adminBaseUrl = process.env.ADMIN_BASE_URL ?? 'http://127.0.0.1:5174';
const iamOrigin = process.env.IAM_ORIGIN ?? 'http://localhost:8084';
const studentNumber = requiredEnvironmentVariable('TEST_STUDENT_NUMBER');
const password = requiredEnvironmentVariable('TEST_PASSWORD');
const evidencePath = requiredEnvironmentVariable('SSO_EVIDENCE_PATH');

interface StoredOidcUser {
  access_token?: string;
}

test('reuses the IAM login session across student and admin clients', async ({
  browser,
}) => {
  const context = await browser.newContext();
  const studentPage = await context.newPage();

  await studentPage.goto(`${studentBaseUrl}/login`);
  await studentPage.getByRole('button', { name: '使用校园统一身份认证' }).click();
  await studentPage.waitForURL(`${iamOrigin}/login`);
  await studentPage.locator('input[name="username"]').fill(studentNumber);
  await studentPage.locator('input[name="password"]').fill(password);
  await studentPage.locator('button[type="submit"]').click();
  await studentPage.waitForURL(`${studentBaseUrl}/trips`);

  const studentAccessToken = await oidcAccessToken(
    studentPage,
    'school-bus-student-web',
  );
  const studentClaims = jwtClaims(studentAccessToken);

  const adminPage = await context.newPage();
  let secondCredentialPromptSeen = false;
  adminPage.on('framenavigated', (frame) => {
    if (
      frame === adminPage.mainFrame() &&
      frame.url().startsWith(`${iamOrigin}/login`)
    ) {
      secondCredentialPromptSeen = true;
    }
  });

  await adminPage.goto(`${adminBaseUrl}/login`);
  await adminPage.getByRole('button', { name: '使用校园统一身份认证' }).click();
  await adminPage.waitForURL(`${adminBaseUrl}/vehicles`);

  const adminAccessToken = await oidcAccessToken(adminPage, 'school-bus-admin-web');
  const adminClaims = jwtClaims(adminAccessToken);
  const adminRoles = Array.isArray(adminClaims.roles) ? adminClaims.roles : [];

  expect(secondCredentialPromptSeen).toBe(false);
  expect(adminRoles).toContain('ADMIN');
  expect(adminClaims.sub).toBe(studentClaims.sub);
  expect(adminAccessToken).not.toBe(studentAccessToken);

  await adminPage.getByRole('button', { name: '退出统一认证' }).click();
  await adminPage.getByRole('button', { name: '确定' }).click();
  await adminPage.waitForURL(`${adminBaseUrl}/login`);

  const existingStudentTokenStillPresent = Boolean(
    await oidcAccessToken(studentPage, 'school-bus-student-web'),
  );

  const freshStudentPage = await context.newPage();
  await freshStudentPage.goto(`${studentBaseUrl}/login`);
  await freshStudentPage.getByRole('button', { name: '使用校园统一身份认证' }).click();
  await freshStudentPage.waitForURL(`${iamOrigin}/login`);
  await expect(freshStudentPage.locator('input[name="password"]')).toBeVisible();

  const evidence = {
    studentClientAuthorized: true,
    adminClientAuthorized: true,
    loginPromptBypassedForSecondClient: !secondCredentialPromptSeen,
    accessTokensDistinct: adminAccessToken !== studentAccessToken,
    subjectsMatch: adminClaims.sub === studentClaims.sub,
    adminRolePresent: adminRoles.includes('ADMIN'),
    iamSessionEndedByRpLogout: true,
    freshAuthorizationRequiresLogin: true,
    existingStudentTokenStillPresent,
    studentClientId: 'school-bus-student-web',
    adminClientId: 'school-bus-admin-web',
  };
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2), 'utf8');

  await context.close();
});

async function oidcAccessToken(page: Page, clientId: string): Promise<string> {
  const token = await page.evaluate((expectedClientId) => {
    const key = Object.keys(window.sessionStorage).find(
      (candidate) =>
        candidate.startsWith('oidc.user:') &&
        candidate.endsWith(`:${expectedClientId}`),
    );
    if (!key) return null;
    const raw = window.sessionStorage.getItem(key);
    if (!raw) return null;
    return (JSON.parse(raw) as StoredOidcUser).access_token ?? null;
  }, clientId);
  if (!token) throw new Error(`OIDC access token missing for ${clientId}`);
  return token;
}

function jwtClaims(token: string): Record<string, unknown> {
  const parts = token.split('.');
  if (parts.length !== 3 || !parts[1]) throw new Error('Invalid JWT structure');
  return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')) as Record<
    string,
    unknown
  >;
}

function requiredEnvironmentVariable(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} must be configured for browser acceptance`);
  return value;
}
