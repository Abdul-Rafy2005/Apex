import { useAuthStore } from '@/store/auth.store';
import { ApiError } from '@/features/auth/api/auth.api';

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const token = useAuthStore.getState().accessToken;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(init?.headers as Record<string, string> | undefined),
  };

  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...init,
    headers,
  });

  if (res.status === 401) {
    const refreshed = await useAuthStore.getState().refreshAccessToken();
    if (refreshed) {
      const newToken = useAuthStore.getState().accessToken;
      const retryHeaders: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(newToken ? { Authorization: `Bearer ${newToken}` } : {}),
        ...(init?.headers as Record<string, string> | undefined),
      };
      const retryRes = await fetch(`${API_BASE}${path}`, {
        credentials: 'include',
        ...init,
        headers: retryHeaders,
      });
      if (!retryRes.ok) {
        let body: unknown;
        try { body = await retryRes.json(); } catch { body = null; }
        const err = body as { detail?: string; title?: string };
        throw new ApiError(err.detail ?? err.title ?? `Request failed (${retryRes.status})`, retryRes.status, body);
      }
      if (retryRes.status === 204) return undefined as T;
      return retryRes.json() as Promise<T>;
    }
    useAuthStore.getState().logout();
    throw new ApiError('Session expired', 401);
  }

  if (!res.ok) {
    let body: unknown;
    try { body = await res.json(); } catch { body = null; }
    const err = body as { detail?: string; title?: string; status?: number };
    throw new ApiError(err.detail ?? err.title ?? `Request failed (${res.status})`, res.status, body);
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}
