import {api} from './apiClient';
import type {PlacementRequest, PlacementResponse} from '../types/api';

export const confirmPlacement = (data: PlacementRequest) =>
  api<PlacementResponse>('/placements/confirm', {
    method: 'POST',
    body: JSON.stringify(data),
  });
