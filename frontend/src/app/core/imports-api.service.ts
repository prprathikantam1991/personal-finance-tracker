import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Account } from './account';

export interface ParsedTransaction { date: string; description: string; amount: number; balance: number | null; }
export interface ImportResult {
  importId: string;
  originalFilename: string;
  status: 'REVIEW_REQUIRED';
  accountIdentification: { status: 'MATCHED' | 'CREATED' | 'CONFIRMATION_REQUIRED'; account: Account | null; message: string };
  transactions: ParsedTransaction[];
  warnings: string[];
}
export interface ReviewTransaction { id: string; date: string; description: string; amount: number; balance: number | null; status: 'REVIEW_REQUIRED' | 'CONFIRMED'; }
export interface ImportReview { importId: string; originalFilename: string; status: 'REVIEW_REQUIRED' | 'CONFIRMED'; transactions: ReviewTransaction[]; }
export interface ImportHistoryItem {
  importId: string;
  originalFilename: string;
  source: 'MANUAL' | 'WATCHED_FOLDER';
  status: 'REVIEW_REQUIRED' | 'CONFIRMED';
  accountName: string | null;
  transactionCount: number;
  importedAt: string;
}
export interface IncomingStatementItem {
  filename: string;
  sizeBytes: number;
  modifiedAt: string;
  status: 'AWAITING_PROCESSING' | 'NEEDS_ATTENTION' | 'UNSUPPORTED' | 'FAILED';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ImportsApiService {
  private readonly http = inject(HttpClient);

  upload(statement: File): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('statement', statement);
    return this.http.post<ImportResult>('http://localhost:8080/api/imports', formData);
  }
  history() { return this.http.get<ImportHistoryItem[]>('http://localhost:8080/api/imports'); }
  incoming() { return this.http.get<IncomingStatementItem[]>('http://localhost:8080/api/automation/incoming'); }
  review(importId: string) { return this.http.get<ImportReview>(`http://localhost:8080/api/imports/${importId}`); }
  updateTransaction(importId: string, row: ReviewTransaction) { return this.http.patch<ReviewTransaction>(`http://localhost:8080/api/imports/${importId}/transactions/${row.id}`, { date: row.date, description: row.description, amount: row.amount, balance: row.balance }); }
  deleteTransaction(importId: string, transactionId: string) { return this.http.delete(`http://localhost:8080/api/imports/${importId}/transactions/${transactionId}`); }
  confirm(importId: string) { return this.http.post<ImportReview>(`http://localhost:8080/api/imports/${importId}/confirm`, {}); }
}
