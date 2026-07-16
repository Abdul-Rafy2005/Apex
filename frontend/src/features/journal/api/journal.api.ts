import { apiFetch } from '@/lib/api';
import type { JournalEntryResponse, JournalPageResponse } from '../types/journal';

export const journalApi = {
  generate: () =>
    apiFetch<JournalEntryResponse>('/api/v1/journal/generate', {
      method: 'POST',
    }),

  getEntries: (page = 0, size = 20) =>
    apiFetch<JournalPageResponse>(
      `/api/v1/journal?page=${page}&size=${size}`,
    ),
};
