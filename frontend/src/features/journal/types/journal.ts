export interface JournalEntryResponse {
  id: string;
  entryDate: string;
  narrativeText: string;
  generatedAt: string;
}

export interface JournalPageResponse {
  content: JournalEntryResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
