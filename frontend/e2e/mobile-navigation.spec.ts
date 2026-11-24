import {signIn} from './auth-helper';
import { expect, test } from '@playwright/test';

test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });

test('mobile navigation opens and navigates to Locations', async ({ page }) => {
  await page.route('**/api/warehouses/1/locations*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{
        id: 12, code: 'ST-12', zoneId: 4,
        capacityDm3: 100, occupiedDm3: 20, maxWeightKg: 200,
        distanceToReceivingM: 10, distanceToPickingM: 15,
        distanceToShippingM: 20, active: true,
      }]),
    });
  });

  await signIn(page);
  const menuButton = page.getByRole('button', { name: 'Open navigation' });
  await expect(menuButton).toBeVisible();
  await menuButton.click();
  await expect(page.getByRole('button', { name: 'Close navigation' })).toHaveAttribute('aria-expanded', 'true');

  const mobileNav = page.getByRole('navigation', { name: 'Mobile navigation' });
  await mobileNav.getByRole('link', { name: 'Locations' }).click();
  await expect(page).toHaveURL(/\/locations$/);
  await expect(page.getByRole('heading', { name: 'Storage locations' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'ST-12' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open navigation' })).toHaveAttribute('aria-expanded', 'false');
});
