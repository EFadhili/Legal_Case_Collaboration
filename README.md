# Legal Case Management Platform

## AI-Assisted Legal Case Management and Collaboration Platform

**Built with Spring Boot | Deployed on AWS | Powered by Gemini AI | Fully Audited for Compliance**

---

## Overview

The Legal Case Management Platform is a comprehensive backend system designed to support legal professionals in managing cases, coordinating workflows, collaborating with team members, interacting with legal documents, and leveraging AI-powered assistance.

Built with Spring Boot 3.3.5 and Java 17, the system combines structured case management, task-driven workflows, real-time collaboration tools, document management with AWS S3, and a prompt-based AI assistant powered by Google Gemini 2.5 Flash.

All actions are fully audited for compliance with legal industry regulations, with a 7-year audit retention policy.

---

## Technology Stack

| Category | Technology | Version |
|----------|------------|---------|
| Framework | Spring Boot | 3.3.5 |
| Language | Java | 17 |
| Build Tool | Maven | 3.8+ |
| Database | PostgreSQL | 15+ |
| ORM | Hibernate / JPA | 6.5.3 |
| Security | Spring Security + JWT | 0.12.5 |
| WebSocket | STOMP | Built-in |
| Cloud Storage | AWS S3 | 1.12.600 |
| AI | Google Gemini 2.5 Flash | REST API |
| Document Processing | Apache PDFBox, Apache POI | 3.0.0, 5.2.5 |
| API Documentation | SpringDoc OpenAPI | 2.6.0 |

---

## Features

### User Management

- JWT-based authentication with BCrypt password encoding
- Login supports both username AND email
- Role-based access control (ADMIN, LAWYER, STAFF)
- User registration with email/username validation
- Profile management (update name, email, change password)
- Soft delete with 30-day retention (admin reactivation available)
- Admin user management (activate, deactivate, role change, delete)
- User search and autocomplete with partial matching

### Case Management

- Full case lifecycle (OPEN → IN_PROGRESS → CLOSED → ARCHIVED)
- Case priority (LOW, MEDIUM, HIGH, URGENT)
- Case types (CIVIL, CRIMINAL, CORPORATE, FAMILY, etc.)
- Case member management with roles (LAWYER, STAFF)
- Soft locking on case closure
- Case number generation (CASE-YYYY-XXXXX)
- Transition validation with business rules
- Progress tracking based on mandatory tasks
- Case search by title, case number, description
- Soft delete with 30-day retention (admin restore)

### Task Management

- Task types (MANDATORY, OPTIONAL, REVIEW)
- Task priority (LOW, MEDIUM, HIGH, URGENT)
- Task status workflow (TODO → IN_PROGRESS → REVIEW → COMPLETED)
- Task dependencies
- Approval workflow (staff → REVIEW, lawyer → COMPLETED)
- Progress tracking (0-100%)
- Task number generation (TASK-YYYY-CASEID-SEQUENCE)
- Task assignment by username or email
- Task search by title or task number
- Escalation rules for overdue tasks with automatic notifications
- Soft delete with 30-day retention

### Chat System

