-- WebLager SQL Server schema (dbo)
-- Creates core tables for SwiftDoc if they do not exist.

IF OBJECT_ID('dbo.Users', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Users (
        UserId INT IDENTITY(1,1) PRIMARY KEY,
        Username NVARCHAR(100) NOT NULL UNIQUE,
        Email NVARCHAR(150) NOT NULL UNIQUE,
        PasswordHash NVARCHAR(255) NOT NULL,
        UserRole NVARCHAR(20) NOT NULL DEFAULT 'USER',
        CreatedAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        UpdatedAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END;

IF OBJECT_ID('dbo.Clients', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Clients (
        ClientId INT IDENTITY(1,1) PRIMARY KEY,
        ClientName NVARCHAR(150) NOT NULL
    );
END;

-- Ensure a default client exists for backfill
IF NOT EXISTS (SELECT 1 FROM dbo.Clients WHERE ClientName = 'Default Client')
BEGIN
    INSERT INTO dbo.Clients (ClientName) VALUES ('Default Client');
END;

IF OBJECT_ID('dbo.Profiles', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Profiles (
        ProfileId INT IDENTITY(1,1) PRIMARY KEY,
        ProfileName NVARCHAR(150) NOT NULL,
        SplitRule NVARCHAR(255) NULL,
        ClientId INT NOT NULL,
        CONSTRAINT FK_Profiles_Clients
            FOREIGN KEY (ClientId) REFERENCES dbo.Clients(ClientId)
    );
END;
ELSE
BEGIN
    -- Add missing SplitRule column
    IF COL_LENGTH('dbo.Profiles', 'SplitRule') IS NULL
    BEGIN
        ALTER TABLE dbo.Profiles ADD SplitRule NVARCHAR(255) NULL;
    END;

    -- Add missing ClientId column as NULL first
    IF COL_LENGTH('dbo.Profiles', 'ClientId') IS NULL
    BEGIN
        ALTER TABLE dbo.Profiles ADD ClientId INT NULL;
    END;

    -- Backfill ClientId for existing rows
    DECLARE @DefaultClientId INT;
    SELECT @DefaultClientId = ClientId FROM dbo.Clients WHERE ClientName = 'Default Client';

    UPDATE dbo.Profiles
    SET ClientId = @DefaultClientId
    WHERE ClientId IS NULL;

    -- Enforce NOT NULL after backfill
    ALTER TABLE dbo.Profiles ALTER COLUMN ClientId INT NOT NULL;

    -- Add FK if missing
    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_Profiles_Clients'
    )
    BEGIN
        ALTER TABLE dbo.Profiles
        ADD CONSTRAINT FK_Profiles_Clients
            FOREIGN KEY (ClientId) REFERENCES dbo.Clients(ClientId);
    END;
END;

IF OBJECT_ID('dbo.Boxes', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Boxes (
        BoxId INT IDENTITY(1,1) PRIMARY KEY,
        BoxName NVARCHAR(150) NOT NULL,
        ProfileId INT NOT NULL,
        GlobalRotation INT NOT NULL DEFAULT 0,
        CONSTRAINT FK_Boxes_Profiles
            FOREIGN KEY (ProfileId) REFERENCES dbo.Profiles(ProfileId)
    );
END;

IF OBJECT_ID('dbo.Documents', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Documents (
        DocumentId INT IDENTITY(1,1) PRIMARY KEY,
        BoxId INT NOT NULL,
        BarcodeValue NVARCHAR(255) NULL,
        CONSTRAINT FK_Documents_Boxes
            FOREIGN KEY (BoxId) REFERENCES dbo.Boxes(BoxId)
    );
END;

IF OBJECT_ID('dbo.Files', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Files (
        FileId INT IDENTITY(1,1) PRIMARY KEY,
        DocumentId INT NOT NULL,
        ReferenceId INT NOT NULL,
        IncrementalId INT NOT NULL,
        RotationAngle INT NOT NULL DEFAULT 0,
        TiffData VARBINARY(MAX) NOT NULL,
        CONSTRAINT FK_Files_Documents
            FOREIGN KEY (DocumentId) REFERENCES dbo.Documents(DocumentId)
    );
END;

IF OBJECT_ID('dbo.SystemLogs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.SystemLogs (
        LogId INT IDENTITY(1,1) PRIMARY KEY,
        Action NVARCHAR(50) NOT NULL,
        EntityType NVARCHAR(50) NULL,
        EntityId INT NULL,
        PerformedBy INT NULL,
        LogMessage NVARCHAR(MAX) NULL,
        CreatedAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_SystemLogs_Users
            FOREIGN KEY (PerformedBy) REFERENCES dbo.Users(UserId)
    );
END;

-- Helpful indexes for scan performance
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Documents_BoxId' AND object_id = OBJECT_ID('dbo.Documents'))
BEGIN
    CREATE INDEX IX_Documents_BoxId ON dbo.Documents(BoxId);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Files_DocumentId' AND object_id = OBJECT_ID('dbo.Files'))
BEGIN
    CREATE INDEX IX_Files_DocumentId ON dbo.Files(DocumentId);
END;

-- Seed admin user (optional)
IF COL_LENGTH('dbo.Users', 'Email') IS NOT NULL
   AND COL_LENGTH('dbo.Users', 'PasswordHash') IS NOT NULL
   AND COL_LENGTH('dbo.Users', 'UserRole') IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE Username = 'admin')
    BEGIN
        INSERT INTO dbo.Users (Username, Email, PasswordHash, UserRole)
        VALUES ('admin', 'admin@weblager.local', 'hashed_admin_password', 'ADMIN');
    END;
END;

