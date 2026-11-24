import {test,expect} from '@playwright/test';
import {signIn} from './auth-helper';
test('requires login and clears session on logout',async ({page})=>{
  await page.goto('/locations'); await expect(page).toHaveURL(/\/login$/);
  await signIn(page); await expect(page.getByRole('heading',{name:'Warehouse workspace'})).toBeVisible();
  await page.getByRole('button',{name:'Sign out'}).click(); await expect(page).toHaveURL(/\/login$/);
});
test('manager cannot open receiving page',async ({page})=>{
  await signIn(page,'/','manager'); await page.evaluate(()=>history.pushState({},'', '/receive'));
  await page.evaluate(()=>window.dispatchEvent(new PopStateEvent('popstate')));
  await expect(page.getByRole('heading',{name:'Access denied'})).toBeVisible();
});
test('sends bearer token and logs out on 401',async ({page})=>{
  await page.route('**/api/warehouses/*/locations*',async route=>{
    expect(route.request().headers().authorization).toBe('Bearer mock-token'); await route.fulfill({status:401});
  });
  await signIn(page); await page.getByRole('link',{name:/view locations/i}).click(); await expect(page).toHaveURL(/\/login$/);
});
