# AGENTS.md

## Overview

This repository is a personal monorepo for the `Bapsang` project.
It currently contains a Java/Spring Boot backend and a JavaScript frontend.

Treat the current stack as the default unless the user explicitly asks for a migration.

## Stack Defaults

- Backend: Java 21, Spring Boot 3.x, Gradle
- Frontend: Vanilla JavaScript, HTML, CSS, Express
- Data: MySQL, Redis

Database schema, deployment setup, and infrastructure details may evolve over time.

## Repository Layout

- `apps/backend`: Spring Boot application
- `apps/frontend`: JavaScript frontend and Express-based serving layer
- `apps/backend/docs`: backend architecture and design notes
- `apps/frontend/docs`: frontend screenshots and project materials
- `docs/agent`: AI-assisted workflow notes and working agreements

## Working Rules

- Prefer minimal, targeted changes over broad rewrites.
- Preserve the current stack unless the user asks to change it.
- Keep frontend and backend responsibilities clearly separated.
- Do not introduce new frameworks or major infrastructure dependencies without a clear reason.
- Prefer repo-wide conventions that fit the monorepo layout.

## Commands

Run commands from the relevant app directory, not from the repo root, unless root-level tooling is added later.

### Backend

Working directory: `apps/backend`

- Run tests: `./gradlew test`
- Build: `./gradlew build`
- Run app locally: `./gradlew bootRun`

### Frontend

Working directory: `apps/frontend`

- Install dependencies: `npm install`
- Run tests: `npm test`
- Start server locally: `node server.js`

## Coding Expectations

- Follow the existing code style in each app before introducing new patterns.
- Keep comments short and useful.
- Add documentation comments to classes and functions in touched code.
- Add inline comments only when they materially help explain non-obvious logic or constraints.
- Avoid mixing unrelated refactors with feature or bug-fix work.
- Prefer readable, explicit code over clever abstractions.

## Backend Notes

- Respect existing Spring domain boundaries such as auth, user, post, comment, interaction, infra, and global modules.
- Favor service-layer changes over controller-heavy logic.
- Keep MySQL as the primary source of truth unless the user asks for architectural changes.
- Use Redis for caching or high-read / high-contention support cases, not as a casual replacement for the main database.
- Be careful with profile-specific configuration files under `src/main/resources`.

## Frontend Notes

- Preserve the current Vanilla JS approach unless the user explicitly requests a framework migration.
- Keep static assets and page structure easy to trace.
- Do not add unnecessary build complexity for small changes.
- Prefer improving the current Express/static serving setup rather than replacing it casually.

## Tests and Verification

- For backend changes, prefer running at least the affected Gradle tests.
- For frontend changes, run the existing Jest tests when relevant.
- If you cannot run verification, say so clearly.

## Agent Workflow

- Keep this file stable and concise.
- Put changing workflow details in `docs/agent` instead of expanding this file.
- For review, refactoring, and TDD-style work, follow `docs/agent/WORKFLOWS.md`.
- Ask the user before irreversible work, deployment, external API changes, or broad architectural changes.
- Proceed without asking for safe, reversible, convention-driven changes.

## Environment and Secrets

- Never commit real `.env` files or secrets.
- Prefer documenting required environment variables in example or README files.
- Treat production-oriented infrastructure settings as sensitive.
