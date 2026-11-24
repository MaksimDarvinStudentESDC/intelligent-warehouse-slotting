
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const wcagTags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const pages = [
    { name: 'Home', path: '/' },
    { name: 'Receive Batch', path: '/receive' },
    { name: 'Locations', path: '/locations' },
];

const mockLocations = [
    {
        id: 1,
        code: 'ST-01',
        zoneId: 1,
        occupiedDm3: 20,
        capacityDm3: 100,
    },
];

for (const pageInfo of pages) {
    test(`${pageInfo.name} meets automated WCAG 2.1 AA checks`, async ({
                                                                           page,
                                                                       }) => {
        if (pageInfo.path === '/locations') {
            await page.route(
                '**/api/warehouses/*/locations*',
                async (route) => {
                    await route.fulfill({
                        status: 200,
                        contentType: 'application/json',
                        body: JSON.stringify(mockLocations),
                    });
                },
            );
        }

        await page.goto(pageInfo.path);

        await expect(page.locator('main')).toBeVisible();

        if (pageInfo.path === '/locations') {
            await expect(page.getByText('ST-01')).toBeVisible();

            await expect(
                page.getByText('80.0 dm³', { exact: true }),
            ).toBeVisible();

            await expect(
                page.getByRole('region', {
                    name: 'Warehouse storage locations',
                }),
            ).toHaveAttribute('tabindex', '0');
        }

        const results = await new AxeBuilder({ page })
            .withTags(wcagTags)
            .analyze();

        expect(
            results.violations,
            JSON.stringify(results.violations, null, 2),
        ).toEqual([]);
    });
}

test('Receive Batch validation errors meet WCAG 2.1 AA checks', async ({
                                                                           page,
                                                                       }) => {
    await page.goto('/receive');

    await expect(page.locator('main')).toBeVisible();

    await page.getByRole('button', {
        name: /get recommendation/i,
    }).click();

    await expect(
        page.getByText(/product id is required|enter a valid product id/i),
    ).toBeVisible();

    const results = await new AxeBuilder({ page })
        .withTags(wcagTags)
        .analyze();

    expect(
        results.violations,
        JSON.stringify(results.violations, null, 2),
    ).toEqual([]);
});