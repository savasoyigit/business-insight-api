-- Tenant isolation: a user can only work inside organizations they are a member of.
create table if not exists organization_member (
    user_id         bigint      not null references app_user(id) on delete cascade,
    organization_id bigint      not null references organization(id) on delete cascade,
    created_at      timestamptz not null default now(),
    primary key (user_id, organization_id)
);

create index if not exists ix_organization_member_org on organization_member(organization_id);

-- Upgrade path: before this migration every user could reach every organization through ?orgId=.
-- Keep existing users working by making them members of all existing organizations;
-- admins can then narrow memberships via PUT /api/admin/rbac/users/{id}/organizations.
insert into organization_member(user_id, organization_id)
select u.id, o.id
from app_user u
cross join organization o
on conflict do nothing;
