import {signIn} from './auth-helper';
import { expect, test } from '@playwright/test';

test('receives a batch and shows ranked location recommendations', async ({ page }) => {
  let batchCreated = false;
  let recommendationRequested = false;

  await page.route('**/api/batches', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    const body = route.request().postDataJSON();
    expect(body).toMatchObject({
      productId: 1,
      batchCode: 'E2E-BATCH-001',
      quantity: 5,
    });
    batchCreated = true;
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 42, batchCode: 'E2E-BATCH-001', quantity: 5,
        volumeDm3: 10, weightKg: 5, receivingDate: '2026-11-22',
        expiryDate: null, status: 'RECEIVED',
      }),
    });
  });

  await page.route('**/api/recommendations/batches/42*', async (route) => {
    recommendationRequested = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        batchId: 42, strategy: 'ABC_XYZ', feasibleCount: 1,
        recommendations: [{
          rank: 1, locationVersion: 0, locationId: 12, locationCode: 'ST-12',
          distanceMeters: 18.5, explanation: 'Compatible location with available capacity',
        }],
      }),
    });
  });

  let placementRequests = 0;
  await page.route('**/api/placements/confirm', async route => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({batchId: 42, locationId: 12, locationVersion: 0});
    placementRequests++;
    await route.fulfill({status: 201, json: {movementId: 1, batchId: 42, locationId: 12,
      locationCode: 'ST-12', status: 'placed', occupiedVolumeDm3: 10, occupiedWeightKg: 5,
      placedAt: '2026-11-25T19:08:34Z'}});
  });
  await signIn(page, '/receive');
  await page.getByLabel('Product ID *').fill('1');
  await page.getByLabel('Batch code *').fill('E2E-BATCH-001');
  await page.getByLabel('Quantity *').fill('5');
  await page.getByRole('button', { name: 'Get recommendation' }).click();

  await expect(page.getByText(/Batch #42 registered/)).toBeVisible();
  await expect(page.getByText('ST-12')).toBeVisible();
  await expect(page.getByText('Best match')).toBeVisible();
  expect(batchCreated).toBe(true);
  expect(recommendationRequested).toBe(true);
  await page.getByRole('button', {name: 'Confirm placement'}).click();
  await expect(page.getByRole('button', {name: 'Placement confirmed'})).toBeDisabled();
  expect(placementRequests).toBe(1);
});
