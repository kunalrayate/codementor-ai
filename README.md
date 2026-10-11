# AI-Powered Learning Platform (v2)

Spring Boot 3.4 · Spring AI 1.0 · Spring Security + JWT · Hibernate/JPA · MySQL 8 · Flyway · Bucket4j · Angular 18

```
ai-learning-platform/
├── backend/    Spring Boot API (Java 21, Maven)
├── frontend/   Angular 18 app
├── docker-compose.yml
└── .env.example   <- AI provider presets (OpenAI / Gemini / Groq / Ollama)
```

## IMPORTANT: always run backend and frontend from the SAME folder
The backend and the frontend are updated together. If you start the backend from a new folder but the frontend from an old one (or the browser keeps an old page), you see old screens and errors such as "Start the quiz first". To avoid this:
1. Stop both running windows (Ctrl+C).
2. Unzip the new version into a new folder and **double-click `start-all.bat`** (Windows) - it starts both from that folder. Edit `run-backend.bat` first if you need `JAVA_HOME` or `DB_PORT` (3307).
3. Open http://localhost:4200 and press **Ctrl+F5**.
The page now shows a yellow warning bar when the page and the backend are different versions or the backend is not running.

## Quick start (local, Windows / Mac / Linux)

```bash
# 1. MySQL 8 on localhost:3306 (user root / password root), e.g.
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8.4

# 2. Backend  (JDK 21 + Maven)
cd backend
#   set your AI provider first - see "AI provider setup" below
#   PowerShell:  $env:AI_API_KEY="..." ; $env:AI_BASE_URL="..." ; $env:AI_CHAT_PATH="..." ; $env:AI_MODEL="..."
mvn spring-boot:run

# 3. Frontend (Node 18+)
cd ../frontend && npm install && npm start       # http://localhost:4200
```
Swagger: http://localhost:8080/swagger-ui.html · Everything in Docker: `cp .env.example .env`, edit it, `docker compose up --build`.

**Demo accounts** (the login page has one-click fill buttons): `student@learnai.com / Student@123` · `instructor@learnai.com / Instructor@123` · `admin@learnai.com / Admin@123`.
On every start the backend re-creates missing demo users/content and resets these three demo passwords
(set `SEED_RESET_DEMO_PASSWORDS=false` once you use real accounts).

## AI provider setup (fixes "AI is not configured")
The AI Tutor, AI flashcards/quizzes/summaries and recommendations need an LLM. **You no longer need environment variables:**

1. Get a free key: **Google Gemini** -> https://aistudio.google.com/apikey (or **Groq** -> https://console.groq.com/keys).
2. Log in as **admin** (`admin@learnai.com` / `Admin@123`) -> **Admin -> AI Settings**.
3. Choose the provider, paste the key, click **Save**, then **Test connection**. It works immediately - no restart.

The key is stored encrypted in MySQL (AES-GCM, derived from `JWT_SECRET`). Environment variables (`AI_API_KEY`, `AI_BASE_URL`, ... see `.env.example`) still work as a fallback.
Providers: Gemini (free), Groq (free), OpenAI (paid), Ollama (local, free), or any OpenAI-compatible API ("Custom").
If the provider has no embedding endpoint (Groq), PDF Q&A automatically falls back to keyword search.

**Before a key is added the app still works:** lessons ship with flashcards and quizzes, flashcards fall back to fill-in-the-blank cards, summaries to a basic summary, and the AI Tutor to an *offline tutor* that quotes the course notes. A yellow banner tells users when this mode is active.
Only "Generate quiz with AI" and personalised AI study plans need a real key (instructors can still create quizzes manually).

## Code practice: Docker, auto and local mode
`SANDBOX_MODE` (default **auto**):
* **auto** - uses a Docker container when Docker Desktop is running (image `eclipse-temurin:21-jdk` is pulled automatically in the background the first time); otherwise runs code locally. The code page shows a banner with the active mode.
* **docker** - strict: always Docker (shows "Docker is not running" if it is not). Recommended for any shared/public deployment.
* **local** - always local. Local mode runs `javac`/`java` as a separate process with a timeout, a 64 MB heap limit and a source blocklist (no files, network, reflection, `Runtime`, `ProcessBuilder`). It is **not isolated** - only use it on your own machine.

There are **18 problems in 6 topics**; students pick a topic, solve problems, and solved ones get a check mark. Instructors add problems on the same page.

