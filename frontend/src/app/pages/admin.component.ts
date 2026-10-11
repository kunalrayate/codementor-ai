import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2>Admin</h2>
    <div class="card"><h3>🤖 AI Settings</h3>
      @if (ai) {
        <p>Status: <b [class.okc]="ai.configured" [style.color]="ai.configured ? '' : '#dc2626'">{{ ai.configured ? 'Configured' : 'Not configured' }}</b>
          · provider {{ ai.provider }} · model {{ ai.model }} · key {{ ai.apiKeyMasked || 'none' }} · source {{ ai.source }}</p>
      }
      <p class="muted">Pick a provider and paste its API key - it works immediately (no restart). Free options: <b>Google Gemini</b> (key at aistudio.google.com/apikey) or <b>Groq</b> (console.groq.com/keys). Ollama runs locally and needs no key.</p>
      <div class="row">
        <select [(ngModel)]="form.provider" style="max-width:220px"><option value="GEMINI">Google Gemini (free)</option><option value="GROQ">Groq (free)</option><option value="OPENAI">OpenAI (paid)</option><option value="OLLAMA">Ollama (local)</option><option value="CUSTOM">Custom (OpenAI-compatible)</option></select>
        <input type="password" placeholder="API key (leave empty to keep the saved key)" [(ngModel)]="form.apiKey" style="flex:1" />
      </div>
      <div class="row"><input placeholder="Model (optional - leave empty for the default, e.g. gemini-flash-latest)" [(ngModel)]="form.model" style="flex:1" />
        <button class="secondary" (click)="loadModels()">Load available models</button></div>
      @if (models.length) { <select [ngModel]="form.model" (ngModelChange)="form.model = $event"><option value="">- pick a model -</option>@for (m of models; track m) { <option [value]="m">{{ m }}</option> }</select> }
      @if (modelsMsg) { <div class="err">{{ modelsMsg }}</div> }
      @if (form.provider === 'CUSTOM') {
        <input placeholder="Base URL, e.g. https://api.example.com" [(ngModel)]="form.baseUrl" />
        <input placeholder="Chat path, e.g. /v1/chat/completions" [(ngModel)]="form.chatPath" />
        <input placeholder="Embeddings path (optional)" [(ngModel)]="form.embeddingsPath" />
        <input placeholder="Embedding model (optional)" [(ngModel)]="form.embeddingModel" />
      }
      <div class="row"><button (click)="saveAi()">Save</button><button class="secondary" (click)="testAi()" [disabled]="testing">{{ testing ? 'Testing…' : 'Test connection' }}</button></div>
      @if (aiMsg) { <div [class.okc]="aiOk" [class.err]="!aiOk" style="margin-top:8px">{{ aiMsg }}</div> }
    </div>
    <div class="card"><h3>▶ Lesson videos (YouTube)</h3>
      @if (vid) { <p>Status: <b [class.okc]="vid.configured" [style.color]="vid.configured ? '' : '#dc2626'">{{ vid.configured ? 'Ready' : 'Not set up' }}</b> · key source: {{ vid.source }}</p> }
      <p class="muted">Every lesson gets a playable video automatically - it works out of the box through YouTube web search, and lessons are filled in the background after start-up. For the most reliable results also use the official API (optional): (1) open
        <a href="https://console.cloud.google.com/apis/library/youtube.googleapis.com" target="_blank" rel="noopener">YouTube Data API v3</a>, choose the same Google project as your Gemini key and click <b>Enable</b>;
        (2) leave the box empty to reuse your Gemini key, or paste an API key of that project; (3) click <b>Test</b>, then <b>Fill videos for all lessons</b>.</p>
      <input type="password" placeholder="YouTube API key (optional - leave empty to reuse the Gemini key)" [(ngModel)]="vidKey" />
      <div class="row"><button (click)="saveVid()">Save key</button><button class="secondary" (click)="testVid()" [disabled]="vidBusy">Test</button>
        <button class="secondary" (click)="fillVid()" [disabled]="vidBusy">{{ vidBusy ? 'Working…' : '🎬 Fill videos for all lessons' }}</button></div>
      @if (vidMsg) { <div [class.okc]="vidOk" [class.err]="!vidOk" style="margin-top:8px">{{ vidMsg }}</div> }
    </div>
    @if (stats) { <div class="grid">@for (k of keys(stats); track k) { <div class="card stat"><b>{{ stats[k] }}</b>{{ k }}</div> }</div> }
    <div class="card"><h3>🏆 Leaderboard rules (XP per activity)</h3>
      <table><tr><th>Activity</th><th>Description</th><th>XP</th><th></th></tr>
        @for (r of rules; track r.code) { <tr><td>{{ r.code }}</td><td>{{ r.description }}</td><td style="width:110px"><input type="number" [(ngModel)]="r.xp" style="margin:0" /></td>
          <td><button (click)="saveRule(r)">Save</button></td></tr> }
      </table>@if (msg) { <div class="okc">{{ msg }}</div> }<p class="muted">Changes apply to XP earned from now on. To remove fraudulent XP, delete the event below.</p></div>
    <div class="card"><h3>Users</h3><table>
      <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr>
      @for (u of users; track u.id) { <tr><td>{{ u.name }}</td><td>{{ u.email }}</td><td>{{ u.role }}</td><td>{{ u.enabled ? 'Active' : 'Disabled' }}</td>
        <td><button class="secondary" (click)="toggle(u)">{{ u.enabled ? 'Disable' : 'Enable' }}</button></td></tr> }
    </table></div>
    <div class="card"><h3>AI usage (last 7 days)</h3><table>
      <tr><th>User</th><th>Date</th><th>Requests</th><th>In</th><th>Out</th><th>Total tokens</th></tr>
      @for (a of usage; track $index) { <tr><td>{{ a.user }}</td><td>{{ a.date }}</td><td>{{ a.requests }}</td><td>{{ a.inputTokens }}</td><td>{{ a.outputTokens }}</td><td>{{ a.totalTokens }}</td></tr> }
    </table>@if (!usage.length) { <span class="muted">No AI usage yet.</span> }</div>
    <div class="card"><h3>Recent XP events</h3><table>
      <tr><th>Student</th><th>Type</th><th>XP</th><th>When</th><th></th></tr>
      @for (e of events; track e.id) { <tr><td>{{ e.student }}</td><td>{{ e.type }}</td><td>{{ e.xp }}</td><td>{{ e.createdAt }}</td>
        <td><button class="danger" (click)="del(e)">Remove</button></td></tr> }
    </table></div>
    <div class="card"><h3>🏅 Badges</h3>
      <table><tr><th>Code</th><th>Name</th><th>Description</th><th>Auto-award at XP</th><th></th></tr>
        @for (b of badges; track b.id) {
          @if (editId === b.id) {
            <tr><td><input [(ngModel)]="ef.code" style="margin:0" /></td><td><input [(ngModel)]="ef.name" style="margin:0" /></td><td><input [(ngModel)]="ef.description" style="margin:0" /></td>
              <td style="width:120px"><input type="number" [(ngModel)]="ef.minXp" placeholder="none" style="margin:0" /></td>
              <td><button (click)="saveBadge(b)">Save</button> <button class="secondary" (click)="editId = null">Cancel</button></td></tr>
          } @else {
            <tr><td>{{ b.code }}</td><td>🏅 {{ b.name }}</td><td>{{ b.description }}</td><td>{{ b.minXp ?? '-' }}</td>
              <td><button class="secondary" (click)="startEdit(b)">✏ Edit</button> <button class="danger" (click)="deleteBadge(b)">🗑 Delete</button></td></tr>
          }
        }
      </table>
      <h4 style="margin-top:18px">➕ Create a badge</h4>
      <div class="row"><input placeholder="Code (e.g. JAVA_STAR)" [(ngModel)]="nb.code" style="flex:1" /><input placeholder="Name" [(ngModel)]="nb.name" style="flex:1" /></div>
      <div class="row"><input placeholder="Description" [(ngModel)]="nb.description" style="flex:2" /><input type="number" placeholder="Auto-award at XP (optional)" [(ngModel)]="nb.minXp" style="flex:1" /></div>
      <button (click)="addBadge()">Create badge</button>
      @if (badgeMsg) { <div [class.okc]="badgeOk" [class.err]="!badgeOk" style="margin-top:8px">{{ badgeMsg }}</div> }
      <p class="muted">Badges with an "Auto-award at XP" value are given automatically when a student's total XP reaches it. Built-in badges (lessons, courses, streaks) are awarded by their own rules.</p>
    </div>`
})
export class AdminComponent implements OnInit {
  stats: any; users: any[] = []; usage: any[] = []; events: any[] = []; badges: any[] = []; rules: any[] = []; msg = '';
  ai: any; form: any = { provider: 'GEMINI', apiKey: '', model: '', baseUrl: '', chatPath: '', embeddingsPath: '', embeddingModel: '' }; aiMsg = ''; aiOk = false; testing = false; models: string[] = []; modelsMsg = ''; vid: any; vidKey = ''; vidMsg = ''; vidOk = true; vidBusy = false;
  nb: any = { code: '', name: '', description: '', minXp: null }; editId: number | null = null; ef: any = {}; badgeMsg = ''; badgeOk = true;
  constructor(private api: Api) {}
  keys(o: any) { return Object.keys(o); }
  ngOnInit() {
    this.api.get('/admin/stats').subscribe(s => this.stats = s);
    this.api.get('/admin/users').subscribe(u => this.users = u);
    this.api.get('/admin/ai-usage?days=7').subscribe(u => this.usage = u);
    this.api.get('/admin/xp-events').subscribe(e => this.events = e);
    this.api.get('/admin/badges').subscribe(b => this.badges = b);
    this.api.get('/admin/xp-rules').subscribe(r => this.rules = r);
    this.loadAi();
    this.loadVid();
  }
  loadVid() { this.api.get('/admin/video-settings').subscribe(v => this.vid = v); }
  saveVid() { this.api.put('/admin/video-settings', { apiKey: this.vidKey }).subscribe({ next: v => { this.vid = v; this.vidKey = ''; this.vidOk = true; this.vidMsg = 'Saved.'; }, error: e => { this.vidOk = false; this.vidMsg = this.api.msg(e); } }); }
  testVid() { this.vidBusy = true; this.vidMsg = ''; this.api.post('/admin/video-settings/test').subscribe({ next: r => { this.vidBusy = false; this.vidOk = r.ok; this.vidMsg = (r.ok ? '✅ ' : '❌ ') + r.message; }, error: e => { this.vidBusy = false; this.vidOk = false; this.vidMsg = this.api.msg(e); } }); }
  fillVid() { this.vidBusy = true; this.vidMsg = ''; this.api.post('/admin/video-settings/fill-all').subscribe({ next: r => { this.vidBusy = false; this.vidOk = r.filled > 0 || r.failed === 0; this.vidMsg = (this.vidOk ? '✅ ' : '❌ ') + r.message; }, error: e => { this.vidBusy = false; this.vidOk = false; this.vidMsg = this.api.msg(e); } }); }
  loadAi() { this.api.get('/admin/ai-settings').subscribe(a => { this.ai = a; if (a.source === 'database') this.form.provider = a.provider; }); }
  loadModels() {
    this.modelsMsg = ''; this.models = [];
    this.api.get('/admin/ai-settings/models').subscribe({
      next: r => { if (r.ok) { this.models = r.models; if (!r.models.length) this.modelsMsg = 'The provider returned no models.'; } else this.modelsMsg = r.message; },
      error: e => this.modelsMsg = this.api.msg(e) });
  }
  saveAi() {
    this.aiMsg = ''; this.testing = false;
    this.api.put('/admin/ai-settings', this.form).subscribe({
      next: a => { this.ai = a; this.form.apiKey = ''; this.aiOk = true; this.aiMsg = 'Saved. Click "Test connection" to verify.'; },
      error: e => { this.aiOk = false; this.aiMsg = this.api.msg(e); } });
  }
  testAi() {
    this.testing = true; this.aiMsg = '';
    this.api.post('/admin/ai-settings/test').subscribe({
      next: r => { this.testing = false; this.aiOk = r.ok; this.aiMsg = (r.ok ? '✅ ' : '❌ ') + r.message; },
      error: e => { this.testing = false; this.aiOk = false; this.aiMsg = this.api.msg(e); } });
  }
  saveRule(r: any) { this.api.put(`/admin/xp-rules/${r.code}`, r).subscribe({ next: () => this.msg = `Saved ${r.code} = ${r.xp} XP`, error: e => this.msg = this.api.msg(e) }); }
  toggle(u: any) { this.api.put(`/admin/users/${u.id}/enabled`, { enabled: !u.enabled }).subscribe(() => u.enabled = !u.enabled); }
  del(e: any) { this.api.delete(`/admin/xp-events/${e.id}`).subscribe(() => this.events = this.events.filter(x => x.id !== e.id)); }
  loadBadges() { this.api.get('/admin/badges').subscribe(b => this.badges = b); }
  startEdit(b: any) { this.editId = b.id; this.ef = { ...b }; this.badgeMsg = ''; }
  private done(ok: boolean, msg: string) { this.badgeOk = ok; this.badgeMsg = msg; }
  addBadge() {
    this.badgeMsg = '';
    this.api.post('/admin/badges', this.nb).subscribe({
      next: () => { this.nb = { code: '', name: '', description: '', minXp: null }; this.done(true, '✅ Badge created.'); this.loadBadges(); },
      error: e => this.done(false, this.api.msg(e)) });
  }
  saveBadge(b: any) {
    this.api.put(`/admin/badges/${b.id}`, this.ef).subscribe({
      next: () => { this.editId = null; this.done(true, '✅ Badge updated.'); this.loadBadges(); },
      error: e => this.done(false, this.api.msg(e)) });
  }
  deleteBadge(b: any) {
    if (!confirm(`Delete the badge "${b.name}"? Students who earned it will lose it.`)) return;
    this.api.delete(`/admin/badges/${b.id}`).subscribe({ next: () => { this.done(true, '🗑 Badge deleted.'); this.loadBadges(); }, error: e => this.done(false, this.api.msg(e)) });
  }
}
