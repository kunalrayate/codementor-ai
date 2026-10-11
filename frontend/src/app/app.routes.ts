import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { LoginComponent } from './pages/login.component';
import { DashboardComponent } from './pages/dashboard.component';
import { CoursesComponent } from './pages/courses.component';
import { CourseDetailComponent } from './pages/course-detail.component';
import { LessonComponent } from './pages/lesson.component';
import { TutorComponent } from './pages/tutor.component';
import { QuizComponent } from './pages/quiz.component';
import { ReviewComponent } from './pages/review.component';
import { LeaderboardComponent } from './pages/leaderboard.component';
import { QuizzesComponent } from './pages/quizzes.component';
import { CodeComponent } from './pages/code.component';
import { AdminComponent } from './pages/admin.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'courses', component: CoursesComponent, canActivate: [authGuard] },
  { path: 'courses/:id', component: CourseDetailComponent, canActivate: [authGuard] },
  { path: 'lessons/:id', component: LessonComponent, canActivate: [authGuard] },
  { path: 'tutor', component: TutorComponent, canActivate: [authGuard] },
  { path: 'quizzes', component: QuizzesComponent, canActivate: [authGuard] },
  { path: 'quiz/:id', component: QuizComponent, canActivate: [authGuard] },
  { path: 'review/:id', component: ReviewComponent, canActivate: [authGuard] },
  { path: 'leaderboard', component: LeaderboardComponent, canActivate: [authGuard] },
  { path: 'leaderboard/:courseId', component: LeaderboardComponent, canActivate: [authGuard] },
  { path: 'code', component: CodeComponent, canActivate: [authGuard] },
  { path: 'admin', component: AdminComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
