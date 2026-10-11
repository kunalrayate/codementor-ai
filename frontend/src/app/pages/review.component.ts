import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';

@Component({
  selector: 'app-review',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    @if (quiz) {
      <h2>Review: {{ quiz.title }}</h2>
      <div class="row"><span class="badge">{{ quiz.status }}</span>@if (quiz.aiGenerated) { <span class="badge">AI generated</span> }
        <button (click)="setStatus('PUBLISHED')">✅ Publish</button>
        <button class="secondary" (click)="setStatus('DRAFT')">Back to draft</button>
        <button class="secondary" (click)="setStatus('ARCHIVED')">Archive</button>
        <a class="btn secondary" [routerLink]="['/lessons', quiz.lessonId]">Back to lesson</a></div>
      <p class="muted">Only PUBLISHED quizzes are visible to students. Check every question and the correct answer before publishing.</p>
      <div class="card"><h3>🔒 Attempts per student</h3>
        <div class="row"><select [(ngModel)]="policy" style="max-width:380px">
            <option [ngValue]="0">Unlimited attempts (fresh random questions each time)</option>
            <option [ngValue]="1">Single attempt per topic - locks after completion</option>
            <option [ngValue]="2">2 attempts</option><option [ngValue]="3">3 attempts</option></select>
          <button (click)="savePolicy()">Save</button></div>
        <p class="muted">With a limit, a student's quiz shows Status / Attempt (e.g. 1/1) / Access: Locked once the limit is reached. You can allow a retake for a single student below.</p>
        <b>Student results</b>
        <table><tr><th>Student</th><th>Score</th><th>Result</th><th>Date</th><th></th></tr>
          @for (r of results; track $index) { <tr><td>{{ r.student }}</td><td>{{ r.score }}/{{ r.total }} ({{ r.percent }}%)</td><td>{{ r.passed ? 'Passed' : 'Not passed' }}</td><td>{{ r.date }}</td>
            <td><button class="secondary" (click)="retake(r)">Allow retake</button></td></tr> }
        </table>
        @if (!results.length) { <span class="muted">No student has completed this quiz yet.</span> }
      </div>
      @for (q of quiz.questions; track q.id) {
        <div class="card">
          <textarea rows="2" [(ngModel)]="q.text"></textarea>
          @for (o of q.options; track $index) { <input [(ngModel)]="q.options[$index]" [placeholder]="'Option ' + letters[$index]" /> }
          <div class="row"><label>Correct answer</label>
            <select [(ngModel)]="q.correct" style="width:80px"><option>A</option><option>B</option><option>C</option><option>D</option></select></div>
          <input [(ngModel)]="q.explanation" placeholder="Explanation" />
          <button (click)="saveQ(q)">Save question</button> <button class="danger" (click)="delQ(q)">Delete</button>
        </div>
      }
      @if (msg) { <div class="okc">{{ msg }}</div> } @if (err) { <div class="err">{{ err }}</div> }
    }`
})
export class ReviewComponent implements OnInit {
  quiz: any; policy = 0; results: any[] = []; msg = ''; err = ''; letters = ['A', 'B', 'C', 'D']; id = 0;
  constructor(private route: ActivatedRoute, private api: Api) {}
  ngOnInit() { this.id = +this.route.snapshot.paramMap.get('id')!; this.load(); }
  load() {
    this.api.get(`/quizzes/${this.id}`).subscribe(q => { this.quiz = q; this.policy = q.maxAttempts || 0; });
    this.loadResults();
  }
  loadResults() { this.api.get(`/quizzes/${this.id}/results`).subscribe(r => this.results = r); }
  savePolicy() {
    this.api.patch(`/quizzes/${this.id}/attempts`, { maxAttempts: this.policy || null }).subscribe({
      next: q => { this.quiz = q; this.msg = this.policy === 1 ? '✅ Saved: single attempt per topic.' : this.policy ? `✅ Saved: ${this.policy} attempts.` : '✅ Saved: unlimited attempts.'; this.err = ''; },
      error: e => this.err = this.api.msg(e) });
  }
  retake(r: any) {
    if (!confirm(`Allow ${r.student} to take this quiz again? Their previous result for this quiz will be removed.`)) return;
    this.api.delete(`/quizzes/${this.id}/attempts/${r.studentId}`).subscribe({ next: () => { this.msg = '✅ Retake allowed for ' + r.student + '.'; this.loadResults(); }, error: e => this.err = this.api.msg(e) });
  }
  setStatus(status: string) { this.api.patch(`/quizzes/${this.id}/status`, { status }).subscribe({ next: q => { this.quiz = q; this.msg = 'Status: ' + status; }, error: e => this.err = this.api.msg(e) }); }
  saveQ(q: any) { this.api.put(`/questions/${q.id}`, { text: q.text, options: q.options, correct: q.correct, explanation: q.explanation }).subscribe({ next: () => this.msg = 'Saved', error: e => this.err = this.api.msg(e) }); }
  delQ(q: any) { this.api.delete(`/questions/${q.id}`).subscribe(() => this.load()); }
}
