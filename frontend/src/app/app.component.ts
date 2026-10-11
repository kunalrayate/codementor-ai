import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { Api } from './core/api.service';
import { APP_VERSION } from './core/version';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (notice()) { <div class="versionbar">{{ notice() }}</div> }
    @if (auth.user(); as u) {
      <div class="layout">
        <aside class="sidebar">
          <div class="brand">🎓 <span>LearnAI</span></div>
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">🏠 Dashboard</a>
          <a routerLink="/courses" routerLinkActive="active">📚 Courses</a>
          @if (u.role === 'STUDENT') { <a routerLink="/quizzes" routerLinkActive="active">📝 My Quizzes</a> }
          <a routerLink="/tutor" routerLinkActive="active">🤖 AI Tutor</a>
          <a routerLink="/code" routerLinkActive="active">💻 Code Practice</a>
          @if (u.role !== 'INSTRUCTOR') { <a routerLink="/leaderboard" routerLinkActive="active">🏆 Leaderboard</a> }
          @if (u.role === 'ADMIN') { <a routerLink="/admin" routerLinkActive="active">⚙️ Admin</a> }
          <div class="spacer"></div>
          <div class="usercard"><div class="avatar">{{ initials(u.name) }}</div><div><b>{{ u.name }}</b><small>{{ u.role }}</small></div></div>
        </aside>
        <div class="main">
          <header class="topbar">
            <button class="secondary" (click)="toggleTheme()">{{ dark() ? '☀️ Light mode' : '🌙 Dark mode' }}</button>
            <button class="secondary" (click)="auth.logout()">Logout</button>
          </header>
          <div class="container"><router-outlet /></div>
        </div>
      </div>
    } @else {
      <div class="auth-bg"><div class="container"><router-outlet /></div></div>
    }`
})
export class AppComponent {
  dark = signal(localStorage.getItem('theme') === 'dark');
  notice = signal('');
  constructor(public auth: AuthService, api: Api) {
    this.apply();
    api.get('/auth/version').subscribe({
      next: v => { if (v.version !== APP_VERSION) this.notice.set(`⚠ This page is version ${APP_VERSION} but the backend is version ${v.version}. Stop both, then start the backend and the frontend from the SAME latest folder (run start-all.bat) and press Ctrl+F5.`); },
      error: e => this.notice.set(e.status === 0
        ? '⚠ Cannot reach the backend on http://localhost:8080. Start it first (run-backend.bat) and wait for "Started Application".'
        : '⚠ The backend is older than this page. Stop it and start the latest backend (run start-all.bat), then press Ctrl+F5.') });
  }
  toggleTheme() { this.dark.set(!this.dark()); localStorage.setItem('theme', this.dark() ? 'dark' : 'light'); this.apply(); }
  private apply() { document.documentElement.setAttribute('data-theme', this.dark() ? 'dark' : 'light'); }
  initials(name: string) { return (name || '?').split(' ').map(x => x[0]).slice(0, 2).join('').toUpperCase(); }
}
