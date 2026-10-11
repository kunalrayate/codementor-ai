import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';

const TEMPLATE = 'import java.util.*;\n\npublic class Main {\n  public static void main(String[] args) {\n    Scanner sc = new Scanner(System.in);\n    // TODO: write your solution\n  }\n}\n';
const norm = (s: string) => (s || '').replace(/\r/g, '').trim().split('\n').map(x => x.trimEnd()).join('\n');

@Component({
  selector: 'app-code',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2>💻 Java practice</h2>
    @if (mode === 'local') { <div class="warn">⚠ Docker is not running, so your code runs in <b>LOCAL mode</b> (checked and time-limited, but not isolated - fine on your own PC). Start Docker Desktop for the secure container sandbox.</div> }
    <div class="two" style="grid-template-columns:minmax(260px,1fr) 2fr">
      <div>
        <div class="card"><b>Choose a topic</b><div style="margin-top:6px">
          <span class="chip" [class.on]="!topic" (click)="setTopic('')">All ({{ all.length }})</span>
          @for (t of topics; track t) { <span class="chip" [class.on]="topic === t" (click)="setTopic(t)">{{ t }}</span> }</div></div>
        @for (p of shown; track p.id) {
          <div class="prob" [class.sel]="current?.id === p.id" (click)="select(p)">
            <span>{{ p.solved ? '✅' : '⬜' }}</span><span style="flex:1">{{ p.title }}</span><span class="pill" [class]="'pill ' + p.difficulty">{{ p.difficulty }}</span></div>
        }
        @if (!shown.length) { <span class="muted">No problems in this topic.</span> }
      </div>
      <div>
        @if (current) {
          <div class="card"><h3>{{ current.title }} <span class="pill">{{ current.topic }}</span></h3><p style="white-space:pre-wrap">{{ current.description }}</p>
            <textarea #editor class="code" [(ngModel)]="code" spellcheck="false"></textarea>
            <label class="muted">Input for <b>Run</b> (stdin) - already filled with the sample input; edit it to try other values</label>
            <textarea rows="3" [(ngModel)]="stdin" placeholder="Input for your program"></textarea>
            <div class="row"><button class="secondary" (click)="run()" [disabled]="busy">▶ Run</button>
              @if (auth.hasRole('STUDENT')) { <button (click)="submit()" [disabled]="busy">✔ Submit (runs all tests)</button> }
              <button class="secondary" (click)="reset()">↺ Reset code</button>
              <button class="secondary" (click)="useSample()">Use sample input</button>
              @if (busy) { <span class="muted">Running…</span> }</div>
            @if (resetMsg) { <div class="okc" style="margin-top:6px">{{ resetMsg }}</div> }
            <p class="muted">The class must be named Main. <b>Run</b> tests your code with the input above; <b>Submit</b> checks it against all hidden test cases and gives AI hints (never the full solution).</p>
          </div>
        }
        @if (runOut !== null) {
          <div class="card"><h3>Output</h3><pre>{{ runOut }}</pre>
            @if (runHints.length) { <div class="warn"><b>What went wrong</b><ul>@for (h of runHints; track $index) { <li>{{ h }}</li> }</ul></div> }
            @if (sampleMatch === true) { <div class="okc">✅ Matches the expected sample output. Now press Submit.</div> }
            @if (sampleMatch === false) { <div class="err">❌ Does not match the sample output ({{ current.sampleOutput }}). Check your logic.</div> }
          </div>
        }
        @if (result) {
          @if (result.status === 'PASSED') {
            <div class="card"><h3 class="okc">🎉 PASSED - {{ result.passed }}/{{ result.total }} test cases passed @if (result.xpAwarded) { <span>(+{{ result.xpAwarded }} XP)</span> }</h3>
              <div>@for (o of result.outcomes; track o.index) { <span class="badge">Test {{ o.index }} ✅</span> }</div></div>
          } @else {
            <div class="card"><h3 class="err">❌ {{ statusLabel(result.status) }} - {{ result.passed }}/{{ result.total }} test cases passed</h3>
              @if (result.compileOutput) { <b>Compiler messages</b><pre>{{ result.compileOutput }}</pre> }
              @for (o of result.outcomes; track o.index) {
                <div>Test {{ o.index }}: {{ o.passed ? '✅ passed' : '❌ failed' }}
                  @if (o.expected !== null && !o.passed) { <pre>Input:
{{ o.input }}
Expected: {{ o.expected }}
Your output: {{ o.actual }}</pre> }</div>
              }
              @if (result.hints.length) { <div class="warn"><b>What went wrong and how to fix it</b><ul>@for (h of result.hints; track $index) { <li>{{ h }}</li> }</ul></div> }
              <div class="row"><button class="secondary" (click)="getHint()" [disabled]="hintBusy">💡 Ask the AI for a hint (optional)</button>
                @if (hintBusy) { <span class="muted typing"><span></span><span></span><span></span></span> }</div>
              @if (hintText) { <pre>{{ hintText }}</pre> }
              @if (hintErr) { <div class="muted">{{ hintErr }}</div> }
            </div>
          }
        }
        @if (err) { <div class="err">{{ err }}</div> }
      </div>
    </div>

    @if (auth.hasRole('INSTRUCTOR', 'ADMIN')) {
      <div class="card"><h3>➕ Add a coding problem</h3>
        <div class="row"><input placeholder="Title" [(ngModel)]="np.title" style="flex:2" />
          <input placeholder="Topic (e.g. Strings)" [(ngModel)]="np.topic" style="flex:1" />
          <select [(ngModel)]="np.difficulty" style="flex:1"><option>Easy</option><option>Medium</option><option>Hard</option></select></div>
        <textarea rows="4" placeholder="Problem description - say what the program reads and prints" [(ngModel)]="np.description"></textarea>
        <label class="muted">Starter code shown to students</label>
        <textarea rows="7" class="code" style="min-height:140px" [(ngModel)]="np.starterCode" spellcheck="false"></textarea>
        <b>Test cases</b> <span class="muted">(the first one is shown to students as the sample)</span>
        @for (t of np.tests; track $index) {
          <div class="row" style="align-items:flex-start;margin:6px 0">
            <textarea rows="2" placeholder="Input (what the program reads)" [(ngModel)]="t.input" style="flex:1;margin:0"></textarea>
            <textarea rows="2" placeholder="Expected output" [(ngModel)]="t.expected" style="flex:1;margin:0"></textarea>
            <button class="secondary" (click)="removeTest($index)" [disabled]="np.tests.length === 1">✕</button></div>
        }
        <div class="row"><button class="secondary" (click)="addTest()">+ Add test case</button><button (click)="createProblem()">Create problem</button></div>
        @if (createMsg) { <div class="okc">{{ createMsg }}</div> }
      </div>
    }`
})
export class CodeComponent implements OnInit {
  @ViewChild('editor') editor?: ElementRef<HTMLTextAreaElement>;
  all: any[] = []; topics: string[] = []; topic = ''; current: any; code = ''; stdin = ''; runOut: string | null = null; result: any;
  err = ''; busy = false; mode = 'docker'; resetMsg = ''; createMsg = ''; sampleMatch: boolean | null = null; runHints: string[] = []; hintText = ''; hintErr = ''; hintBusy = false;
  drafts: any = {};
  np: any = this.blank();
  constructor(private api: Api, public auth: AuthService) {}
  blank() { return { title: '', topic: '', difficulty: 'Easy', description: '', starterCode: TEMPLATE, tests: [{ input: '', expected: '' }] }; }
  get shown() { return this.topic ? this.all.filter(p => p.topic === this.topic) : this.all; }
  ngOnInit() {
    this.api.get('/code/info').subscribe(i => this.mode = i.mode);
    this.api.get('/problems/topics').subscribe(t => this.topics = t);
    this.load();
  }
  load() { this.api.get('/problems').subscribe(p => { this.all = p; if (p.length && !this.current) this.select(p[0]); }); }
  setTopic(t: string) { this.topic = t; }
  select(p: any) {
    if (this.current) this.drafts[this.current.id] = this.code;
    this.current = p; this.code = this.drafts[p.id] ?? p.starterCode ?? TEMPLATE; this.stdin = p.sampleInput || '';
    this.result = null; this.runOut = null; this.err = ''; this.resetMsg = ''; this.sampleMatch = null; this.runHints = []; this.hintText = ''; this.hintErr = '';
  }
  useSample() { this.stdin = this.current.sampleInput || ''; }
  reset() {
    this.code = this.current.starterCode || TEMPLATE; delete this.drafts[this.current.id];
    this.stdin = this.current.sampleInput || ''; this.result = null; this.runOut = null; this.err = ''; this.sampleMatch = null;
    this.resetMsg = '↺ Code reset to the starter code.';
    setTimeout(() => { this.editor?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' }); }, 0);
    setTimeout(() => this.resetMsg = '', 4000);
  }
  run() {
    this.busy = true; this.err = ''; this.result = null; this.resetMsg = ''; this.sampleMatch = null;
    this.api.post('/code/execute', { code: this.code, input: this.stdin }).subscribe({
      next: r => {
        this.busy = false; this.runOut = r.output; this.runHints = r.hints || [];
        if (r.ok && this.current.sampleOutput && norm(this.stdin) === norm(this.current.sampleInput)) this.sampleMatch = norm(r.output) === norm(this.current.sampleOutput);
      },
      error: e => { this.busy = false; this.err = this.api.msg(e); } });
  }
  statusLabel(s: string) { return s === 'COMPILE_ERROR' ? 'Compile error' : s === 'TIMEOUT' ? 'Time limit exceeded' : 'Wrong answer'; }
  getHint() {
    this.hintBusy = true; this.hintText = ''; this.hintErr = '';
    const problem = (this.result?.compileOutput || '') + '\n' + (this.result?.hints || []).join('\n');
    this.api.post(`/problems/${this.current.id}/hint`, { code: this.code, error: problem }).subscribe({
      next: r => { this.hintText = r.text; this.hintBusy = false; },
      error: e => { this.hintErr = 'The AI hint is not available right now (' + this.api.msg(e) + '). The explanation above is still valid.'; this.hintBusy = false; } });
  }
  submit() {
    this.busy = true; this.err = ''; this.runOut = null; this.resetMsg = ''; this.hintText = ''; this.hintErr = '';
    this.api.post(`/problems/${this.current.id}/submit`, { code: this.code }).subscribe({
      next: r => { this.busy = false; this.result = r; if (r.status === 'PASSED') this.current.solved = true; },
      error: e => { this.busy = false; this.err = this.api.msg(e); } });
  }
  addTest() { this.np.tests.push({ input: '', expected: '' }); }
  removeTest(i: number) { if (this.np.tests.length > 1) this.np.tests.splice(i, 1); }
  createProblem() {
    this.err = ''; this.createMsg = '';
    const tests = this.np.tests.filter((t: any) => (t.expected || '').trim() !== '').map((t: any) => ({ input: t.input || '', expected: t.expected }));
    if (!this.np.title.trim() || !this.np.description.trim()) { this.err = 'Please enter a title and a description.'; return; }
    if (!tests.length) { this.err = 'Add at least one test case with an expected output.'; return; }
    this.api.post('/problems', { title: this.np.title, topic: this.np.topic, difficulty: this.np.difficulty, description: this.np.description, starterCode: this.np.starterCode, tests }).subscribe({
      next: () => { this.np = this.blank(); this.createMsg = '✅ Problem created. It is now visible to students.'; this.load(); this.api.get('/problems/topics').subscribe(t => this.topics = t); },
      error: e => this.err = this.api.msg(e) });
  }
}
