import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface FinanceSessionStatus { authenticationEnabled: boolean; authenticated: boolean; }

@Injectable({ providedIn: 'root' })
export class FinanceAuthService {
  readonly authenticationEnabled = signal(false);
  readonly authenticated = signal(true);

  constructor(private readonly http: HttpClient) {}

  loadStatus(): void {
    this.http.get<FinanceSessionStatus>('http://localhost:8080/api/auth/session').subscribe({
      next: status => {
        this.authenticationEnabled.set(status.authenticationEnabled);
        this.authenticated.set(status.authenticated);
      },
      error: () => {
        this.authenticationEnabled.set(true);
        this.authenticated.set(false);
      },
    });
  }

  signIn(): void {
    const returnUrl = `${window.location.origin}/dashboard`;
    window.location.assign(`http://localhost:8081/auth/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  }

  signOut(): void {
    this.http.post('http://localhost:8080/api/auth/logout', {}).subscribe({
      next: () => this.authenticated.set(false),
      error: () => this.authenticated.set(false),
    });
  }
}
