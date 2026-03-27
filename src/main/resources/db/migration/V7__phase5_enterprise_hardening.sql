alter table query_run
    add column if not exists query_hash varchar(64),
    add column if not exists error_type varchar(100);

create index if not exists ix_query_run_query_hash on query_run(query_hash);
create index if not exists ix_query_run_error_type on query_run(error_type);

alter table app_user
    add column if not exists failed_login_attempts integer not null default 0,
    add column if not exists locked_until timestamptz;

create table if not exists rbac_audit_log (
    id bigserial primary key,
    actor_username varchar(120) not null,
    action varchar(100) not null,
    target_type varchar(80) not null,
    target_id varchar(120),
    correlation_id varchar(100),
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists ix_rbac_audit_created_at on rbac_audit_log(created_at desc);
create index if not exists ix_rbac_audit_actor on rbac_audit_log(actor_username);
create index if not exists ix_rbac_audit_action on rbac_audit_log(action);
