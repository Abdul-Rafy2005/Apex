CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    action          VARCHAR(50) NOT NULL,
    target_user_id  UUID,
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_org_created_at ON audit_log(organization_id, created_at DESC);
CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
