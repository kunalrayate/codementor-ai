import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../core/api.service';

@Component({
  selector: 'app-quizzes',
  standalone: true,
  imports: [RouterLink],
  template: `
    <h2>📝 My Quizzes</h2>
    <p class="muted">Quizzes set to <b>single attempt</b> can be taken only once per topic. After you complete one it is <b>locked</b>.</p>
    @for (g of groups; track g.course) {
      <h3 style="margin-top:22px">{{ g.course }}</h3>
      <div class="grid">
        @for (q of g.rows; track q.quizId) {
          <div class="card" [class.locked]="q.locked">
            <h3>{{ q.topic }} Quiz</h3>
            <p class="muted" style="margin:0 0 8px">{{ q.module }}</p>
            <div>Status: <b>{{ q.status }}</b></div>
            <div>Attempt: <b>{{ q.attemptsUsed }}/{{ q.maxAttempts ?? '∞' }}</b></div>
            <div>Access: <b>{{ q.locked ? '🔒 Locked' : '🔓 Open' }}</b></div>
            @if (q.lastPercent !== null) { <div>Score: <b>{{ q.lastPercent }}%</b></div> }
            <div style="margin-top:12px">
              @if (q.locked) { <button disabled>🔒 Locked</button> }
              @else { <a class="btn" [routerLink]="['/quiz', q.quizId]">{{ q.attemptsUsed > 0 ? 'Retake' : (q.status === 'In progress' ? 'Continue' : 'Start quiz') }}</a> }
            </div>
          </div>
        }
      </div>
    }
    @if (loaded && !groups.length) { <div class="card"><p class="muted">No published quizzes yet. Enroll in a course first.</p><a class="btn" routerLink="/courses">Browse courses</a></div> }
    @if (err) { <div class="err">{{ err }}</div> }`
})
export class QuizzesComponent implements OnInit {
  groups: { course: string; rows: any[] }[] = []; loaded = false; err = '';
  constructor(private api: Api) {}
  ngOnInit() {
    this.api.get('/me/quizzes').subscribe({
      next: rows => {
        const map = new Map<string, any[]>();
        for (const r of rows) { if (!map.has(r.course)) map.set(r.course, []); map.get(r.course)!.push(r); }
        this.groups = [...map.entries()].map(([course, rows]) => ({ course, rows })); this.loaded = true; },
      error: e => this.err = this.api.msg(e) });
  }
}