## Courses included (full beginner -> intermediate Java syllabus)
5 courses / 39 lessons, each lesson with notes, flashcards and a published quiz: *Java Programming* (basics, OOP, collections), *Java Fundamentals* (control flow, methods, arrays, strings), *Java OOP Deep Dive and Exception Handling*, *Java Collections, Generics and Streams*, *Java Intermediate: File I/O, Threads and JDBC*.
Instructors only need to add lessons/videos: in a course, **Add a lesson** asks for the title, the **video link** (with a "Find video on YouTube" helper button) and optional notes. Only the first three lessons ship with a verified video; others show a "Find a video" button until a link is added.

## Requirements coverage

| Requirement | Where |
|---|---|
| JWT + refresh tokens | `/api/auth/*`, Angular interceptor refreshes automatically |
| Student: courses, enroll, video lessons, resume, completion, progress | Courses, Lesson page (YouTube/Vimeo/mp4), Dashboard progress bars |
| Instructor: courses, modules, lessons, video URLs, materials (PDF), quizzes (manual + AI), review/publish, analytics, course leaderboard | Course page, Lesson page, Tutor page (PDF upload, share with a course) |
| Admin: users, courses, AI usage, stats, XP monitoring, **leaderboard rules**, badges, global leaderboard | Admin page (editable XP rules), Courses (delete) |
| AI Tutor text + voice, levels, history, RAG over PDFs | Tutor page |
| Flashcards, quizzes (draft → published), summaries, caching | Lesson page |
| Java code evaluator + sandbox + AI hints/complexity | Code page |
| XP, badges, streaks, SQL leaderboards (global/course, courses completed) | Dashboard, Leaderboard |
| Recommendations from quiz **and** coding results | Dashboard (+ non-AI fallback) |
| Guardrails, Bucket4j, daily quota, token tracking | `AiGateway`, `AiGuardrailService`, Admin AI usage |
| Flyway (V1-V12), Swagger | `db/migration`, `/swagger-ui.html` |

## Notes / differences from the spec
* One role per user (`users.role`) instead of `roles`/`user_roles` tables. Redis is optional in the spec and not used (in-memory Bucket4j/caches).
* Vector search is stored in MySQL (JSON + cosine similarity) - swap for Spring AI `VectorStore` (PGVector, Qdrant) at scale.
* Seed videos: only the Java Basics lessons ship with a verified video. For other lessons, an instructor pastes a link in "Edit lesson"; until then students see a "Find a video on YouTube" button.
* Docker alone is not enough isolation for a public code-execution service (consider gVisor/Firecracker or Judge0).
* The backend could not be compiled in the authoring environment (no Maven Central access); sources pass a Java syntax check and the Angular app builds with `ng build`. If `mvn` reports a compile error, send me the message.

## What's new in v11
* **Single attempt per topic (quiz attempt limits)**. Each quiz has an *attempts per student* setting: unlimited (default), **single attempt** (locks after completion), or 2/3 attempts. Instructors set it on the quiz review page (or for a whole course with the **Single attempt for all quizzes** button on the course page).
* Students see **Status / Attempt (e.g. 1/1) / Access (Open or Locked)** for each quiz - on the lesson page and on the new **My Quizzes** page. A locked quiz cannot be opened or started again; this is enforced on the server (start and submit), an unfinished single-attempt quiz resumes with the same questions after a refresh, and a start screen warns that it can be taken only once.
* Instructors/admins see every student's result on the quiz review page and can **Allow retake** for one student. Database migration V12 adds `quizzes.max_attempts`.

## What's new in v10
* Version guard (yellow bar), `start-all.bat` / `run-backend.bat` / `run-frontend.bat` (and `.sh`), and a clearer message when an old page talks to the new backend. Old chats that contain a raw "503" error are shown as a short sentence.

## What's new in v9
* **Quiz shuffling**: every attempt draws a fresh random set (5 questions, `app.quiz.questions-per-attempt`) from the lesson's question bank - the quiz's own questions plus questions built from the lesson's flashcards - and shuffles the answer options. The served questions are stored per attempt (table `quiz_attempts`), so grading cannot be tampered with and an attempt can be submitted only once. After a result, **Try again with new questions** starts a new attempt. Lessons with an AI-generated or manually created quiz get an even bigger bank.
* **AI Tutor chats**: a "My chats" list with **Delete** per chat and **Delete all chats**; the offline tutor no longer shows raw provider errors.

