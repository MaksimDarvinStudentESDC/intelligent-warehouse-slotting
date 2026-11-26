import type {UserRole} from '../types/auth';
const permissions: Record<string, UserRole[]> = {
  '/receive':['operator'], '/locations':['operator','manager','planner'],
  '/movements':['operator','manager','planner'],
  '/import':['planner'], '/compare':['planner','manager','business_manager'],
};
export const canAccess = (role: UserRole, path: string) => path === '/' || (permissions[path]?.includes(role) ?? false);
