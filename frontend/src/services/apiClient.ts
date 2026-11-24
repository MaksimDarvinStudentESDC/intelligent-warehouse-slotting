export class ApiError extends Error {constructor(public status:number,message:string){super(message)}}
let token: string | null = null;
let onUnauthorized: (() => void) | undefined;
export function setApiSession(value: string | null, handler?: () => void) { token = value; onUnauthorized = handler; }
export async function api<T>(path:string,options?:RequestInit):Promise<T>{
  const requestToken = path === '/auth/login' ? null : token;
  const headers = new Headers(options?.headers);
  if (!headers.has('Content-Type')) headers.set('Content-Type','application/json');
  if (requestToken) headers.set('Authorization',`Bearer ${requestToken}`);
  let response: Response;
  try { response = await fetch(`/api${path}`, {...options,headers}); }
  catch { throw new ApiError(0,'Cannot connect to the server. Check that the backend is running.'); }
  if (!response.ok) {
    if (response.status === 401) {
      if (requestToken && requestToken === token) onUnauthorized?.();
      throw new ApiError(401,path === '/auth/login' ? 'Invalid login or password.' : 'Session expired. Please sign in again.');
    }
    if (response.status === 403) throw new ApiError(403,'You do not have permission to perform this action.');
    const body = await response.text();
    throw new ApiError(response.status,body || `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}
