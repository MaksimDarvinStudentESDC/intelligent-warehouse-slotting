import {api} from './apiClient';
import type {Location} from '../types/api';
export const getLocations=(warehouseId:number)=>api<Location[]>(`/warehouses/${warehouseId}/locations`);
