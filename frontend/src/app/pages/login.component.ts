import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="card" style="max-width:440px;margin:50px auto">
      <h2>{{ mode === 'login' ? 'Login' : 'Create account' }}</h2>
      @if (mode === 'register') {
        <input placeholder="Full name" [(ngModel)]="f.name" />
        <select [(ngModel)]="f.role"><option value="STUDENT">Student</option><option value="INSTRUCTOR">Instructor</option></select>
      }
      <input placeholder="Email" type="email" [(ngModel)]="f.email" />
      <input placeholder="Password (min 6)" [type]="show ? 'text' : 'password'" [(ngModel)]="f.password" (keyup.enter)="submit()" />
      <label class="row muted"><input type="checkbox" [(ngModel)]="show" style="width:auto;margin:0" /> Show password</label>
      @if (error) { <div class="err">{{ error }}</div> }
      <div class="row">
        <button (click)="submit()">{{ mode === 'login' ? 'Login' : 'Register' }}</button>
        <button class="secondary" (click)="mode = mode === 'login' ? 'register' : 'login'">{{ mode === 'login' ? 'Need an account?' : 'Have an account?' }}</button>
      </div>
      @if (mode === 'login') {
        <p class="muted" style="margin-top:16px">Demo accounts - click to fill:</p>
        <div class="row">
          <button class="secondary" (click)="fill('student@learnai.com','Student@123')">Student</button>
          <button class="secondary" (click)="fill('instructor@learnai.com','Instructor@123')">Instructor</button>
          <button class="secondary" (click)="fill('admin@learnai.com','Admin@123')">Admin</button>
        </div>
      }
    </div>`
})
export class LoginComponent {
  mode = 'login'; show = false;
  f = { name: '', email: '', password: '', role: 'STUDENT' };
  error = '';
  constructor(private auth: AuthService, private router: Router) {}
  fill(email: string, password: string) { this.f.email = email; this.f.password = password; this.error = ''; }
  submit() {
    const obs = this.mode === 'login' ? this.auth.login(this.f.email, this.f.password) : this.auth.register(this.f);
    obs.subscribe({ next: () => this.router.navigate(['/']), error: e => this.error = e?.error?.error || 'Cannot reach the backend on http://localhost:8080' });
  }
}
