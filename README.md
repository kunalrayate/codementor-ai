# 🤖 CodeMentor AI - AI-Powered Learning Platform

> A full-stack, AI-powered learning platform built with **Java 21, Spring Boot, Spring AI, Hibernate/JPA, MySQL and Angular 18**. Designed to help students learn Java, practice coding, generate quizzes and flashcards, and track their learning progress through personalized study recommendations.

CodeMentor AI brings together interactive courses, an AI Tutor, coding challenges, PDF-based learning, gamified progress tracking and instructor tools in one platform. Students can learn at their own pace, practice programming problems, assess their understanding and improve their skills with AI-assisted guidance.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-blue)
![Angular](https://img.shields.io/badge/Angular-18-red)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)
![Security](https://img.shields.io/badge/Security-Spring%20Security%20%2B%20JWT-purple)
![Build](https://img.shields.io/badge/Build-Maven-C71A36)

---

## 📖 Overview

I built CodeMentor AI to explore how artificial intelligence can improve the traditional learning experience. Instead of creating a basic CRUD application, I wanted to build a real-world full-stack platform that combines education, programming practice, AI integration, authentication, and personalized learning.

The platform supports three roles: **Student, Instructor and Admin**. Students can enroll in courses, watch lessons, practice Java problems, interact with an AI Tutor, take quizzes, review flashcards, and monitor their progress. Instructors can create courses, publish lessons, manage learning materials, prepare quizzes and review student performance. Administrators manage users, courses, AI settings, gamification rules and platform-wide statistics.

The application integrates configurable AI providers, including Google Gemini, Groq, OpenAI, Ollama and compatible APIs. It also supports learning from uploaded PDF materials, coding evaluation, automated quiz generation and recommendations based on quiz results and coding performance.

## 🎯 Key Features

1. **AI-Powered Tutor:** Interact with an AI Tutor using text or voice, ask programming questions, adjust explanation levels and revisit previous conversations. The tutor can also use course notes and uploaded PDF content as context.

2. **Interactive Learning Courses:** Explore structured Java courses covering programming fundamentals, object-oriented programming, collections, generics, streams, exception handling, file I/O, threads and JDBC.

3. **AI Flashcards, Quizzes and Summaries:** Generate study materials from lessons, review important concepts, take quizzes and revise topics efficiently. Instructors can also create quizzes manually, review them and publish them.

4. **Java Coding Practice:** Solve programming challenges across six topics, execute Java code, evaluate results and receive compiler or runtime feedback. Students can request optional AI-generated hints and complexity guidance.

5. **PDF-Based Learning:** Upload PDF study materials, view extracted text, generate summaries and use document content as context for AI Tutor questions.

6. **Personalized Study Plans:** Receive study plans based on quiz scores, coding results, learning progress and selected explanation levels, with optional AI coaching tips.

7. **Student Dashboard and Progress Tracking:** Monitor course completion, learning progress, experience points (XP), streaks, badges and leaderboard rankings.

8. **Instructor Dashboard:** Create and manage courses, modules, lessons, video links, PDF materials and quizzes. Review student results, course analytics and leaderboard information.

9. **Admin Dashboard:** Manage users and courses, configure AI providers, monitor AI usage, customize XP rules, manage badges and view platform statistics.

10. **Secure Authentication:** Authenticate users with Spring Security and JWT, support refresh-token workflows and enforce role-based access control for protected features.

11. **Gamification and Leaderboards:** Encourage consistent learning through XP, badges, streaks, global rankings, course leaderboards and course-completion tracking.

12. **AI Reliability and Fallbacks:** Use configurable AI providers, request timeouts, usage limits and fallback learning features. Core learning content remains accessible even when an AI provider is not configured.

## 📚 Learning Modules

CodeMentor AI includes five Java courses with 39 lessons.

- **Java Programming:** Programming basics, object-oriented programming and collections.
- **Java Fundamentals:** Control flow, methods, arrays and strings.
- **Java OOP Deep Dive and Exception Handling:** Object-oriented concepts and exception handling.
- **Java Collections, Generics and Streams:** Collections framework, generics and stream operations.
- **Java Intermediate:** File I/O, multithreading and JDBC.

Each lesson supports learning notes, flashcards and a published quiz. Instructors can add video links and optional notes to extend course content.

## 🧠 AI and Code Evaluation

### AI Provider Integration

CodeMentor AI supports multiple language-model providers through configurable settings:

- Google Gemini
- Groq
- OpenAI
- Ollama for local models
- Custom OpenAI-compatible APIs

Administrators can configure an AI provider and test its connection from the AI Settings page. Provider credentials are stored encrypted in MySQL using AES-GCM, with the encryption key derived from the JWT secret configuration.

### Java Code Execution

The coding environment supports three execution modes:

- **Auto mode:** Uses Docker when available and otherwise falls back to local execution.
- **Docker mode:** Requires Docker and is the preferred mode for stronger isolation.
- **Local mode:** Executes Java in a separate process with a timeout, heap limit and source-code restrictions.

**Security note:** Local execution is not a security sandbox. Public deployments should use properly isolated execution infrastructure and additional security controls.

## 📄 What I Learned

Building CodeMentor AI helped me develop practical full-stack development skills and understand how different application components work together.

On the backend, I worked with Spring Boot to build REST APIs, Spring Security and JWT for authentication, and Hibernate/JPA for database operations. I also explored database versioning with Flyway, request validation, AI provider integration, usage limits, caching and backend error handling.

For AI integration, I explored Spring AI, configurable language-model providers, prompt-based learning assistance, PDF-based question answering, AI-generated learning materials and fallback responses when AI services are unavailable.

On the frontend, I worked with Angular 18 to build a role-based learning interface, connect REST APIs, manage authentication flows and present learning progress through dashboards and interactive components.

I also gained experience with Maven, MySQL, Docker Compose, environment-based configuration, Swagger API documentation and troubleshooting full-stack integration issues.

## 🧰 Technologies Used

- **Java 21:** Primary programming language for backend application development.
- **Spring Boot 3.4:** Framework for building REST APIs and application services.
- **Spring AI 1.0:** Integration layer for language models and AI-powered learning features.
- **Spring Security + JWT:** Authentication, authorization and protected API access, including refresh-token workflows.
- **Hibernate / Spring Data JPA:** Object-relational mapping and database interaction.
- **MySQL 8:** Relational database for users, courses, lessons, quizzes, progress and other application data.
- **Flyway:** Version-controlled database schema migrations.
- **Bucket4j:** Request rate limiting and AI usage protection.
- **Angular 18:** Frontend framework for the interactive learning application.
- **TypeScript, HTML and CSS:** Frontend logic, page structure and styling.
- **Maven:** Backend dependency and build management.
- **Docker and Docker Compose:** Optional containerized database and application setup.
- **Swagger / OpenAPI:** Interactive REST API documentation.

## 🏗️ Project Structure

```text
CodeMentor_AI/
├── backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       └── resources/
│   │           └── db/
│   │               └── migration/
│   └── pom.xml
├── frontend/
│   ├── src/
│   ├── package.json
│   └── angular.json
├── docker-compose.yml
├── .env.example
├── .gitignore
├── start-all.bat
├── run-backend.bat
├── run-frontend.bat
└── README.md
```

*Note: The structure above highlights the main directories and configuration files; individual source files may vary.*

## ⚙️ Prerequisites

Install the following software before running the application:

- JDK 21 or later
- Maven
- MySQL 8 or Docker Desktop
- Node.js 18 or later
- npm
- Git

## 🚀 How to Run the Project

### 1. Clone the repository

```bash
git clone https://github.com/kunalrayate/codementor-ai.git
cd codementor-ai
```

### 2. Configure the database

Start MySQL locally using your preferred database credentials, or start a MySQL container:

```bash
docker run -d --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  mysql:8.4
```

Configure the database connection and other required settings using the application's configuration files and environment variables.

If MySQL uses a different port, configure `DB_PORT` accordingly.

### 3. Configure the AI provider

The application can run without an AI API key using its built-in fallback learning features.

To enable AI-powered tutoring, quizzes, summaries and recommendations:

1. Obtain an API key from a supported provider, such as [Google AI Studio](https://aistudio.google.com/apikey) or [Groq](https://console.groq.com/keys).
2. Start the backend and log in using the administrator demo account.
3. Navigate to **Admin → AI Settings**.
4. Select a provider, enter the API key, save the settings and test the connection.

Alternatively, configure the supported AI environment variables listed in `.env.example`.

**Important:** Never commit real API keys, database passwords or JWT secrets to GitHub.

### 4. Run the backend

Open a terminal in the project root and execute:

```bash
cd backend
mvn spring-boot:run
```

The Spring Boot API starts at:

`http://localhost:8080`

Swagger API documentation is available at:

`http://localhost:8080/swagger-ui.html`

### 5. Run the frontend

Open another terminal in the project root and execute:

```bash
cd frontend
npm install
npm start
```

The Angular application runs at:

`http://localhost:4200`



## 🔑 Demo Accounts

The application provides demo accounts for testing the different roles. The login page includes one-click buttons to fill in the demo credentials.

| Role | Email | Password |
|---|---|---|
| Student | `student@learnai.com` | `Student@123` |
| Instructor | `instructor@learnai.com` | `Instructor@123` |
| Admin | `admin@learnai.com` | `Admin@123` |

**Security note:** These credentials are for local demonstration only. The application resets the demo passwords on startup by default. Disable that behavior and configure appropriate credentials before using real accounts.

## 🗄️ Database and API Documentation

- **Database:** MySQL 8
- **ORM:** Hibernate / Spring Data JPA
- **Schema migrations:** Flyway, with migration scripts V1–V12 in the documented project version
- **API documentation:** Swagger UI
- **Authentication:** JWT with refresh-token support
- **Rate limiting:** Bucket4j with in-memory limits and caches

The database stores application data related to users, courses, lessons, quizzes, coding results, learning progress, AI usage and gamification.

## 🔐 Security Considerations

- Use environment variables or secure configuration for secrets.
- Never publish real API keys or production credentials.
- Configure HTTPS and production-grade authentication settings before deployment.
- Prefer Docker or a dedicated isolated code-execution service for shared deployments.
- Review rate limits, resource limits and AI usage quotas before exposing the platform publicly.

## 📌 Future Enhancements

Potential areas for future development include:

- Advanced learning analytics and progress visualizations.
- More programming languages and coding challenges.
- Stronger isolated code execution using a dedicated judge service.
- Scalable vector search using a dedicated vector database.
- Additional course content and learning paths.
- Production deployment with monitoring and automated testing.

## 👨‍💻 Author

**Kunal Rayate**

- **Email:** kunalrayate126@gmail.com
- **GitHub:** [kunalrayate](https://github.com/kunalrayate)

---

⭐ If you find this project interesting, feel free to explore the repository and share your feedback.
