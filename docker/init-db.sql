-- Initial database setup script for MySQL
-- This script runs automatically when MySQL container starts

-- Set timezone
SET time_zone = '+09:00';

-- Set character set
ALTER DATABASE pkx_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create indexes will be handled by JPA/Hibernate
-- This file is mainly for initial configuration
