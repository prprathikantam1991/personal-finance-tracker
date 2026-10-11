import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface FinanceSessionStatus { authenticationEnabled: boolean; authenticated: boolean; csrfToken: string | null; }
export interface FinanceUserProfile { name: string | null; email: string | null; picture: string | null; }

@Injectable({ providedIn: 'root' })
export class FinanceAuthService {
  readonly authenticationEnabled = signal(false);
  readonly authenticated = signal(false);
  readonly statusLoaded = signal(false);
  readonly csrfToken = signal<string | null>(null);
  readonly profile = signal<FinanceUserProfile | null>(null);

  constructor(private readonly http: HttpClient) {}

  loadStatus(): void {
    this.http.get<FinanceSessionStatus>('http://localhost:8080/api/auth/session').subscribe({
      next: status => {
        this.authenticationEnabled.set(status.authenticationEnabled);
        this.authenticated.set(status.authenticated);
        this.csrfToken.set(status.authenticated ? status.csrfToken : null);
        if (status.authenticated) this.loadProfile();
        else this.profile.set(null);
        this.statusLoaded.set(true);
      },
      error: () => {
        this.authenticationEnabled.set(true);
        this.authenticated.set(false);
        this.csrfToken.set(null);
        this.profile.set(null);
        this.statusLoaded.set(true);
      },
    });
  }

  signIn(): void {
    const returnUrl = `${window.location.origin}/dashboard`;
    window.location.assign(`http://localhost:8081/auth/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  }

  signOut(): void {
    // Preserve the synchronizer token for this one protected request. Clearing it
    // before the request reaches the API would make the CSRF filter reject logout
    // and leave the server-side session usable after a browser reload.
    const csrfToken = this.csrfToken();
    this.authenticated.set(false);
    this.csrfToken.set(null);
    this.profile.set(null);
    const options = csrfToken
      ? { headers: new HttpHeaders().set('X-Finance-CSRF', csrfToken) }
      : {};
    this.http.post('http://localhost:8080/api/auth/logout', {}, options).subscribe({
      next: () => undefined,
      error: () => undefined,
    });
  }

  markUnauthenticated(): void {
    this.authenticationEnabled.set(true);
    this.authenticated.set(false);
    this.csrfToken.set(null);
    this.profile.set(null);
    this.statusLoaded.set(true);
  }

  private loadProfile(): void {
    this.http.get<FinanceUserProfile>('http://localhost:8081/auth/profile', { withCredentials: true }).subscribe({
      next: profile => this.profile.set(profile),
      error: () => this.profile.set(null),
    });
  }
}
