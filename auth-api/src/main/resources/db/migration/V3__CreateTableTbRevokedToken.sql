create table tb_revoked_token (
    token_hash varchar(64) not null,
    expires_at timestamp with time zone not null,
    constraint pk_tb_revoked_token primary key (token_hash),
    constraint ck_tb_revoked_token_sha256 check (token_hash ~ '^[0-9a-f]{64}$')
);

create index idx_tb_revoked_token_expires_at
    on tb_revoked_token (expires_at);