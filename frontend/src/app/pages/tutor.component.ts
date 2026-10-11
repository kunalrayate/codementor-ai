import { Component, NgZone, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { timeout } from 'rxjs';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-tutor',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2>🤖 AI Tutor</h2>
    @if (ai && !ai.configured) { <div class="warn">⚠ AI is not configured yet, so the tutor quotes the course notes.
      @if (auth.hasRole('ADMIN')) { <a href="/admin">Add an API key in Admin → AI Settings</a> } @else { Ask your administrator to add an API key. } </div> }
    <div class="row">
      <button class="secondary" (click)="openConversation(null)">➕ New chat</button>
      <label class="row"><input type="checkbox" [(ngModel)]="useMaterials" style="width:auto;margin:0" /> Use my uploaded PDFs</label>
      <label class="row"><input type="checkbox" [(ngModel)]="speak" style="width:auto;margin:0" /> 🔊 Read answers aloud</label>
      @if (remaining !== null) { <span class="muted">AI requests left today: {{ remaining }}</span> }
    </div>
    <div class="card" style="padding:14px 18px">
      <div class="row" style="justify-content:space-between"><b>💬 My chats ({{ conversations.length }})</b>
        @if (conversations.length) { <button class="danger" (click)="clearAll()">🗑 Delete all chats</button> }</div>
      <div style="max-height:190px;overflow:auto;margin-top:8px">
        @for (c of conversations; track c.id) {
          <div class="prob" [class.sel]="c.id === conversationId" (click)="openConversation(c.id)">
            <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ c.title }}</span>
            <button class="danger" style="padding:4px 12px" (click)="deleteChat(c, $event)">🗑 Delete</button></div>
        }
        @if (!conversations.length) { <span class="muted">No saved chats yet - ask your first question below.</span> }
      </div>
    </div>
    @if (lessonTitle) { <div class="warn" style="background:var(--bg2);border-color:var(--border);color:var(--text)">📘 Studying: <b>{{ lessonTitle }}</b> - your questions use this lesson as context.</div> }

    <div class="card" style="min-height:260px">
      @if (!messages.length) {
        <p class="muted">Ask me anything about Java or programming. Try one of these:</p>
        @for (s of suggestions; track s) { <span class="chip" (click)="ask(s)">{{ s }}</span> }
      }
      @for (m of messages; track $index) { <div class="msg" [class.user]="m.role === 'user'" [class.assistant]="m.role !== 'user'">{{ m.content }}</div> }
      @if (loading) { <div class="msg assistant typing"><span></span><span></span><span></span></div> @if (slow) { <div class="muted">Still working - the AI is a little slower than usual…</div> } }
    </div>
    @if (err) { <div class="err">{{ err }}</div> }
    <div class="row">
      <input style="flex:1;margin:0" placeholder="Ask anything about programming…" [(ngModel)]="input" (keyup.enter)="send()" maxlength="2000" />
      <button class="secondary" (click)="toggleMic()">{{ listening ? '⏹ Stop' : '🎤 Speak' }}</button>
      <button (click)="send()" [disabled]="loading">Send</button>
    </div>

    <div class="card" style="margin-top:22px"><h3>📄 Learning materials (PDF)</h3>
      <p class="muted">Upload a PDF, then view it, summarise it or ask the tutor questions about it.</p>
      <input placeholder="Title" [(ngModel)]="docTitle" />
      <input type="file" accept="application/pdf" (change)="file = $any($event.target).files[0]" />
      @if (auth.hasRole('INSTRUCTOR', 'ADMIN')) { <select [(ngModel)]="docCourse"><option [ngValue]="null">Private (only me)</option>@for (c of courses; track c.id) { <option [ngValue]="c.id">Share with course: {{ c.title }}</option> }</select> }
      <button (click)="upload()" [disabled]="uploading">{{ uploading ? 'Processing PDF…' : '⬆ Upload' }}</button>
      @if (docMsg) { <span [class.okc]="docOk" [class.err]="!docOk"> {{ docMsg }}</span> }

      @for (d of docs; track d.id) {
        <div class="docitem"><b style="flex:1;min-width:160px">📕 {{ d.title }}</b>
          <button class="secondary" (click)="view(d)">👁 View</button>
          <button class="secondary" (click)="summarize(d)">📝 Summarise</button>
          <button class="secondary" (click)="askAbout(d)">💬 Ask about it</button>
          @if (d.mine) { <button class="danger" (click)="remove(d)">🗑</button> }
        </div>
      }
      @if (!docs.length) { <p class="muted">No documents yet.</p> }
    </div>

    @if (viewDoc) {
      <div class="card"><div class="row" style="justify-content:space-between"><h3>📕 {{ viewDoc.title }}</h3><button class="secondary" (click)="closeDoc()">✕ Close</button></div>
        <div class="tabs"><span class="chip" [class.on]="tab === 'pdf'" (click)="tab = 'pdf'">PDF</span><span class="chip" [class.on]="tab === 'text'" (click)="tab = 'text'">Extracted text</span>
          <span class="chip" [class.on]="tab === 'sum'" (click)="tab = 'sum'">Summary</span></div>
        @if (tab === 'pdf') {
          @if (pdfUrl) { <iframe class="pdf" [src]="pdfUrl"></iframe> } @else { <p class="muted">The original file is not stored for this older upload - see the extracted text.</p> }
        }
        @if (tab === 'text') { <p class="muted">{{ viewDoc.length }} characters extracted - this is what the AI Tutor searches.</p><pre>{{ viewDoc.text || 'No text could be extracted.' }}</pre> }
        @if (tab === 'sum') { @if (docBusy) { <div class="muted typing"><span></span><span></span><span></span> &nbsp;Summarising…</div> } @else { <pre>{{ docSummary || 'Press Summarise on the document.' }}</pre> } }
      </div>
    }`
})
export class TutorComponent implements OnInit, OnDestroy {
  conversations: any[] = []; conversationId: number | null = null; messages: any[] = []; input = '';
  ai: any; loading = false; slow = false; err = ''; remaining: number | null = null; useMaterials = false; speak = false;
  lessonId: number | null = null; lessonTitle = ''; listening = false; rec: any;
  suggestions = ['What is object-oriented programming?', 'Explain inheritance with an example', 'Difference between List, Set and Map', 'How does exception handling work?'];
  courses: any[] = []; docCourse: any = null; docs: any[] = []; docTitle = ''; file: File | null = null; uploading = false; docMsg = ''; docOk = true;
  viewDoc: any = null; pdfUrl: SafeResourceUrl | null = null; pdfRaw = ''; tab = 'pdf'; docSummary = ''; docBusy = false;
  constructor(private api: Api, private route: ActivatedRoute, private zone: NgZone, public auth: AuthService, private san: DomSanitizer) {}

  ngOnInit() {
    const l = this.route.snapshot.queryParamMap.get('lesson');
    this.lessonId = l ? +l : null;
    if (this.lessonId) this.api.get(`/lessons/${this.lessonId}`).subscribe(x => {
      this.lessonTitle = x.title;
      this.suggestions = [`Explain "${x.title}" in simple words`, `Give me a real-world example of ${x.title}`, `Quiz me with 3 questions on ${x.title}`, `What are common mistakes with ${x.title}?`];
    });
    this.loadConversations(); this.loadDocs();
    if (this.auth.hasRole('INSTRUCTOR', 'ADMIN')) this.api.get('/me/courses').subscribe(c => this.courses = c);
    this.api.get('/ai/status').subscribe(s => this.ai = s);
    this.api.get('/me/ai-usage').subscribe(u => this.remaining = u.remaining);
  }
  ngOnDestroy() { this.closeDoc(); }
  loadConversations() { this.api.get('/ai/conversations').subscribe(c => this.conversations = c); }
  loadDocs() { this.api.get('/documents').subscribe(d => this.docs = d); }
  openConversation(id: number | null) {
    this.conversationId = id; this.messages = [];
    if (id) this.api.get(`/ai/conversations/${id}`).subscribe(m => this.messages = m);
  }
  deleteChat(c: any, ev: Event) {
    ev.stopPropagation();
    if (!confirm(`Delete the chat "${c.title}"?`)) return;
    this.api.delete(`/ai/conversations/${c.id}`).subscribe(() => { if (this.conversationId === c.id) { this.conversationId = null; this.messages = []; } this.loadConversations(); });
  }
  clearAll() {
    if (!confirm('Delete ALL your AI Tutor chats? This cannot be undone.')) return;
    this.api.delete('/ai/conversations').subscribe(() => { this.conversationId = null; this.messages = []; this.loadConversations(); });
  }
  ask(text: string) { this.input = text; this.send(); }
  send() {
    const text = this.input.trim(); if (!text || this.loading) return;
    this.err = ''; this.input = ''; this.loading = true; this.slow = false;
    const slowTimer = setTimeout(() => this.slow = true, 12000);
    this.messages.push({ role: 'user', content: text });
    this.api.post('/ai/chat', { conversationId: this.conversationId, message: text, lessonId: this.lessonId, useMaterials: this.useMaterials }).pipe(timeout(80000)).subscribe({
      next: r => {
        clearTimeout(slowTimer); this.loading = false; this.remaining = r.remainingRequests;
        if (!this.conversationId) { this.conversationId = r.conversationId; this.loadConversations(); }
        this.messages.push({ role: 'assistant', content: r.reply });
        this.say(r.reply);
      },
      error: e => { clearTimeout(slowTimer); this.loading = false; this.err = e?.name === 'TimeoutError' ? 'The AI took too long to answer. Please try again.' : this.api.msg(e); }
    });
  }
  // ---- voice: browser Speech-to-Text and Text-to-Speech (Web Speech API)
  toggleMic() {
    const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SR) { this.err = 'Speech recognition is not supported in this browser. Use Chrome or Edge.'; return; }
    if (this.listening) { this.rec.stop(); return; }
    this.err = '';
    this.rec = new SR(); this.rec.lang = 'en-US'; this.rec.interimResults = false;
    this.rec.onresult = (e: any) => this.zone.run(() => { this.input = e.results[0][0].transcript; this.send(); });
    this.rec.onend = () => this.zone.run(() => this.listening = false);
    this.rec.onerror = (e: any) => this.zone.run(() => {
      this.listening = false;
      this.err = e.error === 'not-allowed' || e.error === 'service-not-allowed' ? 'Microphone is blocked. Click the 🔒 icon in the address bar, set Microphone to "Allow", reload and press Speak again.'
        : e.error === 'no-speech' ? 'I did not hear anything - please try again.'
        : e.error === 'network' ? 'Voice input needs the browser speech service (not available in Brave). Use Chrome or Edge.' : 'Voice input error: ' + e.error;
    });
    try { this.rec.start(); this.listening = true; } catch { this.err = 'Could not start the microphone.'; }
  }
  say(text: string) {
    if (!this.speak || !('speechSynthesis' in window)) return;
    speechSynthesis.cancel();
    speechSynthesis.speak(new SpeechSynthesisUtterance(text.replace(/```[\s\S]*?```/g, ' code block ')));
  }
  // ---- PDF materials
  upload() {
    this.docMsg = '';
    if (!this.file || !this.docTitle.trim()) { this.docOk = false; this.docMsg = 'Choose a PDF and enter a title.'; return; }
    const f = new FormData(); f.append('file', this.file); f.append('title', this.docTitle); if (this.docCourse) f.append('courseId', this.docCourse);
    this.uploading = true;
    this.api.upload('/documents', f).subscribe({
      next: d => { this.uploading = false; this.docTitle = ''; this.file = null; this.docOk = true; this.docMsg = '✅ Uploaded.'; this.loadDocs(); this.view(d); },
      error: e => { this.uploading = false; this.docOk = false; this.docMsg = this.api.msg(e); }
    });
  }
  view(d: any) {
    this.closeDoc(); this.tab = d.hasFile ? 'pdf' : 'text';
    this.viewDoc = { id: d.id, title: d.title, text: '', length: 0 };
    this.api.get(`/documents/${d.id}/content`).subscribe(c => this.viewDoc = c);
    if (d.hasFile) this.api.blob(`/documents/${d.id}/file`).subscribe(b => { this.pdfRaw = URL.createObjectURL(b); this.pdfUrl = this.san.bypassSecurityTrustResourceUrl(this.pdfRaw); });
    setTimeout(() => window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }), 150);
  }
  closeDoc() { if (this.pdfRaw) URL.revokeObjectURL(this.pdfRaw); this.pdfRaw = ''; this.pdfUrl = null; this.viewDoc = null; this.docSummary = ''; }
  summarize(d: any) {
    if (!this.viewDoc || this.viewDoc.id !== d.id) this.view(d);
    this.tab = 'sum'; this.docSummary = ''; this.docBusy = true;
    this.api.post('/ai/summary', { documentId: d.id, type: 'SHORT' }).subscribe({
      next: r => { this.docSummary = r.text; this.docBusy = false; }, error: e => { this.docSummary = this.api.msg(e); this.docBusy = false; } });
  }
  askAbout(d: any) { this.useMaterials = true; this.input = `Summarise the document "${d.title}" and list its key points`; window.scrollTo({ top: 0, behavior: 'smooth' }); }
  remove(d: any) { if (confirm(`Delete "${d.title}"?`)) this.api.delete(`/documents/${d.id}`).subscribe(() => { if (this.viewDoc?.id === d.id) this.closeDoc(); this.loadDocs(); }); }
}
