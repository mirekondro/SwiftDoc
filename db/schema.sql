-- WebLager Database Schema
-- SaaS Archive Core Solution for Scanning & Digitalization
-- Tables: Users, Profiles, Boxes, Documents, Files, Logs

-- Users table: admin + user differentiation
CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_role ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Scanning profiles: define barcode split rules
CREATE TABLE IF NOT EXISTS scanning_profiles (
    profile_id INT PRIMARY KEY AUTO_INCREMENT,
    profile_name VARCHAR(150) NOT NULL,
    description TEXT,
    barcode_split_rule VARCHAR(255),
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- Boxes: containers for documents
CREATE TABLE IF NOT EXISTS boxes (
    box_id INT PRIMARY KEY AUTO_INCREMENT,
    box_name VARCHAR(150) NOT NULL,
    profile_id INT,
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (profile_id) REFERENCES scanning_profiles(profile_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- Documents: items within boxes
CREATE TABLE IF NOT EXISTS documents (
    document_id INT PRIMARY KEY AUTO_INCREMENT,
    document_name VARCHAR(255) NOT NULL,
    box_id INT NOT NULL,
    barcode VARCHAR(100),
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (box_id) REFERENCES boxes(box_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- Files: actual scanned/uploaded files
CREATE TABLE IF NOT EXISTS files (
    file_id INT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    document_id INT NOT NULL,
    file_size BIGINT,
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(document_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- System logs: audit trail for file/document operations
CREATE TABLE IF NOT EXISTS system_logs (
    log_id INT PRIMARY KEY AUTO_INCREMENT,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id INT,
    performed_by INT NOT NULL,
    log_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (performed_by) REFERENCES users(user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_performed_by (performed_by)
);

-- Sample Admin user (password: admin123 hashed)
INSERT INTO users (username, email, password_hash, user_role)
VALUES ('admin', 'admin@weblager.local', 'hashed_admin_password', 'ADMIN')
ON DUPLICATE KEY UPDATE user_role = 'ADMIN';

