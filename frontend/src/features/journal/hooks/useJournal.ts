import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { journalApi } from '../api/journal.api';

export function useJournalEntries(page = 0, size = 20) {
  return useQuery({
    queryKey: ['journal', page, size],
    queryFn: () => journalApi.getEntries(page, size),
  });
}

export function useGenerateJournal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: journalApi.generate,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['journal'] });
    },
  });
}
