import {api} from './apiClient';
import type {Batch,CreateBatch,RecommendationResult} from '../types/api';
export const createBatch=(data:CreateBatch)=>api<Batch>('/batches',{method:'POST',body:JSON.stringify(data)});
export const recommend=(id:number)=>api<RecommendationResult>(`/recommendations/batches/${id}`);
