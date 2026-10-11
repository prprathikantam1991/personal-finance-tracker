import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

interface FinanceSessionStatus { authenticationEnabled: boolean; authenticated: boolean; csrfToken: string | null; }

@Injectable({ providedIn: 'root' })
export class FinanceAuthService {
  readonly authenticationEnabled = signal(false);
  readonly authenticated = signal(false);
  readonly statusLoaded = signal(false);
  readonly csrfToken = signal<string | null>(null);

  constructor(private readonly http: HttpClient) {}

  loadStatus(): void {
    this.http.get<FinanceSessionStatus>('http://localhost:8080/api/auth/session').subscribe({
      next: status => {
        this.authenticationEnabled.set(status.authenticationEnabled);
        this.authenticated.set(status.authenticated);
        this.csrfToken.set(status.authenticated ? status.csrfToken : null);
        this.statusLoaded.set(true);
      },
      error: () => {
        this.authenticationEnabled.set(true);
        this.authenticated.set(false);
        this.csrfToken.set(null);
        this.statusLoaded.set(true);
      },
    });
  }

  signIn(): void {
    const returnUrl = `${window.location.origin}/dashboard`;
    window.location.assign(`http://localhost:8081/auth/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  }

  signOut(): void {
    // Hide protected screens immediately. A subsequent status check will restore
    // access only if server-side logout did not actually invalidate the session.
    this.authenticated.set(false);
    this.csrfToken.set(null);
    this.http.post('http://localhost:8080/api/auth/logout', {}).subscribe({
      next: () => undefined,
      error: () => undefined,
    });
  }

  markUnauthenticated(): void {
    this.authenticationEnabled.set(true);
    this.authenticated.set(false);
    this.csrfToken.set(null);
    this.statusLoaded.set(true);
  }
}
