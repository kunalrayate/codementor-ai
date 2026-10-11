import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { API } from './api.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  user = signal<any>(JSON.parse(localStorage.getItem('user') || 'null'));
  constructor(private http: HttpClient, private router: Router) {}

  get accessToken() { return localStorage.getItem('at'); }
  get refreshToken() { return localStorage.getItem('rt'); }

  private store(r: any) {
    localStorage.setItem('at', r.accessToken);
    localStorage.setItem('rt', r.refreshToken);
    localStorage.setItem('user', JSON.stringify(r.user));
    this.user.set(r.user);
  }
  login(email: string, password: string) { return this.http.post<any>(`${API}/auth/login`, { email, password }).pipe(tap(r => this.store(r))); }
  register(body: any) { return this.http.post<any>(`${API}/auth/register`, body).pipe(tap(r => this.store(r))); }
  refresh() { return this.http.post<any>(`${API}/auth/refresh`, { refreshToken: this.refreshToken }).pipe(tap(r => this.store(r))); }
  logout() {
    const rt = this.refreshToken;
    if (rt) this.http.post(`${API}/auth/logout`, { refreshToken: rt }).subscribe({ error: () => {} });
    ['at', 'rt', 'user'].forEach(k => localStorage.removeItem(k)); // keep the theme choice
    this.user.set(null);
    this.router.navigate(['/login']);
  }
  hasRole(...roles: string[]) { return roles.includes(this.user()?.role); }
}
