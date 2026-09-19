-- PostgreSQL Schema for FreeSWITCH Softswitch Control Plane
-- Tables: fs_extension, fs_gateway, fs_cdr

CREATE TABLE IF NOT EXISTS fs_extension (
    id                  SERIAL PRIMARY KEY,
    extension           VARCHAR(32) NOT NULL UNIQUE,
    password            VARCHAR(128) NOT NULL,
    context             VARCHAR(64) DEFAULT 'default',
    callgroup           VARCHAR(64) DEFAULT 'default',
    effective_caller_id VARCHAR(64) DEFAULT '',
    endpoint_type       VARCHAR(32) DEFAULT 'SIP',
    is_enabled          BOOLEAN DEFAULT TRUE,
    description         VARCHAR(255) DEFAULT '',
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fs_ext_enabled ON fs_extension (is_enabled);

CREATE TABLE IF NOT EXISTS fs_gateway (
    id                  SERIAL PRIMARY KEY,
    name                VARCHAR(64) NOT NULL UNIQUE,
    profile             VARCHAR(32) DEFAULT 'external',
    proxy               VARCHAR(128) NOT NULL,
    username            VARCHAR(64) NOT NULL,
    password            VARCHAR(128) DEFAULT '',
    auth_user           VARCHAR(128) DEFAULT '',
    from_user           VARCHAR(128) DEFAULT '',
    from_domain         VARCHAR(128) DEFAULT '',
    caller_id_in_from   BOOLEAN DEFAULT TRUE,
    context             VARCHAR(64) DEFAULT 'public',
    extension           VARCHAR(64) DEFAULT 'auto_to_user',
    dtmf_type           VARCHAR(32) DEFAULT 'rfc2833',
    codecs              VARCHAR(128) DEFAULT 'PCMA, G729',
    register            BOOLEAN DEFAULT TRUE,
    expire_seconds      INT DEFAULT 3600,
    ping_seconds        INT DEFAULT 25,
    status              VARCHAR(32) DEFAULT 'UNKNOWN',
    ping_ms             VARCHAR(32) DEFAULT '',
    is_enabled          BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fs_gw_enabled ON fs_gateway (is_enabled);

CREATE TABLE IF NOT EXISTS fs_cdr (
    id                      SERIAL PRIMARY KEY,
    call_uuid               VARCHAR(64) NOT NULL UNIQUE,
    caller_id_name          VARCHAR(64) DEFAULT '',
    caller_id_number        VARCHAR(64) NOT NULL,
    destination_number      VARCHAR(64) NOT NULL,
    context                 VARCHAR(64) DEFAULT 'default',
    start_epoch             BIGINT DEFAULT 0,
    answer_epoch            BIGINT DEFAULT 0,
    end_epoch               BIGINT DEFAULT 0,
    duration                INT DEFAULT 0,
    billsec                 INT DEFAULT 0,
    hangup_cause            VARCHAR(64) DEFAULT 'NORMAL_CLEARING',
    sip_hangup_disposition  VARCHAR(64) DEFAULT 'send_bye',
    direction               VARCHAR(32) DEFAULT 'inbound',
    read_codec              VARCHAR(32) DEFAULT 'PCMA',
    write_codec             VARCHAR(32) DEFAULT 'PCMA',
    sip_user_agent          VARCHAR(128) DEFAULT '',
    quality_percentage      VARCHAR(16) DEFAULT '',
    variables_json          JSONB DEFAULT '{}'::jsonb,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fs_cdr_caller ON fs_cdr (caller_id_number);
CREATE INDEX IF NOT EXISTS idx_fs_cdr_dest ON fs_cdr (destination_number);
CREATE INDEX IF NOT EXISTS idx_fs_cdr_created ON fs_cdr (created_at DESC);
