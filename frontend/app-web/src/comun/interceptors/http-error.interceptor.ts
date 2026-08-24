import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable()
export class HttpErrorInterceptor implements HttpInterceptor {
  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 403) {
          // Captura error 403 Forbidden y muestra el mensaje amigable de HU-RBAC-004
          const detail = error.error?.detail || 'No tienes permiso para esta acción';
          alert(detail);
        }
        return throwError(() => error);
      })
    );
  }
}
