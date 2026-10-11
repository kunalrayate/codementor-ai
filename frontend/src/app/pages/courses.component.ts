import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    <div class="row" style="justify-content:space-between"><h2>📚 Courses</h2>
      @if (auth.hasRole('INSTRUCTOR', 'ADMIN')) { <button (click)="showNew = !showNew">{{ showNew ? '✕ Close' : '➕ New course' }}</button> }</div>
    @if (showNew) {
      <div class="card"><h3>Create a course</h3>
        <input placeholder="Course title" [(ngModel)]="title" />
        <textarea rows="3" placeholder="Description" [(ngModel)]="description"></textarea>
        <button (click)="create()">Create course</button></div>
    }
    @if (msg) { <div [class.okc]="ok" [class.err]="!ok">{{ msg }}</div> }
    <div class="grid">
      @for (c of courses; track c.id) {
        <div class="card">
          @if (editId === c.id) {
            <h3>Edit course</h3>
            <input placeholder="Title" [(ngModel)]="ef.title" />
            <textarea rows="3" placeholder="Description" [(ngModel)]="ef.description"></textarea>
            <div class="row"><button (click)="save(c)">Save</button><button class="secondary" (click)="editId = null">Cancel</button></div>
          } @else {
            <h3>{{ c.title }}</h3><p class="muted">{{ c.description }}</p><p class="muted">By {{ c.instructor }}</p>
            <div class="row">
              <a class="btn" [routerLink]="['/courses', c.id]">Open</a>
              @if (auth.hasRole('STUDENT')) { <button class="secondary" (click)="enroll(c.id)">Enroll</button> }
              @if (canEdit(c)) { <button class="secondary" (click)="startEdit(c)">✏ Edit</button><button class="danger" (click)="remove(c)">🗑 Delete</button> }
            </div>
          }
        </div>
      }
    </div>`
})
export class CoursesComponent implements OnInit {
  courses: any[] = []; title = ''; description = ''; msg = ''; ok = true; showNew = false; editId: number | null = null; ef: any = {};
  constructor(private api: Api, public auth: AuthService) {}
  ngOnInit() { this.load(); }
  load() { this.api.get('/courses').subscribe(c => this.courses = c); }
  canEdit(c: any) { return this.auth.hasRole('ADMIN') || (this.auth.hasRole('INSTRUCTOR') && c.instructorId === this.auth.user()?.id); }
  private say(ok: boolean, msg: string) { this.ok = ok; this.msg = msg; }
  create() {
    if (!this.title.trim()) { this.say(false, 'Enter a course title.'); return; }
    this.api.post('/courses', { title: this.title.trim(), description: this.description }).subscribe({
      next: () => { this.title = this.description = ''; this.showNew = false; this.say(true, '✅ Course created. Open it to add modules and lessons.'); this.load(); },
      error: e => this.say(false, this.api.msg(e)) });
  }
  startEdit(c: any) { this.editId = c.id; this.ef = { title: c.title, description: c.description }; this.msg = ''; }
  save(c: any) {
    if (!this.ef.title?.trim()) { this.say(false, 'The title cannot be empty.'); return; }
    this.api.put(`/courses/${c.id}`, { title: this.ef.title.trim(), description: this.ef.description }).subscribe({
      next: () => { this.editId = null; this.say(true, '✅ Course updated.'); this.load(); }, error: e => this.say(false, this.api.msg(e)) });
  }
  remove(c: any) {
    if (!confirm(`Delete the course "${c.title}" with all its modules, lessons and quizzes? This cannot be undone.`)) return;
    this.api.delete(`/courses/${c.id}`).subscribe({ next: () => { this.say(true, '🗑 Course deleted.'); this.load(); }, error: e => this.say(false, this.api.msg(e)) });
  }
  enroll(id: number) { this.api.post(`/courses/${id}/enroll`).subscribe(() => this.say(true, '✅ Enrolled! Open the course to start learning.')); }
}
