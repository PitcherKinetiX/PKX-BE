-- Initial database setup script
-- This script runs automatically when PostgreSQL container starts

-- Create extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set timezone
SET timezone = 'Asia/Seoul';

-- Create indexes will be handled by JPA/Hibernate
-- This file is mainly for extensions and initial configuration
