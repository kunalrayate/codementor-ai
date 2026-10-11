import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    @if (me) {
      <div class="hero"><h2>Welcome back, {{ me.user.name }} 👋</h2><div>Every lesson, quiz and coding problem earns XP. Keep your streak going!</div></div>
      @if (me.user.role === 'STUDENT') {
        <div class="grid">
          <div class="card stat"><b>{{ me.totalXp }}</b>Total XP</div>
          <div class="card stat"><b>{{ me.globalRank ? '#' + me.globalRank : 'Unranked' }}</b>Global rank</div>
          <div class="card stat"><b>{{ me.streakDays }}🔥</b>Day streak</div>
          <div class="card stat"><b>{{ me.aiUsage.remaining }}/{{ me.aiUsage.limit }}</b>AI requests left today</div>
        </div>

        <div class="two">
          <div class="card"><h3>📚 My learning progress</h3>
            @for (p of progress; track p.courseId) {
              <div style="margin-bottom:12px"><div class="row" style="justify-content:space-between"><a [routerLink]="['/courses', p.courseId]"><b>{{ p.title }}</b></a><span class="muted">{{ p.completed }}/{{ p.total }} lessons · {{ p.percent }}%</span></div>
                <div class="bar"><div [style.width.%]="p.percent"></div></div></div>
            }
            @if (!progress.length) { <span class="muted">You are not enrolled in a course yet. <a routerLink="/courses">Browse courses</a></span> }
          </div>
          <div class="card"><h3>📊 Performance</h3>
            @if (perf?.modules?.length) { <p class="muted">Quiz average by module</p> }
            @for (m of perf?.modules; track m.name) {
              <div style="margin-bottom:8px"><div class="row" style="justify-content:space-between"><span>{{ m.name }}</span><b [style.color]="m.percent < 60 ? '#dc2626' : '#16a34a'">{{ m.percent }}%</b></div>
                <div class="bar"><div [style.width.%]="m.percent" [style.background]="m.percent < 60 ? '#dc2626' : '#16a34a'"></div></div></div>
            }
            @if (perf?.codeTopics?.length) { <p class="muted">Coding practice by topic</p> }
            @for (c of perf?.codeTopics; track c.topic) { <div class="row" style="justify-content:space-between"><span>{{ c.topic }}</span><span>{{ c.passed }} passed / {{ c.attempts }} attempts</span></div> }
            @if (!perf?.modules?.length && !perf?.codeTopics?.length) { <span class="muted">Take a quiz or solve a coding problem to see your performance here.</span> }
          </div>
        </div>

        <div class="two">
          <div class="card"><h3>🏆 Leaderboard (top 5)</h3>
            <table>@for (r of top; track r.studentId) { <tr [style.font-weight]="r.studentId === me.user.id ? 700 : 400"><td>#{{ r.rank }}</td><td>{{ r.name }}</td><td>{{ r.xp }} XP</td></tr> }</table>
            @if (!top.length) { <span class="muted">No XP earned yet - be the first!</span> }
            <p><a routerLink="/leaderboard">Full leaderboard →</a></p>
          </div>
          <div class="card"><h3>⚡ Recent activity</h3>
            @for (e of history; track $index) { <div class="row" style="justify-content:space-between"><span>{{ label(e.type) }}</span><b class="okc">+{{ e.xp }} XP</b></div> }
            @if (!history.length) { <span class="muted">Complete a lesson, quiz or coding problem to earn XP.</span> }
          </div>
        </div>

        <div class="card"><h3>🏅 Badges</h3>
          @for (b of badges; track b.code) { <span class="badge" [class.locked]="!b.earned" [title]="b.description">{{ b.earned ? '🏅' : '🔒' }} {{ b.name }} - {{ b.description }}</span> }
        </div>
        <div class="card"><div class="row" style="justify-content:space-between">
          <h3>🎯 Your personal study plan</h3>
          <div class="row"><label class="muted">Explanation level</label>
            <select [ngModel]="me.user.level" (ngModelChange)="setLevel($event)" style="width:auto;margin:0"><option>BEGINNER</option><option>INTERMEDIATE</option><option>ADVANCED</option></select></div></div>
          <p class="muted">The level changes how the AI Tutor, summaries and this plan explain things (BEGINNER = key ideas only, INTERMEDIATE = full lesson, ADVANCED = full detail and harder practice).</p>
          @if (plan) {
            <p><b>{{ plan.headline }}</b></p>
            <p class="muted">{{ plan.stats }}</p>
            <div class="two">
              <div>@if (plan.strengths.length) { <h4>💪 Strengths</h4><ul>@for (s of plan.strengths; track $index) { <li>{{ s }}</li> }</ul> }
                @if (plan.focus.length) { <h4>🎯 Focus on</h4><ul>@for (s of plan.focus; track $index) { <li>{{ s }}</li> }</ul> }</div>
              <div><h4>✅ Next steps</h4><ol>@for (s of plan.steps; track $index) { <li style="margin-bottom:6px"><a [routerLink]="s.link">{{ s.text }}</a></li> }</ol></div>
            </div>
          }
          <div class="row"><button class="secondary" (click)="loadCoach()" [disabled]="coachBusy">{{ coachBusy ? 'Asking the AI…' : '✨ Add AI coaching tips (optional)' }}</button></div>
          @if (coach) { <pre style="white-space:pre-wrap">{{ coach }}</pre> }
          @if (coachErr) { <div class="muted">The AI coach is not available right now ({{ coachErr }}). Your plan above does not need it.</div> }
        </div>
      } @else {
        <div class="card"><h3>{{ me.user.role === 'ADMIN' ? 'All courses' : 'My courses' }}</h3>
          @for (c of courses; track c.id) { <div><a [routerLink]="['/courses', c.id]">{{ c.title }}</a></div> }
          @if (!courses.length) { <span class="muted">No courses yet. <a routerLink="/courses">Create one</a></span> }
        </div>
        <div class="card"><h3>Quick links</h3><div class="row">
          <a class="btn" routerLink="/courses">Manage courses</a><a class="btn secondary" routerLink="/code">Coding problems</a>
          @if (me.user.role === 'ADMIN') { <a class="btn secondary" routerLink="/admin">Admin panel</a> }</div></div>
      }
    }`
})
export class DashboardComponent implements OnInit {
  me: any; progress: any[] = []; perf: any; top: any[] = []; history: any[] = []; badges: any[] = []; courses: any[] = [];
  plan: any; coach = ''; coachErr = ''; coachBusy = false;
  constructor(private api: Api, public auth: AuthService) {}
  ngOnInit() {
    this.api.get('/me').subscribe(m => {
      this.me = m;
      this.api.get('/me/courses').subscribe(c => this.courses = c);
      if (m.user.role === 'STUDENT') {
        this.api.get('/me/progress').subscribe(p => this.progress = p);
        this.api.get('/me/performance').subscribe(p => this.perf = p);
        this.api.get('/leaderboard').subscribe(l => this.top = l.rows.slice(0, 5));
        this.api.get('/me/xp-history').subscribe(h => this.history = h);
        this.api.get('/me/badges').subscribe(b => this.badges = b);
        this.loadPlan();
      }
    });
  }
  label(t: string) { return ({ LESSON_COMPLETED: '✅ Lesson completed', QUIZ_PASSED: '📝 Quiz passed', COURSE_COMPLETED: '🎓 Course completed', PROBLEM_SOLVED: '💻 Problem solved', STREAK_BONUS: '🔥 Streak bonus' } as any)[t] || t; }
  loadPlan() { this.api.get('/me/recommendations').subscribe(p => this.plan = p); }
  setLevel(level: string) { this.api.put('/me/level', { level }).subscribe(() => { this.me.user.level = level; this.coach = ''; this.loadPlan(); }); }
  loadCoach() {
    this.coachErr = ''; this.coachBusy = true;
    this.api.get('/me/recommendations/ai').subscribe({
      next: r => { this.coach = r.text; this.coachBusy = false; },
      error: e => { this.coachErr = this.api.msg(e); this.coachBusy = false; } });
  }
}
