import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { FinanceAuthService } from './finance-auth.service';

/** Sends the opaque Finance Tracker cookie, never an application user ID or a bearer token. */
export const financeSessionInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(FinanceAuthService);
  if (request.url.startsWith('http://localhost:8080/')) {
    return next(request.clone({ withCredentials: true })).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) auth.markUnauthenticated();
        return throwError(() => error);
      }),
    );
  }
  return next(request);
};
