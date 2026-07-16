import { create } from 'zustand';
import type { UserResponse } from '@/features/auth/types/auth';
import { authApi } from '@/features/auth/api/auth.api';

interface AuthState {
  user: UserResponse | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
  restoreSession: () => Promise<void>;
  refreshAccessToken: () => Promise<boolean>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  isAuthenticated: false,
  isLoading: false,

  login: async (email, password) => {
    set({ isLoading: true });
    try {
      const res = await authApi.login({ email, password });
      set({
        user: res.user,
        accessToken: res.accessToken,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (err) {
      set({ isLoading: false });
      throw err;
    }
  },

  register: async (email, password, displayName) => {
    set({ isLoading: true });
    try {
      const res = await authApi.register({ email, password, displayName });
      set({
        user: res.user,
        accessToken: res.accessToken,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (err) {
      set({ isLoading: false });
      throw err;
    }
  },

  logout: () => {
    set({
      user: null,
      accessToken: null,
      isAuthenticated: false,
    });
  },

  restoreSession: async () => {
    set({ isLoading: true });
    try {
      const res = await authApi.refresh();
      set({
        user: res.user,
        accessToken: res.accessToken,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch {
      set({ isLoading: false });
    }
  },

  refreshAccessToken: async () => {
    try {
      const res = await authApi.refresh();
      set({
        user: res.user,
        accessToken: res.accessToken,
        isAuthenticated: true,
      });
      return true;
    } catch {
      set({
        user: null,
        accessToken: null,
        isAuthenticated: false,
      });
      return false;
    }
  },
}));