## What's new in v8
* **Lesson videos work without any Google setup**: if the official YouTube API is not enabled, the backend finds a video through YouTube's public search page and verifies it with YouTube's oEmbed (only existing, embeddable videos are accepted). Lessons are filled in the background right after start-up and the link is saved on the lesson, so clicking a lesson starts its video. This fallback is unofficial; switch it off with `VIDEO_WEB_FALLBACK=false` and use the official API (Admin -> Lesson videos) for production.
* **AI never hangs**: each AI call times out after 25 s, there is no hidden retry, other models are tried within a 40 s budget, and the tutor then answers from the course notes instead of showing "took too long". The Gemini default is now the fast `gemini-flash-lite-latest`; embeddings time out after 8 s and PDF search falls back to keywords.

## What's new in v7
* **Videos start automatically**: opening a lesson plays its video (autoplay). Lessons without a link get one found by the backend through the official **YouTube Data API** and saved on the lesson. One-time setup (Admin -> *Lesson videos*): enable *YouTube Data API v3* in the same Google Cloud project as your Gemini key (https://console.cloud.google.com/apis/library/youtube.googleapis.com), optionally paste a key (empty = reuse the Gemini key), press **Test**, then **Fill videos for all lessons**. Instructors can still paste their own link per lesson.
* **Study plan without AI**: computed from the student's quiz scores, coding results and progress, and adapted to the explanation level (Beginner / Intermediate / Advanced). AI coaching tips are an optional extra button.
* **Explanation level** also changes AI Tutor answers, summaries (prompted for that level) and the offline tutor (Beginner = key idea only).
* **Code results**: a pass shows only "PASSED 3/3". A failure shows compiler messages / exceptions with the line number and a plain-English fix (no AI needed). "Ask the AI for a hint" is optional.
* **Summaries are cached** per lesson/type/level (one AI call, then instant) and the AI gateway tries up to 5 models with short pauses when Gemini returns 503/429.
* **Courses**: admin and the owning instructor can create, edit and delete courses.

## What's new in v6
* **New theme**: sidebar layout, gradient accents, cards, animations and a **light/dark mode toggle** (top right, remembered per browser).
* **PDF materials**: uploaded PDFs are stored and can be **viewed in the page** (original PDF + extracted text + summary), summarised, used as tutor context or deleted.
* **AI reliability**: requests time out after 60 s and retry once (no more endless "Thinking..."). If a model is retired, over quota (429) or overloaded, the backend **automatically tries other models** your key can use (Gemini free tier has per-model limits) and remembers the one that works.
* **Admin badges**: create, edit and delete badges; optional **auto-award at N total XP**.
* **Tutor**: suggested questions for the current lesson, clearer microphone errors (voice input needs Chrome/Edge and microphone permission).
* Uploaded PDFs are saved in `backend/uploads` (set `UPLOADS_DIR` to change).

## Troubleshooting
| AI says the quota/rate limit is used up (429) | Free tiers allow only a few requests per minute and per day. The app now tries other models automatically; if every model is exhausted wait a minute, or pick another model/provider in Admin -> AI Settings. Avoid clicking several AI buttons at once. |
| "Model or endpoint not found" | Model names change. Admin -> AI Settings -> **Load available models**, pick one, Save, Test. The default is now the always-current alias `gemini-flash-latest`, and the backend auto-switches to an available model when it gets a 404. |
| Study plan "AI unavailable" / Refresh does nothing | Fixed: fallback plans are no longer cached; **Refresh plan** always regenerates and uses your quiz/coding results and explanation level. |
| Run shows `NoSuchElementException` | The program read input that was empty. The input box is now pre-filled with the sample input (button *Use sample input*). |
| `Unknown column 'optiona'` on start | Fixed in v3 (explicit column names). |
| MySQL on another port (e.g. 3307) | Set `DB_PORT=3307` (and `DB_HOST`, `DB_USER`, `DB_PASSWORD` if different). |
| Symptom | Fix |
|---|---|
| "Invalid email or password" | Use the one-click demo buttons (watch for typos; `Instructor@123` has 14 characters). Restart the backend to reset demo passwords. |
| "AI is not configured" | Admin -> AI Settings (see above). |
| "Docker is not running" | Not needed in `auto` mode. For the secure sandbox start Docker Desktop. |
| Video does not play | Some YouTube videos disallow embedding - use the "Open on YouTube" link, or paste another URL. |
