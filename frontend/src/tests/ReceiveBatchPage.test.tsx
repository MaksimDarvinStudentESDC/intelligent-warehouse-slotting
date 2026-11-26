import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {cleanup, fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import ReceiveBatchPage from '../pages/ReceiveBatchPage';
import {createBatch, recommend} from '../services/batchService';

import {ApiError} from '../services/apiClient';
import {confirmPlacement} from '../services/placementService';

vi.mock('../services/placementService', () => ({confirmPlacement: vi.fn()}));
vi.mock('../services/batchService', () => ({createBatch: vi.fn(), recommend: vi.fn()}));
const createBatchMock = vi.mocked(createBatch);
const recommendMock = vi.mocked(recommend);

function renderPage(queryClient = new QueryClient({defaultOptions: {mutations: {retry: false}, queries: {retry: false}}})) {
  return render(<QueryClientProvider client={queryClient}><ReceiveBatchPage /></QueryClientProvider>);
}
function fillValidFields() {
  fireEvent.change(screen.getByRole('spinbutton', {name: /Product ID/i}), {target: {value: '1'}});
  fireEvent.change(screen.getByRole('textbox', {name: /Batch code/i}), {target: {value: 'BATCH-TEST-001'}});
  fireEvent.change(screen.getByRole('spinbutton', {name: /Quantity/i}), {target: {value: '5'}});
}
function submit() {fireEvent.click(screen.getByRole('button', {name: /Get recommendation/i}));}

afterEach(cleanup);
beforeEach(() => {vi.clearAllMocks();});
describe('ReceiveBatchPage', () => {
  it('rejects an empty form without calling the backend', async () => {
    renderPage();
    fireEvent.change(screen.getByRole('spinbutton', {name: /Quantity/i}), {target: {value: '0'}});
    submit();
    expect(await screen.findByText('Product ID is required.')).toBeTruthy();
    expect(screen.getByText('Batch code is required.')).toBeTruthy();
    expect(createBatchMock).not.toHaveBeenCalled();
  });
  it('rejects quantity above 9999', async () => {
    renderPage();
    fillValidFields();
    fireEvent.change(screen.getByRole('spinbutton', {name: /Quantity/i}), {target: {value: '10000'}});
    submit();
    expect(await screen.findByText(/Quantity must be a whole number/i)).toBeTruthy();
    expect(createBatchMock).not.toHaveBeenCalled();
  });
  it('creates a batch and shows ranked recommendations', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    recommendMock.mockResolvedValue({batchId: 42, strategy: 'optimized', feasibleCount: 1, recommendations: [
      {rank: 1, locationVersion: 0, locationId: 7, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'}
    ]});
    renderPage();
    fillValidFields();
    submit();
    expect(await screen.findByText(/Batch #42 registered/i)).toBeTruthy();
    expect(createBatchMock).toHaveBeenCalledWith(expect.objectContaining({productId: 1, batchCode: 'BATCH-TEST-001', quantity: 5}), expect.anything());
    expect(recommendMock).toHaveBeenCalledWith(42, expect.anything());
    expect(screen.getByText(/ST-07/)).toBeTruthy();
  });
  it('retries recommendations without creating a duplicate batch', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    recommendMock.mockRejectedValueOnce(new Error('Network unavailable')).mockResolvedValueOnce({
      batchId: 42, strategy: 'optimized', feasibleCount: 0, recommendations: []
    });
    renderPage();
    fillValidFields();
    submit();
    const retry = await screen.findByRole('button', {name: /Retry recommendations for batch #42/i});
    fireEvent.click(retry);
    await waitFor(() => expect(recommendMock).toHaveBeenCalledTimes(2));
    expect(createBatchMock).toHaveBeenCalledTimes(1);
  });
  it('confirms the selected location with its version and prevents repeat placement', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    recommendMock.mockResolvedValue({batchId: 42, strategy: 'optimized', feasibleCount: 2, recommendations: [
      {rank: 1, locationId: 7, locationVersion: 0, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'},
      {rank: 2, locationId: 8, locationVersion: 3, locationCode: 'ST-08', distanceMeters: 15, explanation: 'Enough capacity'},
    ]});
    vi.mocked(confirmPlacement).mockResolvedValue({movementId: 1, batchId: 42, locationId: 8,
      locationCode: 'ST-08', status: 'placed', occupiedVolumeDm3: 10, occupiedWeightKg: 5,
      placedAt: '2026-11-25T19:08:34Z'});
    renderPage();
    fillValidFields();
    submit();
    fireEvent.click(await screen.findByRole('radio', {name: /ST-08/}));
    fireEvent.click(screen.getByRole('button', {name: 'Confirm placement'}));
    const confirmed = await screen.findByRole('button', {name: 'Placement confirmed'});
    expect((confirmed as HTMLButtonElement).disabled).toBe(true);
    expect(vi.mocked(confirmPlacement)).toHaveBeenCalledWith({batchId: 42, locationId: 8, locationVersion: 3}, expect.anything());
    fireEvent.click(confirmed);
    expect(vi.mocked(confirmPlacement)).toHaveBeenCalledTimes(1);
  });

  it('invalidates cached locations after successful placement', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    recommendMock.mockResolvedValue({batchId: 42, strategy: 'optimized', feasibleCount: 2, recommendations: [
      {rank: 1, locationId: 7, locationVersion: 0, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'},
      {rank: 2, locationId: 8, locationVersion: 3, locationCode: 'ST-08', distanceMeters: 15, explanation: 'Enough capacity'},
    ]});
    vi.mocked(confirmPlacement).mockResolvedValue({movementId: 1, batchId: 42, locationId: 8,
      locationCode: 'ST-08', status: 'placed', occupiedVolumeDm3: 10, occupiedWeightKg: 5,
      placedAt: '2026-11-25T19:08:34Z'});
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    client.setQueryData(['locations', 1], []);
    client.setQueryData(['locations', 2], []);
    client.setQueryData(['unrelated'], []);
    renderPage(client);
    fillValidFields();
    submit();
    fireEvent.click(await screen.findByRole('radio', {name: /ST-08/}));
    fireEvent.click(screen.getByRole('button', {name: 'Confirm placement'}));
    const confirmed = await screen.findByRole('button', {name: 'Placement confirmed'});
    expect(confirmed).toBeTruthy();
    expect(client.getQueryState(['locations', 1])?.isInvalidated).toBe(true);
    expect(client.getQueryState(['locations', 2])?.isInvalidated).toBe(true);
    expect(client.getQueryState(['unrelated'])?.isInvalidated).toBe(false);
  });
  it('does not invalidate cached locations after rejected placement', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    recommendMock.mockResolvedValue({batchId: 42, strategy: 'optimized', feasibleCount: 2, recommendations: [
      {rank: 1, locationId: 7, locationVersion: 0, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'},
      {rank: 2, locationId: 8, locationVersion: 3, locationCode: 'ST-08', distanceMeters: 15, explanation: 'Enough capacity'},
    ]});
    vi.mocked(confirmPlacement).mockRejectedValueOnce(new Error('STALE_RECOMMENDATION'));
    const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
    client.setQueryData(['locations', 1], []);
    client.setQueryData(['locations', 2], []);
    client.setQueryData(['unrelated'], []);
    renderPage(client);
    fillValidFields();
    submit();
    fireEvent.click(await screen.findByRole('radio', {name: /ST-08/}));
    fireEvent.click(screen.getByRole('button', {name: 'Confirm placement'}));
    expect(await screen.findByText('STALE_RECOMMENDATION')).toBeTruthy();
    expect(client.getQueryState(['locations', 1])?.isInvalidated).toBe(false);
    expect(client.getQueryState(['locations', 2])?.isInvalidated).toBe(false);
    expect(client.getQueryState(['unrelated'])?.isInvalidated).toBe(false);
  });
  it('blocks stale recommendations and refreshes the same batch before explicit confirmation', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    const first = {batchId: 42, strategy: 'optimized', feasibleCount: 1, recommendations: [
      {rank: 1, locationId: 7, locationVersion: 0, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'}
    ]};
    recommendMock.mockResolvedValueOnce(first).mockResolvedValueOnce({...first, recommendations: [
      {...first.recommendations[0], locationVersion: 1}
    ]});
    vi.mocked(confirmPlacement).mockRejectedValueOnce(new ApiError(409, JSON.stringify({message: 'STALE_RECOMMENDATION'})))
      .mockResolvedValueOnce({movementId: 1, batchId: 42, locationId: 7, locationCode: 'ST-07', status: 'placed',
        occupiedVolumeDm3: 10, occupiedWeightKg: 5, placedAt: '2026-11-25T19:08:34Z'});
    renderPage(); fillValidFields(); submit();
    await screen.findByRole('radio', {name: /ST-07/});
    fireEvent.click(screen.getByRole('button', {name: 'Confirm placement'}));
    fireEvent.click(await screen.findByRole('button', {name: 'Refresh recommendations'}));
    await waitFor(() => expect(screen.queryByRole('button', {name: 'Refresh recommendations'})).toBeNull());
    expect(createBatchMock).toHaveBeenCalledTimes(1);
    expect(recommendMock).toHaveBeenCalledTimes(2);
    expect(recommendMock).toHaveBeenLastCalledWith(42, expect.anything());
    expect(vi.mocked(confirmPlacement)).toHaveBeenCalledTimes(1);
    expect((screen.getByRole('button', {name: 'Confirm placement'}) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole('radio', {name: /ST-07/}));
    fireEvent.click(screen.getByRole('button', {name: 'Confirm placement'}));
    await screen.findByRole('button', {name: 'Placement confirmed'});
    expect(vi.mocked(confirmPlacement)).toHaveBeenLastCalledWith({batchId: 42, locationId: 7, locationVersion: 1}, expect.anything());
  });
  it('blocks further confirmation when the batch is no longer awaiting placement', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    recommendMock.mockResolvedValue({batchId: 42, strategy: 'optimized', feasibleCount: 1, recommendations: [
      {rank: 1, locationId: 7, locationVersion: 0, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'}
    ]});
    vi.mocked(confirmPlacement).mockRejectedValueOnce(new ApiError(409, 'BATCH_NOT_AWAITING_PUTAWAY'));
    renderPage(); fillValidFields(); submit();
    await screen.findByRole('radio', {name: /ST-07/});
    fireEvent.click(screen.getByRole('button', {name: 'Confirm placement'}));
    await screen.findByText(/This batch is no longer awaiting placement/);
    expect(screen.queryByRole('button', {name: 'Refresh recommendations'})).toBeNull();
    expect((screen.getByRole('button', {name: 'Confirm placement'}) as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows server placement details and resets the form for another batch', async () => {
    createBatchMock.mockResolvedValue({id: 42} as Awaited<ReturnType<typeof createBatch>>);
    recommendMock.mockResolvedValue({batchId: 42, strategy: 'optimized', feasibleCount: 1, recommendations: [
      {rank: 1, locationId: 7, locationVersion: 0, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'}
    ]});
    vi.mocked(confirmPlacement).mockResolvedValue({movementId: 81, batchId: 42, locationId: 7,
      locationCode: 'ST-07', status: 'placed', occupiedVolumeDm3: 150, occupiedWeightKg: 75,
      placedAt: '2026-11-25T19:08:34Z'});
    renderPage(); fillValidFields(); submit();
    await screen.findByRole('radio', {name: /ST-07/});
    fireEvent.click(screen.getByRole('button', {name: 'Confirm placement'}));
    const card = await screen.findByRole('region', {name: 'Placement result'});
    for (const value of ['#42', '#81', 'ST-07', 'Placed', '150 dm³', '75 kg']) {
      expect(within(card).getByText(value)).toBeTruthy();
    }
    expect(card.querySelector('time')?.dateTime).toBe('2026-11-25T19:08:34Z');
    expect((screen.getByRole('button', {name: 'Get recommendation'}) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(within(card).getByRole('button', {name: 'Receive another batch'}));
    expect(screen.queryByRole('region', {name: 'Placement result'})).toBeNull();
    expect(screen.getByText('No recommendation yet')).toBeTruthy();
    expect((screen.getByRole('spinbutton', {name: /Product ID/}) as HTMLInputElement).value).toBe('');
    expect((screen.getByRole('textbox', {name: /Batch code/}) as HTMLInputElement).value).toBe('');
    expect((screen.getByRole('spinbutton', {name: /Quantity/}) as HTMLInputElement).value).toBe('1');
    expect((screen.getByLabelText(/Expiry date/) as HTMLInputElement).value).toBe('');
    expect(createBatchMock).toHaveBeenCalledTimes(1);
    expect(vi.mocked(confirmPlacement)).toHaveBeenCalledTimes(1);
    expect((screen.getByRole('button', {name: 'Get recommendation'}) as HTMLButtonElement).disabled).toBe(false);
  });
});
