CREATE TABLE scan_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  disease VARCHAR(100) NOT NULL,
  confidence DECIMAL(5,4) NOT NULL,
  confident_enough BOOLEAN NOT NULL DEFAULT FALSE,
  conv_id VARCHAR(255) DEFAULT NULL,
  scanned_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scan_history_user
    ON scan_history(user_id, scanned_at DESC);