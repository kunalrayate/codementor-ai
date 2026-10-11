import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const withToken = (r: HttpRequest<any>) =>
    auth.accessToken ? r.clone({ setHeaders: { Authorization: `Bearer ${auth.accessToken}` } }) : r;

  if (req.url.includes('/auth/')) return next(req);
  return next(withToken(req)).pipe(
    catchError(err => {
      // access token expired -> use the refresh token once, then retry
      if (err.status === 401 && auth.refreshToken) {
        return auth.refresh().pipe(
          switchMap(() => next(withToken(req))),
          catchError(e => { auth.logout(); return throwError(() => e); })
        );
      }
      return throwError(() => err);
    })
  );
};
