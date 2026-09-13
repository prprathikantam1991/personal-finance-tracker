import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { FinanceTransaction } from './transaction';

@Injectable({ providedIn: 'root' })
export class TransactionsApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/transactions';

  list(accountId: string, from: string, to: string) {
    let params = new HttpParams();
    if (accountId) params = params.set('accountId', accountId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<FinanceTransaction[]>(this.apiUrl, { params });
  }

  updateCategory(transactionId: string, category: string, rememberForFuture: boolean) {
    return this.http.patch<FinanceTransaction>(`${this.apiUrl}/${transactionId}/category`, { category, rememberForFuture });
  }
}