- Real-time messaging with WebSocket (STOMP protocol)
- Case-based chat rooms
- User mentions (@username or @email)
- Task mentions (#taskNumber or #taskId)
- Read/unread message tracking
- Unread counts per case
- Message edit with history tracking (10-minute window for authors)
- Message soft delete (5-minute window for authors)
- Permission model: Admin/Lawyer can delete/edit any message
- REST + WebSocket dual approach

### Comments System

- Case-level and task-level comments
- Threaded replies with type inheritance
- User mentions (@username or @email)
- Permission-based edit/delete (Admin/Lawyer, authors within time limits)
- Soft delete with 30-day retention
- Edit tracking with edit history
- Replies remain visible under deleted parent comments

### Document Management

- Upload documents (PDF, DOCX, TXT, XLSX) to cases or tasks
- AWS S3 storage with presigned URLs (24-hour access)
- Async text extraction (5-minute timeout, 3 retry attempts)
- Document versioning
- Soft delete with 30-day retention
- Document number generation (DOC-YYYY-XXXXX)
- Extracted text storage for AI processing
- Search: case-scoped, task-scoped, user's own documents, admin global search
- Edit tracking for metadata changes
- Permission model: Admin/Lawyer/Uploader can delete

### AI Assistant (Gemini 2.5 Flash)

- Prompt-based legal document analysis
- Document summarization, clause extraction, risk analysis, contract review
- Multi-turn conversations with history
- Interaction history with user rating (1-5 stars)
- Soft delete with 30-day retention
- Interaction number generation (AI-YYYY-XXXXX)
- WebSocket streaming for long responses
- Search: my interactions, case-scoped, admin global search
- Edit tracking for rating changes

### Notifications

- 25+ notification types covering all modules:
  - User added/removed from case
  - Task assigned, completed, deadline approaching, overdue
  - User/task mentioned in chat or comments
  - New message in case chat
  - New comment on case/task
  - Document uploaded/processed
  - AI analysis complete
- Real-time WebSocket delivery
- User-specific topics: /topic/notifications/{userId}
- Case-specific topics: /topic/cases/{caseId}/notifications
- Read/unread/archive status
- Paginated notification retrieval
- Batch operations (mark as read, archive, delete)
- 30-day retention for read/archived notifications

### Audit & Compliance

- Complete audit trail for all system actions (50+ action types)
- Tracks: who, what, when, IP address, user agent
- Before/after state capture for updates
- Async audit logging (non-blocking)
- Admin audit search with filters:
  - By user, action, entity type, date range, status
- 7-year audit retention policy (configurable)
- Automated cleanup scheduler for expired audits

### Security

- JWT token authentication (24-hour expiration)
- BCrypt password encryption
- Role-based authorization (ADMIN > LAWYER > STAFF)
- Case membership verification for all case-related operations
- WebSocket authentication interceptor
- Soft delete with retention policies across all modules

---

## Architecture

The backend follows a layered architecture:

**Client Layer** (React/Mobile) → **Controller Layer** (REST APIs) → **Service Layer** (Business Logic) → **Repository Layer** (Data Access) → **PostgreSQL Database**

### Key Design Decisions

- **Identifier-based APIs**: All endpoints accept both numeric IDs and human-readable identifiers (case numbers, task numbers, document numbers, usernames/emails)
- **JOIN FETCH**: All repository methods use JOIN FETCH or @EntityGraph to prevent LazyInitializationException
- **DTO Pattern**: Entities never exposed directly to clients
- **Custom Exceptions**: Proper error handling with meaningful HTTP status codes
- **Async Processing**: Document text extraction and audit logging run asynchronously
- **Soft Delete**: All modules implement soft delete with configurable retention periods
- **Edit Tracking**: All modules track who made changes and when

---

## Database Schema

### Core Tables (11 tables)

| Table | Description |
|-------|-------------|
| users | User accounts with roles and authentication |
| legal_cases | Legal cases with lifecycle tracking |
| case_members | Many-to-many mapping of users to cases |
| tasks | Tasks within cases with workflow status |
| comments | Case and task comments with threading |
| chat_messages | Real-time chat messages |
| notifications | In-app notifications for users |
| documents | File metadata and extracted text |
| ai_interactions | AI query history and responses |
| audit_logs | Complete audit trail of all actions |

### Key Relationships

```
User
  ├── LegalCase (owner)
  ├── CaseMember (member of)
  ├── Task (created by / assigned to)
  ├── Comment (author)
  ├── ChatMessage (sender)
  ├── Notification (recipient)
  ├── Document (uploader)
  ├── AIInteraction (user)
  └── AuditLog (actor)

LegalCase
  ├── Task (belongs to)
  ├── Comment (on case)
  ├── ChatMessage (in case chat)
  ├── Document (attached to case)
  └── AIInteraction (context)

Task
  ├── Comment (on task)
  ├── Document (attached to task)
  └── Task (depends on)

Document
  ├── Document (version/parent relationship)
  └── AIInteraction (context for analysis)

Notification ─── User (recipient)

AuditLog ─── User (actor)
```

---

## API Documentation

Interactive API documentation is available via Swagger UI at: `http://localhost:8080/api/swagger-ui.html`

### Main API Endpoints

| Module | Base Path | Description |
|--------|-----------|-------------|
| Authentication | /auth | User registration, login, profile |
| User Dashboard | /user | Profile update, password change |
| Admin User | /admin/users | User management (admin only) |
| User Search | /users | Search users by username/email |
| Case Management | /cases | CRUD operations for legal cases |
| Task Management | /tasks | Task creation, assignment, tracking |
| Chat | /chat | Real-time messaging (REST) |
| Comments | /comments | Case and task comments |
| Documents | /documents | Upload, download, manage documents |
| AI Assistant | /ai | AI-powered document analysis |
| Notifications | /notifications | User notifications |
| Audit | /admin/audit | Audit log search (admin only) |

### WebSocket Endpoints

| Destination | Description |
|-------------|-------------|
| /ws/chat | WebSocket endpoint for chat |
| /app/chat/{caseId}/send | Send chat message |
| /app/chat/{caseId}/edit/{messageId} | Edit message |
| /app/chat/{caseId}/delete/{messageId} | Delete message |
| /app/ai/stream/{sessionId} | Stream AI response |
| /topic/notifications/{userId} | User notifications |
| /topic/cases/{caseId}/notifications | Case notifications |

---

## Getting Started

### Prerequisites

- Java 17
- Maven 3.8+
- PostgreSQL 15+
- AWS Account (for S3 storage)
- Gemini API Key (from Google AI Studio)

### Installation Steps

**1. Clone the repository**

```bash
git clone https://github.com/yourusername/legalcase-platform.git
cd legalcase-platform
```

**2. Create PostgreSQL database**

```sql
CREATE DATABASE legalcase_db;
CREATE USER legalcase_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE legalcase_db TO legalcase_user;
```

**3. Configure application.properties**

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/legalcase_db
spring.datasource.username=legalcase_user
spring.datasource.password=your_password

# JWT
app.jwt.secret=your_jwt_secret_key_at_least_32_characters
app.jwt.expiration=86400000

# AWS S3
cloud.aws.region=eu-north-1
cloud.aws.s3.bucket=your-bucket-name

# Gemini AI
gemini.api.key=your_gemini_api_key

# Audit Retention
audit.retention.years=7
```

**4. Set environment variables**

```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export GEMINI_API_KEY=your_gemini_api_key
```

**5. Run the application**

```bash
./mvnw spring-boot:run
```

**6. Access the API**

- Base URL: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`

---

## Security

### Authentication Flow

1. User registers → Password encoded with BCrypt
2. User logs in (username OR email) → Server validates credentials
3. Server returns JWT token (24-hour expiration)
4. Client includes token in Authorization header
5. JWT filter validates token on each request

### Authorization Rules

| Role | Permissions |
|------|-------------|
| **ADMIN** | Full system access, user management, view all audit logs, restore deleted items |
| **LAWYER** | Create cases, manage tasks, approve tasks, add members, access case documents |
| **STAFF** | Work on assigned tasks, comment, chat, upload documents to cases they're members of |

### Case Membership Rules

- Only case members can view case details
- Only case lawyers can modify case settings
- Only case lawyers can add/remove members
- Locked cases prevent modifications

---

## Audit & Compliance

### What Gets Audited (50+ Action Types)

| Category | Actions |
|----------|---------|
| Authentication | LOGIN_SUCCESS, LOGIN_FAILURE, PASSWORD_CHANGE, PASSWORD_RESET, LOGOUT, ACCESS_DENIED |
| User Management | USER_CREATE, USER_UPDATE, USER_DELETE, USER_ACTIVATE, USER_DEACTIVATE, USER_ROLE_CHANGE |
| Case Management | CASE_CREATE, CASE_UPDATE, CASE_STATUS_CHANGE, CASE_PRIORITY_CHANGE, CASE_DELETE, CASE_RESTORE, CASE_MEMBER_ADD, CASE_MEMBER_REMOVE, CASE_LOCK, CASE_UNLOCK |
| Task Management | TASK_CREATE, TASK_UPDATE, TASK_STATUS_CHANGE, TASK_PROGRESS_UPDATE, TASK_ASSIGN, TASK_DELETE, TASK_RESTORE |
| Document Management | DOCUMENT_UPLOAD, DOCUMENT_UPDATE, DOCUMENT_DOWNLOAD, DOCUMENT_DELETE, DOCUMENT_RESTORE, DOCUMENT_PROCESS |
| Chat | CHAT_MESSAGE_SEND, CHAT_MESSAGE_EDIT, CHAT_MESSAGE_DELETE, CHAT_MESSAGE_RESTORE |
| Comments | COMMENT_CREATE, COMMENT_UPDATE, COMMENT_DELETE, COMMENT_RESTORE |
| AI Assistant | AI_QUERY, AI_RATING |
| Notifications | NOTIFICATION_SEND, NOTIFICATION_READ, NOTIFICATION_DELETE |

### Audit Log Fields

| Field | Description |
|-------|-------------|
| id | Unique audit log identifier |
| userId | ID of user who performed the action |
| userIdentifier | Email/username of the user |
| userName | Full name of the user |
| action | Action performed (50+ types) |
| entityType | Type of entity affected |
| entityId | ID of the specific entity |
| entityIdentifier | Human-readable identifier (case number, etc.) |
| beforeValue | JSON snapshot BEFORE the action |
| afterValue | JSON snapshot AFTER the action |
| details | Additional contextual information |
| ipAddress | IP address of the user |
| userAgent | Browser/device information |
| status | SUCCESS or FAILURE |
| errorMessage | Error details if status = FAILURE |
| createdAt | Timestamp of the action |

### Retention Policies

| Module | Retention Period | Cleanup Action |
|--------|-----------------|----------------|
| Audit Logs | 7 years | Auto-delete after 7 years |
| Soft-deleted Users | 30 days | Auto-permanent delete after 30 days |
| Soft-deleted Cases | 30 days | Auto-permanent delete after 30 days |
| Soft-deleted Tasks | 30 days | Auto-permanent delete after 30 days |
| Soft-deleted Documents | 30 days | Auto-permanent delete after 30 days |
| Soft-deleted Chat Messages | 30 days | Auto-permanent delete after 30 days |
| Soft-deleted Comments | 30 days | Auto-permanent delete after 30 days |
| Soft-deleted AI Interactions | 30 days | Auto-permanent delete after 30 days |
| Read/Archived Notifications | 30 days | Auto-delete after 30 days |

---

## Deployment

### Docker Build

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/legalcase-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Docker Compose

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: legalcase_db
      POSTGRES_USER: legalcase_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - redis
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/legalcase_db
      SPRING_DATASOURCE_USERNAME: legalcase_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
```

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| DB_PASSWORD | PostgreSQL database password | Yes |
| JWT_SECRET | Secret key for JWT signing (min 32 chars) | Yes |
| AWS_ACCESS_KEY_ID | AWS access key for S3 | Yes (if using S3) |
| AWS_SECRET_ACCESS_KEY | AWS secret key for S3 | Yes (if using S3) |
| AWS_REGION | AWS region for S3 bucket | Yes (if using S3) |
| S3_BUCKET | S3 bucket name for document storage | Yes (if using S3) |
| GEMINI_API_KEY | Google Gemini API key | Yes (if using AI) |

---

## Project Structure

```
src/main/java/com/legalcase/
├── annotation/          # Custom annotations (@Auditable)
├── aspect/              # AOP aspects (AuditAspect)
├── config/              # Configuration classes
├── controller/          # REST controllers (15+ controllers)
├── dto/                 # Data Transfer Objects
├── entity/              # JPA entities (11 entities)
├── enums/               # Enum types (15+ enums)
├── event/               # Application events
├── exception/           # Custom exceptions & handler
├── repository/          # JPA repositories (10 repositories)
├── scheduler/           # Scheduled jobs (cleanup tasks)
├── security/            # Security components
├── service/             # Business logic (12 services)
└── util/                # Utility classes
```

---

## Performance Considerations

| Area | Strategy |
|------|----------|
| Database | Indexes on foreign keys and frequently queried fields |
| Lazy Loading | JOIN FETCH and @EntityGraph to prevent LazyInitializationException |
| Async Processing | Document text extraction and audit logging run asynchronously |
| Pagination | All list endpoints support page/size parameters |
| Real-time | WebSocket for chat and notifications |
| Caching | Redis ready for future caching implementation |
| Threading | Custom thread pools for async operations |

---

## Testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=UserServiceTest

# Run integration tests
./mvnw test -Dtest=*IntegrationTest
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Coding Standards

- Use meaningful variable and method names
- Add JavaDoc comments for public methods
- Write unit tests for new features
- Follow existing package structure
- Always add audit logging for state-changing operations
- Use `String userIdentifier` (not `Long userId`) for user identification

---

## Author

- **Elton Fadhili Mumalasi**
  - GitHub: https://github.com/EFadhili
---

## Acknowledgments

- Spring Boot team for the excellent framework
- Google for Gemini AI API
- AWS for cloud infrastructure

---

**Built with Spring Boot | Deployed on AWS | Powered by Gemini AI | Fully Audited for Compliance**
