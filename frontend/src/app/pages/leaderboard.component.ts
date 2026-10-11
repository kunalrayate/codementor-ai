import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Api } from '../core/api.service';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  template: `
    <h2>🏆 {{ courseId ? 'Course' : 'Global' }} leaderboard</h2>
    @if (data) {
      @if (data.me) { <div class="card"><b>Your rank: {{ data.me.rank ? '#' + data.me.rank : 'unranked' }}</b> · {{ data.me.xp }} XP · {{ data.me.courses }} course(s) completed</div> }
      <div class="card"><table>
        <tr><th>Rank</th><th>Student</th><th>XP</th><th>Courses completed</th></tr>
        @for (r of data.rows; track r.studentId) { <tr [style.font-weight]="r.studentId === data.me?.studentId ? 700 : 400"><td>{{ r.rank === 1 ? '🥇' : r.rank === 2 ? '🥈' : r.rank === 3 ? '🥉' : r.rank }}</td><td>{{ r.name }}</td><td>{{ r.xp }}</td><td>{{ r.courses }}</td></tr> }
      </table>
      @if (!data.rows.length) { <p class="muted">No XP earned yet.</p> }</div>
    }
    @if (err) { <div class="err">{{ err }}</div> }`
})
export class LeaderboardComponent implements OnInit {
  data: any; err = ''; courseId: string | null = null;
  constructor(private route: ActivatedRoute, private api: Api) {}
  ngOnInit() {
    this.courseId = this.route.snapshot.paramMap.get('courseId');
    this.api.get(this.courseId ? `/courses/${this.courseId}/leaderboard` : '/leaderboard').subscribe({ next: d => this.data = d, error: e => this.err = this.api.msg(e) });
  }
}
