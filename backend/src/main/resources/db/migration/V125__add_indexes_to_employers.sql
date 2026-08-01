-- Employer list search (EmployerRepository.searchPage) filters/LIKE-searches on
-- name, code and email with no supporting index — add them now that bulk
-- archive/restore make list scans more frequent.
CREATE INDEX IF NOT EXISTS idx_employers_name ON employers (name);
CREATE INDEX IF NOT EXISTS idx_employers_code ON employers (code);
CREATE INDEX IF NOT EXISTS idx_employers_email ON employers (email);
CREATE INDEX IF NOT EXISTS idx_employers_active ON employers (active);
