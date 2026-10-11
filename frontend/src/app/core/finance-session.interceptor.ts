import { HttpInterceptorFn } from '@angular/common/http';

/** Sends the opaque Finance Tracker cookie, never an application user ID or a bearer token. */
export const financeSessionInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.url.startsWith('http://localhost:8080/')) {
    return next(request.clone({ withCredentials: true }));
  }
  return next(request);
};
