create table if not exists dataset (
    id bigserial primary key,
    organization_id bigint not null references organization(id),
    name varchar(200) not null,
    query_template_id bigint not null references query_template(id),
    default_params jsonb not null default '{}'::jsonb,
    created_by varchar(120),
    created_at timestamptz not null default now(),
    unique (organization_id, name)
);

alter table dataset_snapshot
    add column if not exists dataset_id bigint references dataset(id);

create index if not exists ix_dataset_org on dataset(organization_id);
create index if not exists ix_dataset_qt on dataset(query_template_id);
create index if not exists ix_snapshot_dataset on dataset_snapshot(dataset_id);

create table if not exists chart (
    id bigserial primary key,
    organization_id bigint not null references organization(id),
    name varchar(200) not null,
    type varchar(50) not null,
    dataset_id bigint not null references dataset(id),
    config jsonb not null default '{}'::jsonb,
    created_by varchar(120),
    created_at timestamptz not null default now(),
    unique (organization_id, name)
);

create index if not exists ix_chart_org on chart(organization_id);
create index if not exists ix_chart_dataset on chart(dataset_id);

create table if not exists dashboard (
    id bigserial primary key,
    organization_id bigint not null references organization(id),
    name varchar(200) not null,
    created_by varchar(120),
    created_at timestamptz not null default now(),
    unique (organization_id, name)
);

create index if not exists ix_dashboard_org on dashboard(organization_id);

create table if not exists dashboard_widget (
    id bigserial primary key,
    dashboard_id bigint not null references dashboard(id) on delete cascade,
    chart_id bigint not null references chart(id),
    layout jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists ix_dashboard_widget_dashboard on dashboard_widget(dashboard_id);
create index if not exists ix_dashboard_widget_chart on dashboard_widget(chart_id);
