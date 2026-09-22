--liquibase formatted sql

--changeset antiam:032-authentication-event-method
--validCheckSum 9:3759b40f7f98b69e9b72456327f3349d
alter table authentication_events add column method varchar(64);

create index if not exists idx_authentication_events_created_at on authentication_events(created_at);
create index idx_authentication_events_method on authentication_events(method);
create index idx_authentication_sessions_created_at on authentication_sessions(created_at);
