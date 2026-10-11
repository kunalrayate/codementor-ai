import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export const API = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class Api {
  constructor(private http: HttpClient) {}
  get<T = any>(path: string) { return this.http.get<T>(API + path); }
  post<T = any>(path: string, body: any = {}) { return this.http.post<T>(API + path, body); }
  put<T = any>(path: string, body: any = {}) { return this.http.put<T>(API + path, body); }
  patch<T = any>(path: string, body: any = {}) { return this.http.patch<T>(API + path, body); }
  delete<T = any>(path: string) { return this.http.delete<T>(API + path); }
  blob(path: string) { return this.http.get(API + path, { responseType: 'blob' }); }
  upload<T = any>(path: string, form: FormData) { return this.http.post<T>(API + path, form); }
  msg(e: any): string { return e?.error?.error || 'Something went wrong'; }
}
