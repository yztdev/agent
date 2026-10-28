CREATE TABLE users (id UUID PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, password_hash TEXT NOT NULL, role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN','USER')), status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','DISABLED')), created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE auth_sessions (id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), token_hash CHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX auth_sessions_user_idx ON auth_sessions(user_id);
