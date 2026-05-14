-- =====================================================================
-- SwiftDoc: Users table setup
-- Run this script once against the SwiftDoc database.
-- Safe to re-run: every statement is idempotent (IF NOT EXISTS guards).
--
-- Default credentials seeded by this script:
--   admin / admin   (role = ADMIN)
--   user  / user    (role = USER)
--
-- Passwords are stored as SHA-256 hex digests of the plain password.
-- =====================================================================

-- 1. Create table if it doesn't exist ------------------------------------
IF OBJECT_ID('dbo.Users', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Users (
        UserId        INT IDENTITY(1,1) PRIMARY KEY,
        Username      NVARCHAR(64)  NOT NULL UNIQUE,
        PasswordHash  NVARCHAR(128) NOT NULL DEFAULT '',
        Role          NVARCHAR(16)  NOT NULL DEFAULT 'USER'
    );
END;

-- 2. Add missing columns if table existed with an old schema -------------
IF COL_LENGTH('dbo.Users', 'PasswordHash') IS NULL
    ALTER TABLE dbo.Users ADD PasswordHash NVARCHAR(128) NOT NULL DEFAULT '';

IF COL_LENGTH('dbo.Users', 'Role') IS NULL
    ALTER TABLE dbo.Users ADD Role NVARCHAR(16) NOT NULL DEFAULT 'USER';

-- 3. Seed default accounts (only if neither exists) ----------------------
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Username IN ('admin', 'user'))
BEGIN
    INSERT INTO dbo.Users (Username, PasswordHash, Role) VALUES
        ('admin',
         '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918',  -- SHA-256('admin')
         'ADMIN'),
        ('user',
         '04f8996da763b7a969b1028ee3007569eaf3a635486ddab211d512c85b9df8fb',  -- SHA-256('user')
         'USER');
END;

-- =====================================================================
-- Verify
-- =====================================================================
SELECT UserId, Username, Role, LEN(PasswordHash) AS HashLen FROM dbo.Users;
