import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Account } from './account';

@Injectable({ providedIn: 'root' })
export class AccountsApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/accounts';

  list() {
    return this.http.get<Account[]>(`${this.apiUrl}/overview`);
  }
}
