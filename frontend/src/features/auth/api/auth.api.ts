import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth';

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  });

  if (!res.ok) {
    let body: unknown;
    try {
      body = await res.json();
    } catch {
      body = null;
    }
    const err = body as { type?: string; title?: string; status?: number; detail?: string };
    throw new ApiError(
      err.detail ?? err.title ?? `Request failed (${res.status})`,
      res.status,
      err,
    );
  }

  return res.json() as Promise<T>;
}

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(message: string, status: number, body?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export const authApi = {
  register(data: RegisterRequest): Promise<AuthResponse> {
    return apiFetch<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  login(data: LoginRequest): Promise<AuthResponse> {
    return apiFetch<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  refresh(): Promise<AuthResponse> {
    return apiFetch<AuthResponse>('/api/v1/auth/refresh', {
      method: 'POST',
    });
  },

  getMe(token: string): Promise<import('../types/auth').UserResponse> {
    return apiFetch<import('../types/auth').UserResponse>('/api/v1/users/me', {
      headers: { Authorization: `Bearer ${token}` },
    });
  },
};
