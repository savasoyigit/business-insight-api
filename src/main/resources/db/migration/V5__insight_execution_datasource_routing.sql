alter table data_source
    add column if not exists host varchar(255),
    add column if not exists port integer,
    add column if not exists database_name varchar(255),
    add column if not exists username varchar(255),
    add column if not exists secret_ref varchar(255),
    add column if not exists options jsonb;

update data_source
set host = coalesce(host, connection->>'host'),
    port = coalesce(port, nullif(connection->>'port', '')::integer),
    database_name = coalesce(database_name, connection->>'database'),
    username = coalesce(username, connection->>'username'),
    secret_ref = coalesce(secret_ref, connection->>'secretRef'),
    options = coalesce(options, connection->'options')
where connection is not null;

alter table data_source
    alter column host set not null,
    alter column port set not null,
    alter column database_name set not null,
    alter column username set not null,
    alter column secret_ref set not null;

alter table data_source
    drop column if exists connection;

alter table query_run
    add column if not exists data_source_id bigint;

update query_run qr
set data_source_id = qt.data_source_id
from query_template qt
where qr.query_template_id = qt.id
  and qr.data_source_id is null;

alter table query_run
    alter column data_source_id set not null;

alter table query_run
    add constraint fk_query_run_data_source
        foreign key (data_source_id) references data_source(id);

create index if not exists ix_query_run_ds on query_run(data_source_id);
