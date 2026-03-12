-- ============================================================
-- Table: member_policy_assignments
-- Depends on: members, benefit_policies
-- ============================================================
CREATE TABLE IF NOT EXISTS member_policy_assignments (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT NOT NULL,
    policy_id             BIGINT NOT NULL,
    assignment_start_date DATE NOT NULL,
    assignment_end_date   DATE,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(255),

    CONSTRAINT fk_assignment_member FOREIGN KEY (member_id)  REFERENCES members(id)          ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_policy FOREIGN KEY (policy_id)  REFERENCES benefit_policies(id) ON DELETE RESTRICT,
    CONSTRAINT chk_assignment_dates CHECK (assignment_end_date IS NULL OR assignment_end_date >= assignment_start_date)
);

CREATE INDEX IF NOT EXISTS idx_policy_assignments_member ON member_policy_assignments(member_id);
CREATE INDEX IF NOT EXISTS idx_policy_assignments_policy ON member_policy_assignments(policy_id);
CREATE INDEX IF NOT EXISTS idx_policy_assignments_dates  ON member_policy_assignments(assignment_start_date, assignment_end_date);
