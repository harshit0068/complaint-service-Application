# HostelFix

HostelFix is a local full-stack hostel complaint-management application made of
three independently runnable parts:

- `frontend/` — React/Vite interface for students, staff and wardens.
- This project root — Complaint Service on port `8081`.
- `user-service/` — account, role and notification service on port `8082`.

Both services use the existing MySQL database named `hostelfix`. The User
Service owns the `users` and `notifications` tables; the Complaint Service owns
the `complaints` table. Hibernate creates or updates these tables at startup.

## Prerequisites

- Java 21 or later and Maven 3.9+
- Node.js 20.19+ and npm
- MySQL running locally, with a `hostelfix` database

The Complaint Service keeps the MySQL connection already configured in
`src/main/resources/application.properties`. The User Service reads
`HOSTELFIX_DB_URL`, `HOSTELFIX_DB_USER` and `HOSTELFIX_DB_PASSWORD`; set these to
the same MySQL connection values. For the default local setup:

```powershell
$env:HOSTELFIX_DB_URL = "jdbc:mysql://localhost:3306/hostelfix"
$env:HOSTELFIX_DB_USER = "root"
$env:HOSTELFIX_DB_PASSWORD = "your-mysql-root-password"
```

If MySQL root has no password, set `HOSTELFIX_DB_PASSWORD` to an empty string.

## Start the application

Open three PowerShell terminals, all using this project directory.

**Terminal 1 — User and Notification Service**

```powershell
cd "c:\Users\harsh\Downloads\complaint-service\complaint-service"
$env:HOSTELFIX_DB_URL = "jdbc:mysql://localhost:3306/hostelfix"
$env:HOSTELFIX_DB_USER = "root"
$env:HOSTELFIX_DB_PASSWORD = "your-mysql-root-password"
$env:HOSTELFIX_BOOTSTRAP_EMAIL = "warden@srmist.edu.in"
$env:HOSTELFIX_BOOTSTRAP_PASSWORD = "choose-a-password-at-least-8-chars"
cd .\user-service
mvn spring-boot:run
```

The bootstrap account is created as a Warden the first time the service starts.
Keep the password private. To reset the existing Warden's email and password,
set `$env:HOSTELFIX_BOOTSTRAP_RESET = "true"` before starting the User Service.
The reset updates the Warden already using the configured email, or the only
existing Warden; it stops with an error if multiple Warden accounts make the
target ambiguous. Remove the reset variable after the reset. If bootstrap
variables are omitted, students can register, but a Warden account must be
configured before staff-management and reporting features are available.

**Terminal 2 — Complaint Service**

Stop any existing IntelliJ run first if port `8081` is already in use.

```powershell
cd "c:\Users\harsh\Downloads\complaint-service\complaint-service"
mvn spring-boot:run
```

**Terminal 3 — React app**

```powershell
cd "c:\Users\harsh\Downloads\complaint-service\complaint-service\frontend"
npm install
npm run dev
```

Open **http://localhost:5173**. The backend health checks are
`http://localhost:8081/api/health` and `http://localhost:8082/api/health`.

## What you can do

- Students register, submit complaints by category and hostel block, optionally
  attach a photo, track status, and rate resolved complaints.
- Wardens sign in, create staff accounts with a work specialty, assign complaints
  to the right team member and view average resolution time by category and open
  issues by block. Existing staff specialties can be set or changed from the
  People & team page.
- Assigned staff update complaint status and must add a resolution note to
  close an issue.
- Status changes and assignments create in-app notifications.

The services share `HOSTELFIX_JWT_SECRET` and
`HOSTELFIX_INTERNAL_SECRET`. Their matching local development defaults make
local startup simple; configure long, random values through environment
variables before exposing the services outside local development.

Uploaded photos are stored in the local `uploads/` folder for development.
