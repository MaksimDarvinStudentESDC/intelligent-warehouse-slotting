import type {Page} from '@playwright/test';
export async function signIn(page: Page, path = '/', role = 'operator') {
  await page.route('**/api/auth/login', route => route.fulfill({json:{accessToken:'mock-token',tokenType:'Bearer',expiresIn:1800,user:{id:1,login:'mock',fullName:'Test User',role}}}));
  await page.goto(path);
  await page.getByLabel('Login', {exact:true}).fill('mock');
  await page.getByLabel('Password', {exact:true}).fill('mock-password');
  await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await page.waitForURL(url => url.pathname !== '/login');
}
