# Deployment Guide — Ink&Pages E-Commerce Bookstore

This guide takes the application from source code to a live, publicly accessible URL using:

- **Railway** — Backend (Spring Boot) + Database (PostgreSQL)
- **Vercel** — Frontend (React + Vite)

Estimated time: **30–45 minutes**

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Overview of What We Are Deploying](#2-overview)
3. [Step 1 — Prepare the Backend for Production](#3-step-1--prepare-the-backend-for-production)
4. [Step 2 — Deploy Backend + Database on Railway](#4-step-2--deploy-backend--database-on-railway)
5. [Step 3 — Deploy Frontend on Vercel](#5-step-3--deploy-frontend-on-vercel)
6. [Step 4 — Connect Frontend to Backend (CORS)](#6-step-4--connect-frontend-to-backend-cors)
7. [Step 5 — Verify Everything Works](#7-step-5--verify-everything-works)
8. [Redeployment (After Code Changes)](#8-redeployment-after-code-changes)
9. [Environment Variable Reference](#9-environment-variable-reference)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites

Before starting, make sure you have:

- [ ] Code pushed to GitHub (`main` branch) — **already done**
- [ ] A free [Railway](https://railway.app) account — sign up with GitHub
- [ ] A free [Vercel](https://vercel.com) account — sign up with GitHub
- [ ] Git installed locally

---

## 2. Overview

```
GitHub (main branch)
       │
       ├──► Railway ──► Spring Boot Backend  ──► https://your-app.up.railway.app
       │         └────► PostgreSQL Database
       │
       └──► Vercel  ──► React Frontend        ──► https://your-app.vercel.app
```

Both platforms watch your GitHub `main` branch. Every `git push` triggers an automatic redeploy — no manual steps needed after initial setup.

---

## 3. Step 1 — Prepare the Backend for Production

Before deploying, we need to make two small code changes.

### 3a — Add a `PORT` environment variable to `application.properties`

Railway assigns a dynamic port via the `PORT` environment variable. Update the server port setting so it reads from that env var:

Open `backend/src/main/resources/application.properties` and change:

```properties
# BEFORE
server.port=8080

# AFTER
server.port=${PORT:8080}
```

This means: use the `PORT` env var if Railway sets it, otherwise fall back to 8080 locally.

### 3b — Update the Frontend API URL

Open `frontend/src/api.js` and change the `BASE` URL to use an environment variable:

```js
// BEFORE
const BASE = 'http://localhost:8080/api';

// AFTER
const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
```

### 3c — Commit and push both changes

```powershell
cd C:\Users\Harsh\Desktop\ecommerce-bookstore

git add backend/src/main/resources/application.properties
git add frontend/src/api.js
git commit -m "config: dynamic PORT for Railway, VITE_API_URL for Vercel"
git push origin main
```

---

## 4. Step 2 — Deploy Backend + Database on Railway

### 4a — Create a Railway account

1. Go to **https://railway.app**
2. Click **Login** → **Login with GitHub**
3. Authorize Railway to access your GitHub account

### 4b — Create a new project

1. From the Railway dashboard, click **New Project**
2. Select **Deploy from GitHub repo**
3. Find and select **`ecommerce-bookstore`**
4. Railway will scan the repo — click **Add service** → **GitHub Repo**

### 4c — Add a PostgreSQL database

1. In the same Railway project, click **+ New**
2. Select **Database**
3. Select **PostgreSQL**
4. Railway creates a Postgres 17 instance automatically

### 4d — Configure the backend service settings

Click on the backend service (not the Postgres service), then go to the **Settings** tab:

| Setting | Value |
|---|---|
| **Root Directory** | `backend` |
| **Build Command** | `./mvnw clean package -DskipTests` |
| **Start Command** | `java -jar target/bookstore-0.0.1-SNAPSHOT.jar` |

### 4e — Set environment variables

In the backend service → **Variables** tab, click **New Variable** and add each one:

| Variable | Value |
|---|---|
| `DB_URL` | Click **Add Reference** → select `DATABASE_URL` from the Postgres service |
| `DB_USERNAME` | Click **Add Reference** → select `PGUSER` |
| `DB_PASSWORD` | Click **Add Reference** → select `PGPASSWORD` |
| `JWT_SECRET` | Generate a strong random string (see below) |

**Generating a strong JWT secret** — run this in PowerShell:
```powershell
-join ((65..90) + (97..122) + (48..57) | Get-Random -Count 48 | % {[char]$_})
```
Copy the output and paste it as the value of `JWT_SECRET`.

> ⚠️ The `DB_URL` from Railway will look like:
> `postgresql://user:password@host:port/dbname`
> But our app expects the JDBC format. Manually set `DB_URL` to:
> `jdbc:postgresql://host:port/dbname`
> (copy the host/port/dbname from Railway's Postgres **Connect** tab)

**Easier alternative** — set all three DB vars manually from Railway's Postgres **Connect** tab:

| Variable | Where to find it in Railway |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host>:<port>/<database>` |
| `DB_USERNAME` | PGUSER value |
| `DB_PASSWORD` | PGPASSWORD value |

### 4f — Deploy

1. Go to the **Deploy** tab of the backend service
2. Click **Deploy Now** (or it may start automatically)
3. Watch the build logs — a successful deploy ends with:
   ```
   Started BookstoreApplication in X seconds
   Seeded 8 categories
   Seeded 113 books
   ```

### 4g — Get your backend URL

In the backend service → **Settings** → **Networking** → click **Generate Domain**.

You will get a URL like:
```
https://ecommerce-bookstore-production.up.railway.app
```

**Save this URL — you need it in the next step.**

---

## 5. Step 3 — Deploy Frontend on Vercel

### 5a — Create a Vercel account

1. Go to **https://vercel.com**
2. Click **Sign Up** → **Continue with GitHub**
3. Authorize Vercel to access your GitHub account

### 5b — Import the project

1. From the Vercel dashboard, click **Add New… → Project**
2. Find **`ecommerce-bookstore`** in the list and click **Import**

### 5c — Configure project settings

Vercel will show a configuration screen. Fill in:

| Setting | Value |
|---|---|
| **Framework Preset** | Vite *(auto-detected)* |
| **Root Directory** | `frontend` |
| **Build Command** | `npm run build` *(auto-detected)* |
| **Output Directory** | `dist` *(auto-detected)* |

### 5d — Add environment variable

Still on the same screen, scroll down to **Environment Variables** and add:

| Name | Value |
|---|---|
| `VITE_API_URL` | `https://ecommerce-bookstore-production.up.railway.app/api` |

Replace the URL with your actual Railway backend URL from Step 4g.

### 5e — Deploy

Click **Deploy**. Vercel builds and deploys the frontend. In about 60 seconds you will see:

```
🎉 Your project has been successfully deployed.
```

You will get a URL like:
```
https://ecommerce-bookstore.vercel.app
```

**Save this URL — you need it in the next step.**

---

## 6. Step 4 — Connect Frontend to Backend (CORS)

The backend currently only allows requests from `http://localhost:5173`. We need to add your Vercel URL.

Open `backend/src/main/java/com/harsh/bookstore/config/CorsConfig.java` and update the allowed origins:

```java
// BEFORE
.allowedOrigins("http://localhost:5173")

// AFTER
.allowedOrigins(
    "http://localhost:5173",
    "https://ecommerce-bookstore.vercel.app"   // ← your actual Vercel URL
)
```

Then commit and push:

```powershell
git add backend/src/main/java/com/harsh/bookstore/config/CorsConfig.java
git commit -m "config: add Vercel domain to CORS allowed origins"
git push origin main
```

Railway will automatically detect the push and redeploy the backend within 1–2 minutes.

---

## 7. Step 5 — Verify Everything Works

Open your Vercel URL in a browser and test:

- [ ] Home page loads and shows books
- [ ] Search for a book by name (e.g. "atheist")
- [ ] Filter by category
- [ ] Add a book to basket as a guest
- [ ] Register a new account
- [ ] Verify guest basket is wiped after sign-in
- [ ] Sign in with the new account
- [ ] Add books and proceed to checkout
- [ ] Place an order and see order confirmation
- [ ] Check gift points in the navbar

If anything fails, check the **Railway logs** (backend service → **Logs** tab) for errors.

---

## 8. Redeployment (After Code Changes)

After the initial setup, deployments are **fully automatic**:

```powershell
# Make your changes, then:
git add .
git commit -m "your message"
git push origin main

# Railway redeploys the backend automatically (~2 min)
# Vercel redeploys the frontend automatically (~1 min)
```

No manual steps required.

---

## 9. Environment Variable Reference

### Backend (Railway)

| Variable | Description | Example |
|---|---|---|
| `PORT` | Port Railway assigns — read automatically | `8080` |
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://host:5432/railway` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `abc123xyz` |
| `JWT_SECRET` | HMAC-SHA256 signing secret (min 32 chars) | `XyZ9...` (48 char random string) |

### Frontend (Vercel)

| Variable | Description | Example |
|---|---|---|
| `VITE_API_URL` | Full base URL of the deployed backend API | `https://your-app.up.railway.app/api` |

---

## 10. Troubleshooting

### Backend fails to start on Railway

**Symptom:** Build succeeds but app crashes on start.

**Check:** Railway logs for the exact error.

Common causes:
- `DB_URL` is in the wrong format — must start with `jdbc:postgresql://`, not `postgresql://`
- `JWT_SECRET` is missing or shorter than 32 characters
- Port binding error — ensure `server.port=${PORT:8080}` is set in `application.properties`

---

### Frontend shows blank page or "Network Error"

**Symptom:** Page loads but no books appear, API calls fail.

**Check:** Browser DevTools → Network tab → look for failed requests.

Common causes:
- `VITE_API_URL` env var not set in Vercel, or set to wrong URL
- CORS not updated in `CorsConfig.java` — the Vercel URL must be in `allowedOrigins`
- Railway backend is sleeping (free tier spins down after inactivity — first request takes ~30s)

---

### CORS error in browser console

**Symptom:** `Access to fetch at '...' from origin '...' has been blocked by CORS policy`

**Fix:** Add your exact Vercel URL (including `https://`) to `CorsConfig.java` → push → wait for Railway to redeploy.

---

### Database tables not created

**Symptom:** App starts but crashes with `relation "book" does not exist`.

**Fix:** Ensure `spring.jpa.hibernate.ddl-auto=update` is set in `application.properties`. Railway's PostgreSQL is a fresh empty database — Hibernate's `update` mode creates tables on first startup automatically.

---

### `./mvnw: Permission denied` on Railway

**Fix:** Add this to your Railway build command:
```
chmod +x mvnw && ./mvnw clean package -DskipTests
```

---

*Last updated: August 2026*
