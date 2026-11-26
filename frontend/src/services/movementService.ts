import {api} from './apiClient';
import type {MovementHistoryPageResponse} from '../types/api';

export type MovementFilters = {
  productId?: number;
  locationId?: number;
  from?: string;
  to?: string;
};

export function getMovements(warehouseId: number, filters: MovementFilters = {}, page = 0, size = 20): Promise<MovementHistoryPageResponse> {
  const params = new URLSearchParams({page: String(page), size: String(size)});
  if (filters.productId !== undefined) params.set('productId', String(filters.productId));
  if (filters.locationId !== undefined) params.set('locationId', String(filters.locationId));
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  return api<MovementHistoryPageResponse>(`/warehouses/${warehouseId}/movements?${params}`);
}
