import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-course-detail',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    @if (d) {
      <h2>{{ d.course.title }}</h2><p class="muted">{{ d.course.description }}</p>
      <div class="row">
        <a class="btn secondary" [routerLink]="['/leaderboard', d.course.id]">Course leaderboard</a>
        @if (d.canEdit) { <button class="secondary" (click)="loadAnalytics()">Student analytics</button> <button class="secondary" (click)="setAttempts(1)">🔒 Single attempt for all quizzes</button> <button class="secondary" (click)="setAttempts(0)">🔓 Unlimited attempts</button> <button class="secondary" (click)="renameCourse()">Rename course</button> <button class="danger" (click)="deleteCourse()">Delete course</button> }
        @if (!d.enrolled) { <button (click)="enroll()">Enroll</button> }
      </div>
      @if (prog) { <div class="card"><b>Progress: {{ prog.completed }}/{{ prog.total }} lessons ({{ prog.percent }}%)</b>
        <div class="bar"><div [style.width.%]="prog.percent"></div></div></div> }
      @if (analytics) { <div class="card"><h3>Student analytics</h3><table>
        <tr><th>Student</th><th>XP</th><th>Completion</th><th>Avg quiz</th></tr>
        @for (a of analytics; track a.studentId) { <tr><td>{{ a.name }}</td><td>{{ a.xp }}</td><td>{{ a.completedLessons }}/{{ a.totalLessons }} ({{ a.completionPercent }}%)</td><td>{{ a.avgQuizPercent ?? '-' }}</td></tr> }
      </table></div> }
      @for (m of d.modules; track m.id) {
        <div class="card"><div class="row" style="justify-content:space-between"><h3>{{ m.position }}. {{ m.title }}</h3>@if (d.canEdit) { <span><button class="secondary" (click)="renameModule(m)">Rename</button> <button class="danger" (click)="deleteModule(m)">Delete</button></span> }</div>
          @for (l of m.lessons; track l.id) {
            <div class="row" style="padding:4px 0">
              <span>{{ done(l.id) ? '✅' : '▶️' }}</span>
              @if (d.enrolled) { <a [routerLink]="['/lessons', l.id]">{{ l.title }}</a> } @else { <span>{{ l.title }}</span> }
            </div>
          }
          @if (d.canEdit) {
            @if (newLesson[m.id]) {
              <div style="border-top:1px dashed var(--border);padding-top:10px;margin-top:8px">
                <b>➕ Add a lesson</b>
                <input placeholder="Lesson title (e.g. Method overloading)" [(ngModel)]="newLesson[m.id].title" />
                <div class="row"><input placeholder="Video link (YouTube / Vimeo / .mp4) for this topic" [(ngModel)]="newLesson[m.id].videoUrl" style="flex:1" />
                  <a class="btn secondary" [href]="searchLink(newLesson[m.id].title)" target="_blank" rel="noopener">🔎 Find video on YouTube</a></div>
                <textarea rows="2" placeholder="Short lesson notes (optional - used by the AI tools and offline tutor)" [(ngModel)]="newLesson[m.id].content"></textarea>
                <button (click)="addLesson(m.id)">Add lesson</button>
              </div>
            }
          }
        </div>
      }
      @if (d.canEdit) {
        <div class="card"><h3>Add module</h3><input placeholder="Module title" [(ngModel)]="moduleTitle" />
          <button (click)="addModule()">Add module</button>
          <p class="muted">After adding a lesson, open it to set the video URL and lesson content.</p></div>
      }
      @if (qMsg) { <div class="okc">{{ qMsg }}</div> }
      @if (err) { <div class="err">{{ err }}</div> }
    }`
})
export class CourseDetailComponent implements OnInit {
  d: any; qMsg = ''; prog: any; analytics: any; moduleTitle = ''; newLesson: any = {}; err = '';
  id = 0;
  constructor(private route: ActivatedRoute, private api: Api, public auth: AuthService) {}
  ngOnInit() { this.id = +this.route.snapshot.paramMap.get('id')!; this.load(); }
  load() {
    this.api.get(`/courses/${this.id}`).subscribe(d => {
      this.d = d;
      for (const m of d.modules) if (!this.newLesson[m.id]) this.newLesson[m.id] = { title: '', videoUrl: '', content: '' };
      if (this.auth.hasRole('STUDENT') && d.enrolled) this.api.get(`/courses/${this.id}/progress`).subscribe(p => this.prog = p);
    });
  }
  done(lessonId: number) { return !!this.prog?.lessons.find((l: any) => l.lessonId === lessonId && l.completed); }
  enroll() { this.api.post(`/courses/${this.id}/enroll`).subscribe(() => this.load()); }
  addModule() { this.api.post(`/courses/${this.id}/modules`, { title: this.moduleTitle }).subscribe(() => { this.moduleTitle = ''; this.load(); }); }
  searchLink(title: string) { return 'https://www.youtube.com/results?search_query=' + encodeURIComponent('Java ' + (title || '') + ' tutorial'); }
  addLesson(mid: number) {
    this.err = '';
    const f = this.newLesson[mid];
    if (!f.title?.trim()) { this.err = 'Enter a lesson title.'; return; }
    const url = (f.videoUrl || '').trim();
    if (url && !/^https?:\/\//i.test(url)) { this.err = 'The video link must start with http:// or https://'; return; }
    if (!url && !confirm('No video link entered for "' + f.title + '". Add the lesson without a video?')) return;
    this.api.post(`/modules/${mid}/lessons`, { title: f.title.trim(), description: '', videoUrl: url, content: f.content || '' }).subscribe({
      next: () => { this.newLesson[mid] = { title: '', videoUrl: '', content: '' }; this.load(); }, error: e => this.err = this.api.msg(e) });
  }
  renameCourse() { const t = prompt('New course title', this.d.course.title); if (t) this.api.put(`/courses/${this.id}`, { title: t, description: this.d.course.description }).subscribe(() => this.load()); }
  deleteCourse() { if (confirm('Delete this course with all its modules and lessons?')) this.api.delete(`/courses/${this.id}`).subscribe(() => history.back()); }
  renameModule(m: any) { const t = prompt('Module title', m.title); if (t) this.api.put(`/modules/${m.id}`, { title: t, position: m.position }).subscribe(() => this.load()); }
  deleteModule(m: any) { if (confirm('Delete module "' + m.title + '" and its lessons?')) this.api.delete(`/modules/${m.id}`).subscribe(() => this.load()); }
  setAttempts(max: number) {
    const text = max === 1 ? 'Make EVERY quiz in this course single-attempt (a student can take each topic quiz only once)?' : 'Allow unlimited attempts on every quiz in this course?';
    if (!confirm(text)) return;
    this.api.put(`/courses/${this.id}/quizzes/attempts`, { maxAttempts: max || null }).subscribe({
      next: r => this.qMsg = `✅ Updated ${r.updated} quiz(zes): ${max === 1 ? 'single attempt per topic' : 'unlimited attempts'}.`, error: e => this.err = this.api.msg(e) });
  }
  loadAnalytics() { this.api.get(`/courses/${this.id}/analytics`).subscribe(a => this.analytics = a); }
}
