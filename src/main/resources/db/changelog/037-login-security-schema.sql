--liquibase formatted sql

--changeset antiam:037-mfa-challenge-purpose
alter table mfa_challenges
    add column if not exists purpose varchar(32) not null default 'GENERAL';

--changeset antiam:037-mfa-factor-last-used-counter
alter table mfa_factors
    add column if not exists last_used_counter bigint;

--changeset antiam:037-session-restriction
alter table authentication_sessions
    add column if not exists restriction varchar(32);

--changeset antiam:037-session-index-unique
update authentication_sessions s
set session_index = s.session_index || '-dup-' || s.id,
    active = false,
    ended_at = coalesce(s.ended_at, now())
where exists (
    select 1 from authentication_sessions o
    where o.session_index = s.session_index
      and (o.created_at < s.created_at or (o.created_at = s.created_at and o.id < s.id))
);
create unique index if not exists uk_authentication_sessions_session_index on authentication_sessions(session_index);
