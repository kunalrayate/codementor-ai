import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../core/api.service';

@Component({
  selector: 'app-quiz',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (info && !attempt && !result) {
      @if (info.locked) {
        <div class="card" style="max-width:640px">
          <h2>🔒 Quiz locked</h2>
          <h3>{{ info.title }}</h3>
          <p>Status: <b>Completed</b></p>
          <p>Attempt: <b>{{ info.attemptsUsed }}/{{ info.maxAttempts }}</b></p>
          <p>Access: <b>Locked</b></p>
          @if (info.lastPercent !== null) { <p>Your score: <b>{{ info.lastPercent }}%</b></p> }
          <p class="muted">This quiz can be attempted only {{ info.maxAttempts === 1 ? 'once' : info.maxAttempts + ' times' }}. Ask your instructor if you need another attempt.</p>
          <a class="btn" [routerLink]="['/lessons', info.lessonId]">Back to lesson</a>
        </div>
      } @else {
        <div class="card" style="max-width:640px">
          <h2>📝 {{ info.title }}</h2>
          <p>Attempt: <b>{{ info.attemptsUsed }}/{{ info.maxAttempts ?? '∞' }}</b> · Access: <b>🔓 Open</b></p>
          @if (info.maxAttempts === 1) {
            <div class="warn">⚠ <b>Single attempt.</b> You can take this quiz only once. After you submit it, the quiz is locked and cannot be attempted again.</div>
          } @else if (info.maxAttempts) {
            <div class="warn">You can attempt this quiz {{ info.maxAttempts }} times in total. After that it is locked.</div>
          } @else {
            <p class="muted">Every attempt uses a fresh random set of questions and shuffled answers.</p>
          }
          <div class="row"><button (click)="start()" [disabled]="busy">{{ info.attemptsUsed > 0 ? 'Start another attempt' : 'Start quiz' }}</button>
            <a class="btn secondary" [routerLink]="['/lessons', info.lessonId]">Back to lesson</a></div>
        </div>
      }
    }
    @if (attempt) {
      <h2>📝 {{ attempt.title }}</h2>
      @if (!result) {
        <p class="muted">{{ attempt.questions.length }} questions, {{ answeredCount() }} answered.
          @if (info?.maxAttempts === 1) { <b> Single attempt - the quiz locks after you submit.</b> }</p>
      }
      @if (result) {
        <div class="card"><h3 [class.okc]="result.passed" [class.err]="!result.passed">{{ result.score }}/{{ result.total }} ({{ result.percent }}%) {{ result.passed ? '- Passed 🎉' : '- Not passed yet, review the answers below' }}</h3>
          @if (result.xpAwarded) { <p class="okc">+{{ result.xpAwarded }} XP</p> }
          @if (info?.locked) {
            <p>Status: <b>Completed</b> · Attempt: <b>{{ info.attemptsUsed }}/{{ info.maxAttempts }}</b> · Access: <b>🔒 Locked</b></p>
            <div class="row"><a class="btn" [routerLink]="['/lessons', attempt.lessonId]">Back to lesson</a><a class="btn secondary" routerLink="/quizzes">My quizzes</a></div>
          } @else {
            <div class="row"><button (click)="again()">🔁 Try again with new questions</button>
              <a class="btn secondary" [routerLink]="['/lessons', attempt.lessonId]">Back to lesson</a></div>
          }
        </div>
      }
      @for (q of attempt.questions; track q.index) {
        <div class="card"><b>{{ q.index + 1 }}. {{ q.text }}</b>
          @for (o of q.options; track $index) {
            <label class="opt" [class.sel]="answers[q.index] === o && !result"
                   [class.right]="result && detail(q.index)?.correctAnswer === o"
                   [class.wrong]="result && answers[q.index] === o && !detail(q.index)?.correct" (click)="pick(q.index, o)">
              {{ letters[$index] }}. {{ o }}</label>
          }
          @if (result && detail(q.index)?.explanation) { <p class="muted">💡 {{ detail(q.index).explanation }}</p> }
          @if (result && !answers[q.index]) { <p class="err">Not answered.</p> }
        </div>
      }
      @if (!result) { <button (click)="submit()" [disabled]="busy">{{ busy ? 'Checking…' : 'Submit answers' }}</button> }
    }
    @if (err) { <div class="err">{{ err }}</div> }`
})
export class QuizComponent implements OnInit {
  info: any; attempt: any; answers: any = {}; result: any; err = ''; busy = false; letters = ['A', 'B', 'C', 'D']; quizId = 0;
  constructor(private route: ActivatedRoute, private api: Api) {}
  ngOnInit() { this.quizId = +this.route.snapshot.paramMap.get('id')!; this.loadInfo(); }
  loadInfo() { this.api.get(`/quizzes/${this.quizId}`).subscribe({ next: q => this.info = q, error: e => this.err = this.api.msg(e) }); }
  start() {
    this.busy = true; this.err = ''; this.result = null; this.answers = {};
    this.api.post(`/quizzes/${this.quizId}/start`).subscribe({
      next: a => { this.attempt = a; this.busy = false; window.scrollTo({ top: 0, behavior: 'smooth' }); },
      error: e => { this.busy = false; this.err = this.api.msg(e); this.loadInfo(); } });
  }
  again() { this.attempt = null; this.result = null; this.answers = {}; this.start(); }
  pick(i: number, option: string) { if (!this.result) this.answers[i] = option; }
  answeredCount() { return Object.keys(this.answers).length; }
  detail(i: number) { return this.result?.details.find((d: any) => d.questionId === i); }
  submit() {
    const left = this.attempt.questions.length - this.answeredCount();
    const once = this.info?.maxAttempts === 1 ? ' This is your only attempt.' : '';
    if (left > 0 && !confirm(`${left} question(s) are not answered and will count as wrong.${once} Submit anyway?`)) return;
    if (left === 0 && once && !confirm('Submit your answers? This is your only attempt - the quiz will be locked afterwards.')) return;
    this.busy = true; this.err = '';
    this.api.post(`/quizzes/${this.quizId}/submit`, { attemptId: this.attempt.attemptId, answers: this.answers }).subscribe({
      next: r => { this.result = r; this.busy = false; this.loadInfo(); window.scrollTo({ top: 0, behavior: 'smooth' }); },
      error: e => { this.err = this.api.msg(e); this.busy = false; } });
  }
}
