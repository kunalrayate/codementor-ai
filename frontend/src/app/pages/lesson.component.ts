import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';

declare const YT: any;

@Component({
  selector: 'app-lesson',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    @if (lesson) {
      <div class="row"><a [routerLink]="['/courses', lesson.courseId]">← Back to course</a></div>
      <h2>{{ lesson.title }}</h2>
      <div class="card">
        @switch (mode) {
          @case ('yt') { <div id="ytplayer"></div> }
          @case ('iframe') { <iframe [src]="safeUrl" allowfullscreen></iframe> }
          @case ('video') { <video controls [src]="lesson.videoUrl" #v (loadedmetadata)="v.currentTime = prog.positionSeconds || 0" (timeupdate)="onTime(v)"></video> }
          @default {
            @if (resolving) { <div class="muted typing"><span></span><span></span><span></span> &nbsp;Finding the best video for this lesson…</div> }
            @else { <p class="muted">No video is available for this lesson yet. @if (videoMsg) { <br />{{ videoMsg }} }</p> }
            <a class="btn secondary" [href]="searchUrl" target="_blank" rel="noopener">🔎 Find a video tutorial on YouTube</a>
            @if (auth.hasRole('INSTRUCTOR', 'ADMIN')) { <p class="muted">Instructors: paste a YouTube, Vimeo or .mp4 link in "Edit lesson" below.</p> }
          }
        }
        @if (mode === 'yt' || ytError) {
          <p class="muted">@if (ytError) { <span class="err">This video cannot be embedded. </span> }<a [href]="watchUrl" target="_blank" rel="noopener">Open the video on YouTube ↗</a></p>
        }
        @if (auth.hasRole('STUDENT')) {
          <div class="row" style="margin-top:10px"><span class="muted">Watched {{ prog.percentWatched }}% · {{ prog.completed ? 'Completed ✅' : 'In progress' }}</span>
            @if (prog.positionSeconds) { <span class="muted">· resumes at {{ prog.positionSeconds }}s</span> }
            <button class="secondary" (click)="report(100, prog.positionSeconds)" [disabled]="prog.completed">{{ prog.completed ? 'Completed' : 'Mark complete' }}</button></div>
          @if (xpMsg) { <div class="okc">{{ xpMsg }}</div> }
        }
      </div>

      <div class="card"><h3>Lesson content</h3><p style="white-space:pre-wrap">{{ lesson.content || 'No written content.' }}</p></div>

      <div class="card"><h3>AI study tools</h3>
        @if (ai && !ai.configured) { <div class="warn">⚠ AI is not configured yet, so offline study tools are used (basic flashcards, summaries and a tutor that quotes the course notes).
          @if (auth.hasRole('ADMIN')) { <a routerLink="/admin">Add an API key in Admin → AI Settings</a> } @else { Ask your administrator to add an API key. } </div> }
        
        <div class="row">
          <button (click)="loadCards()" [disabled]="!!busy">🃏 Flashcards</button>
          <button class="secondary" (click)="summary('SHORT')" [disabled]="!!busy">Short summary</button>
          <button class="secondary" (click)="summary('DETAILED')" [disabled]="!!busy">Detailed summary</button>
          <button class="secondary" (click)="summary('EXAM')" [disabled]="!!busy">Exam summary</button>
          <a class="btn secondary" routerLink="/tutor" [queryParams]="{lesson: lesson.id}">💬 Ask the AI Tutor about this lesson</a>
        </div>
        @if (busy) { <div class="muted typing" style="margin:10px 0"><span></span><span></span><span></span> &nbsp;{{ busy }}…</div> }
        @if (err) { <div class="err">{{ err }}</div> }
        @if (summaryText) { <pre>{{ summaryText }}</pre> }
        @if (cards.length) {
          <p class="muted">Click a card to flip it.</p>
          <div class="grid">
            @for (c of cards; track c.id) {
              <div class="flip" (click)="flipped[c.id] = !flipped[c.id]">
                @if (flipped[c.id]) { <div class="face back">{{ c.answer }}</div> } @else { <div class="face">{{ c.question }}</div> }
              </div>
            }
          </div>
        }
      </div>

      <div class="card"><h3>📝 Quizzes</h3>
        @for (q of quizzes; track q.id) {
          <div class="row" style="padding:4px 0">
            @if (auth.hasRole('STUDENT')) {
              <b style="flex:1;min-width:140px">{{ q.title }}</b>
              <span class="badge">Status: {{ q.attemptsUsed > 0 ? 'Completed' : 'Not completed' }}</span>
              <span class="badge">Attempt: {{ q.attemptsUsed }}/{{ q.maxAttempts ?? '∞' }}</span>
              <span class="badge">Access: {{ q.locked ? '🔒 Locked' : '🔓 Open' }}</span>
              @if (q.locked) { <span class="muted">Score: {{ q.lastPercent }}%</span> } @else { <a class="btn" [routerLink]="['/quiz', q.id]">{{ q.attemptsUsed > 0 ? 'Retake' : 'Start quiz' }}</a> }
            } @else {
              <a [routerLink]="['/review', q.id]">{{ q.title }}</a><span class="badge">{{ q.status }}</span>@if (q.aiGenerated) { <span class="badge">AI</span> }
              @if (q.maxAttempts) { <span class="badge">🔒 {{ q.maxAttempts === 1 ? 'Single attempt' : q.maxAttempts + ' attempts' }}</span> }
            }
          </div>
        }
        @if (!quizzes.length) { <span class="muted">No published quizzes yet.</span> }
        @if (auth.hasRole('INSTRUCTOR', 'ADMIN')) {
          <p><button (click)="genQuiz()">✨ Generate quiz with AI (saved as DRAFT)</button></p>
          <details><summary><b>Create a quiz manually</b></summary>
            <input placeholder="Quiz title" [(ngModel)]="mq.title" />
            @for (q of mq.questions; track $index) {
              <div class="card"><input placeholder="Question" [(ngModel)]="q.text" />
                @for (o of q.options; track $index) { <input [placeholder]="'Option ' + 'ABCD'[$index]" [(ngModel)]="q.options[$index]" /> }
                <div class="row"><label>Correct</label><select [(ngModel)]="q.correct" style="width:80px"><option>A</option><option>B</option><option>C</option><option>D</option></select></div>
                <input placeholder="Explanation (optional)" [(ngModel)]="q.explanation" /></div>
            }
            <div class="row"><button class="secondary" (click)="addQ()">+ Add question</button><button (click)="createManual()" [disabled]="!mq.questions.length">Create quiz (draft)</button></div>
          </details>
        }
      </div>

      @if (auth.hasRole('INSTRUCTOR', 'ADMIN')) {
        <div class="card"><h3>Edit lesson</h3>
          <input placeholder="Title" [(ngModel)]="edit.title" />
          <input placeholder="Description" [(ngModel)]="edit.description" />
          <input placeholder="Video URL (YouTube, Vimeo or direct .mp4)" [(ngModel)]="edit.videoUrl" />
          <input type="number" placeholder="Position (order in module)" [(ngModel)]="edit.position" />
          <textarea rows="8" placeholder="Lesson content (used by the AI for quizzes, flashcards and the tutor)" [(ngModel)]="edit.content"></textarea>
          <button (click)="save()">Save lesson</button> <button class="danger" (click)="remove()">Delete</button>
        </div>
      }
    }`
})
export class LessonComponent implements OnInit, OnDestroy {
  lesson: any; prog: any = { percentWatched: 0, positionSeconds: 0, completed: false };
  resolving = false; videoMsg = ''; startAt = 0; mode = 'none'; safeUrl: SafeResourceUrl | null = null; player: any; timer: any; lastReport = 0; ytError = false; watchUrl = ''; xpMsg = '';
  cards: any[] = []; flipped: any = {}; quizzes: any[] = []; summaryText = ''; err = ''; edit: any = {};
  mq: any = { title: '', questions: [] }; ai: any;
  id = 0;
  constructor(private route: ActivatedRoute, private api: Api, public auth: AuthService, private san: DomSanitizer, private zone: NgZone) {}

  get searchUrl() { return 'https://www.youtube.com/results?search_query=' + encodeURIComponent('Java ' + (this.lesson?.title || '') + ' tutorial'); }

  ngOnInit() {
    this.id = +this.route.snapshot.paramMap.get('id')!;
    this.api.get(`/lessons/${this.id}`).subscribe({
      next: l => {
        this.lesson = l; this.edit = { ...l };
        if (this.auth.hasRole('STUDENT')) this.api.get(`/lessons/${this.id}/progress`).subscribe(p => { this.prog = p; this.setupVideo(); });
        else this.setupVideo();
      },
      error: e => this.err = this.api.msg(e)
    });
    this.loadQuizzes();
    this.api.get('/ai/status').subscribe(s => this.ai = s);
  }
  ngOnDestroy() { clearInterval(this.timer); }
  loadQuizzes() { this.api.get(`/lessons/${this.id}/quizzes`).subscribe(q => this.quizzes = q); }

  private setupVideo() {
    const url: string = this.lesson.videoUrl;
    this.mode = 'none'; this.ytError = false;
    if (!url) { // ask the backend to find (and save) a video for this lesson, then start it
      if (this.resolving) return;
      this.resolving = true;
      this.api.post(`/lessons/${this.id}/video/resolve`).subscribe({
        next: r => { this.resolving = false; if (r.url) { this.lesson.videoUrl = r.url; this.edit.videoUrl = r.url; this.setupVideo(); } else this.videoMsg = r.message || ''; },
        error: () => { this.resolving = false; } });
      return;
    }
    const tm = url.match(/[?&](?:t|start)=(\d+)/); this.startAt = tm ? +tm[1] : 0;
    const yt = url.match(/(?:v=|youtu\.be\/|embed\/)([\w-]{11})/);
    const vimeo = url.match(/vimeo\.com\/(\d+)/);
    if (yt) { this.mode = 'yt'; this.watchUrl = 'https://www.youtube.com/watch?v=' + yt[1]; this.loadYt(yt[1]); }
    else if (vimeo) { this.mode = 'iframe'; this.safeUrl = this.san.bypassSecurityTrustResourceUrl(`https://player.vimeo.com/video/${vimeo[1]}`); }
    else this.mode = 'video';
  }
  private loadYt(videoId: string) {
    const create = () => this.zone.run(() => {
      this.player = new YT.Player('ytplayer', {
        videoId, width: '100%', height: '420',
        playerVars: { start: this.prog.positionSeconds || this.startAt, rel: 0, autoplay: 1, origin: window.location.origin },
        events: { onReady: (e: any) => { try { e.target.playVideo(); } catch {} }, onError: () => this.zone.run(() => this.ytError = true) }
      });
    });
    const w = window as any;
    if (w.YT?.Player) setTimeout(create, 150);
    else {
      w.onYouTubeIframeAPIReady = create;
      if (!document.getElementById('yt-api')) { const s = document.createElement('script'); s.id = 'yt-api'; s.src = 'https://www.youtube.com/iframe_api'; document.body.appendChild(s); }
    }
    clearInterval(this.timer);
    this.timer = setInterval(() => { // save progress every 10 seconds
      try { const t = this.player.getCurrentTime(), d = this.player.getDuration(); if (d > 0) this.report(Math.round(t / d * 100), Math.round(t)); } catch {}
    }, 10000);
  }
  onTime(v: HTMLVideoElement) {
    if (!v.duration || Date.now() - this.lastReport < 10000) return;
    this.report(Math.round(v.currentTime / v.duration * 100), Math.round(v.currentTime));
  }
  report(percentWatched: number, positionSeconds: number) {
    if (!this.auth.hasRole('STUDENT')) return;
    this.lastReport = Date.now();
    this.api.put(`/lessons/${this.id}/progress`, { percentWatched, positionSeconds }).subscribe({
      next: p => { this.prog = p; if (p.xpAwarded) this.xpMsg = `🎉 Lesson completed! +${p.xpAwarded} XP`; },
      error: e => this.err = this.api.msg(e)
    });
  }

  busy = '';
  loadCards() { this.err = ''; this.busy = 'Preparing flashcards'; this.api.post('/ai/flashcards/generate', { lessonId: this.id }).subscribe({ next: c => { this.cards = c; this.busy = ''; }, error: e => { this.err = this.api.msg(e); this.busy = ''; } }); }
  summary(type: string) { this.err = ''; this.summaryText = ''; this.busy = 'Writing the ' + type.toLowerCase() + ' summary'; this.api.post('/ai/summary', { lessonId: this.id, type }).subscribe({ next: r => { this.summaryText = r.text; this.busy = ''; }, error: e => { this.err = this.api.msg(e); this.busy = ''; } }); }
  genQuiz() { this.err = ''; this.api.post('/ai/quiz/generate', { lessonId: this.id, numQuestions: 5 }).subscribe({ next: () => this.loadQuizzes(), error: e => this.err = this.api.msg(e) }); }
  addQ() { this.mq.questions.push({ text: '', options: ['', '', '', ''], correct: 'A', explanation: '' }); }
  createManual() {
    this.err = '';
    this.api.post(`/lessons/${this.id}/quizzes`, this.mq).subscribe({ next: () => { this.mq = { title: '', questions: [] }; this.loadQuizzes(); }, error: e => this.err = this.api.msg(e) });
  }
  save() { this.api.put(`/lessons/${this.id}`, this.edit).subscribe({ next: l => { this.lesson = l; this.setupVideo(); }, error: e => this.err = this.api.msg(e) }); }
  remove() { if (confirm('Delete this lesson?')) this.api.delete(`/lessons/${this.id}`).subscribe(() => history.back()); }
}
