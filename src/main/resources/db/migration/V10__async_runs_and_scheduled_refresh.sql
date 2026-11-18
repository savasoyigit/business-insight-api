-- M3: async query runs (QUEUED status) and scheduled dataset refresh.

-- Scheduled refresh: a cron expression per dataset, plus bookkeeping for the scheduler.
alter table dataset add column if not exists refresh_cron varchar(100);
alter table dataset add column if not exists next_refresh_at timestamptz;
alter table dataset add column if not exists last_refresh_at timestamptz;
create index if not exists ix_dataset_next_refresh on dataset(next_refresh_at) where refresh_cron is not null;

-- Lookups used by run polling, stale-run recovery and snapshot retention.
create index if not exists ix_query_run_status on query_run(status);
create index if not exists ix_snapshot_run on dataset_snapshot(query_run_id);
