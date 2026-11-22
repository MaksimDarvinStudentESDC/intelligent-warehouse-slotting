import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import ReceiveBatchPage from '../pages/ReceiveBatchPage';
import {createBatch, recommend} from '../services/batchService';

vi.mock('../services/batchService', () => ({createBatch: vi.fn(), recommend: vi.fn()}));
const createBatchMock = vi.mocked(createBatch);
const recommendMock = vi.mocked(recommend);

function renderPage() {
  const queryClient = new QueryClient({defaultOptions: {mutations: {retry: false}, queries: {retry: false}}});
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
      {rank: 1, locationId: 7, locationCode: 'ST-07', distanceMeters: 12, explanation: 'Enough capacity'}
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
});
