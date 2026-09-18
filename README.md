# Ant IAM

Ant IAM is a standalone enterprise IAM / IDaaS backend built with Spring Boot 4. It covers organization directory, user lifecycle management, groups, RBAC, application access, identity sources and audit.

For third-party integration, see [docs/integration-guide.md](docs/integration-guide.md) (Chinese), which covers integration options, single sign-on and directory sync flows, API capabilities and FAQs.

For a project overview covering purpose and capabilities, see [docs/project-overview.md](docs/project-overview.md) (Chinese).

For a one-page project brief, see [docs/project-brief.md](docs/project-brief.md) (Chinese).

## Stack

- Java 25
- Spring Boot 4.0.6
- Spring WebMVC, Spring Security, Spring Data JPA
- PostgreSQL
- Liquibase database migrations
- Lombok

## Current Modules

- Organization directory, profiles, tree, user list and updates: `/api/v1/organizations`, `/api/v1/organizations/{organizationId}`, `/api/v1/organizations/tree`, `/api/v1/organizations/{organizationId}/users`
- Users, profiles, directory search and lifecycle actions: `/api/v1/users`, `/api/v1/users/{userId}`, `/api/v1/users/me` with optional `tenantId`, `organizationId`, `status` and `keyword` filters on the list endpoint
- User effective roles and permissions: `/api/v1/users/{userId}/effective-access`
- Group membership list: `/api/v1/access/groups/{groupId}/members`
- Group effective roles and permissions: `/api/v1/access/groups/{groupId}/effective-access`
- Permission impact analysis: `/api/v1/access/permissions/{permissionId}/impact`
- Role impact analysis: `/api/v1/access/roles/{roleId}/impact`
- Groups, roles, permissions, access resource search/profiles/updates, application role bindings and application lifecycle: `/api/v1/access/**`; applications support optional `tenantId`, `enabled` and `keyword` filters
- Application SSO config and assignment lifecycle, including optional assignment expiry: `/api/v1/access/applications/{applicationId}/**`
- Application access decisions across direct, group and application-role sources: `/api/v1/access/applications/{applicationId}/access-decisions?userId=...`
- User application portal list: `/api/v1/access/users/{userId}/applications`
- Current user application portal list: `/api/v1/access/me/applications`
- Current user requestable application list: `/api/v1/access/me/requestable-applications`
- Application access request and approval workflow: `/api/v1/access/application-access-requests`
- Current user application access requests: `/api/v1/access/me/application-access-requests`
- Application access review list: `/api/v1/access/applications/{applicationId}/access-review`
- User suspend, lock and depart actions automatically disable direct application assignments, end active authentication sessions and revoke active OAuth2 tokens.
- Identity sources with profiles, search, updates and lifecycle controls: `/api/v1/identity-sources`, `/api/v1/identity-sources/{identitySourceId}`; list supports optional `tenantId`, `type`, `enabled` and `keyword` filters
- Identity source connectors with lifecycle controls, and sync jobs with job profiles, updates and lifecycle controls: `/api/v1/identity-sources/{identitySourceId}/connector`, `/api/v1/identity-sources/{identitySourceId}/sync-jobs`, `/api/v1/identity-sources/sync-jobs/{syncJobId}`
- Manual identity sync runs with JSON payload import and run profiles: `/api/v1/identity-sources/sync-jobs/{syncJobId}/runs`, `/api/v1/identity-sources/sync-runs/{syncRunId}`
- Identity source realtime event callback with HMAC-SHA256 signature verification: `/api/v1/synchronizer/event_receive/{sourceCode}`
- Outbound email delivery with configurable SMTP service and template rendering, wired into EMAIL MFA challenges
- File upload with native adapters for Aliyun OSS, Tencent COS, Qiniu Kodo and S3-compatible services: `/api/v1/files`
- IP geo-location resolution backed by MaxMind databases, with system-default address classification
- Authentication policies with lifecycle controls, configurable MFA, enrollment, step-up, deny, password minimum-length, failed-login lockout, password expiry and password history enforcement: `/api/v1/authentication-policies`
- Authentication policy evaluation: `/api/v1/authentication-policies/evaluations`
- Login risk rules with search/profiles/updates/lifecycle controls and searchable assessment profiles with device fingerprint and geo-location context: `/api/v1/risk/rules`, `/api/v1/risk/rules/{ruleId}`, `/api/v1/risk/assessments`, `/api/v1/risk/assessments/{assessmentId}`
- Dashboard summary, metrics and range statistics (authentication trend, application ranking, authentication methods, login locations): `/api/v1/dashboard/summary`, `/api/v1/dashboard/metrics`, `/api/v1/dashboard/statistics`
- System settings with search/filtering, profiles and deletion: `/api/v1/settings`, `/api/v1/settings/{settingKey}`
- Tenants and tenant settings with setting search/profiles/deletion: `/api/v1/tenants`, `/api/v1/tenants/{tenantId}/settings`, `/api/v1/tenants/{tenantId}/settings/{settingKey}`
- Authentication sessions with active/history filtering, force logout and searchable events, including login risk and logout events: `/api/v1/authentication/**`
- Password credentials, MFA factors with profiles/updates/lifecycle controls and TOTP verification: `/api/v1/users/**`
- Password reset tickets with search, profiles and revocation: `/api/v1/users/password-reset-tickets`, `/api/v1/users/password-reset-tickets/{ticketId}`
- Self-service password change: `/api/v1/users/me/password`
- MFA challenges with search/profiles and recovery codes: `/api/v1/users/{userId}/mfa-challenges`, `/api/v1/users/mfa-challenges`, `/api/v1/users/{userId}/mfa-recovery-codes`, `/api/v1/users/mfa-challenge-verifications`
- SCIM 2.0 users, groups and organizations with list/create/profile endpoints plus filter and pagination support: `/scim/v2/Users`, `/scim/v2/Groups`, `/scim/v2/Organizations`
- SCIM 2.0 discovery: `/scim/v2/ServiceProviderConfig`, `/scim/v2/ResourceTypes`, `/scim/v2/Schemas`
- OAuth2 authorization endpoint: `/oauth2/authorize`
- OAuth2 token endpoint: `/oauth2/token`
- OIDC discovery and userinfo: `/.well-known/openid-configuration`, `/oauth2/userinfo`
- OIDC JWKS and RS256 ID token signing: `/oauth2/jwks`
- OIDC ID token claim policies on application SSO config
- JWT signing key management with filtering, profiles, rotation and guarded retirement: `/api/v1/jwt-signing-keys`, `/api/v1/jwt-signing-keys/{keyId}`
- OAuth2 PKCE and refresh token support
- OAuth2 refresh token rotation and revocation: `/oauth2/revocations`
- OAuth2 token introspection and client-authenticated revocation: `/oauth2/introspect`, `/oauth2/revoke`
- OAuth2 access/refresh token inventory, profiles and admin revocation: `/api/v1/oauth/tokens`, `/api/v1/oauth/tokens/{tokenType}/{tokenId}`
- OAuth2 consent management with filtering, profiles and revocation: `/oauth2/consents`, `/oauth2/consents/{consentId}`
- SAML2 metadata and SSO assertions with XML responses: `/saml2/metadata`, `/saml2/metadata.xml`, `/saml2/sso`, `/saml2/sso/xml`
- CAS login and service validation with XML response support: `/cas/login`, `/cas/serviceValidate`, `/cas/p3/serviceValidate`
- Audit event profile, search with keyword filtering and CSV export: `/api/v1/audit-events`, `/api/v1/audit-events/{auditEventId}`, `/api/v1/audit-events/export`
- Public catalog: `/api/v1/catalog`
- OpenAPI JSON documentation, publicly readable for integration tooling: `/v3/api-docs`
- Swagger UI interactive API documentation: `/swagger-ui/index.html`
- Health check: `/actuator/health`

## Run Locally

Option 1: start only PostgreSQL with Docker, then run the API on the host.

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run the API:

```bash
mvnd spring-boot:run
```

If Maven Daemon is not present, use local Maven:

```bash
mvn spring-boot:run
```

Option 2: run PostgreSQL and the API with Docker Compose.

Build the application jar first:

```bash
mvnd package -DskipTests
```

Then start the `api` profile:

```bash
docker compose --profile api up --build
```

When the API container is healthy, `http://localhost:8080/actuator/health` returns `UP`.

The default PostgreSQL settings match `docker-compose.yml`:

```text
ANT_IAM_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ant_iam
ANT_IAM_DATASOURCE_USERNAME=ant_iam
ANT_IAM_DATASOURCE_PASSWORD=ant_iam
```

Inside Docker Compose, the API uses `jdbc:postgresql://postgres:5432/ant_iam` to reach PostgreSQL on the compose network.

Password sign-in reads users and password credentials from the `user_accounts` and `user_credentials` database tables. On first install with an empty database, the application initializes the default administrator account `admin / admin123456`; it no longer reads default sign-in credentials from configuration.

SMS verification codes are delivered through sms4j channels. The default local setup enables the `fixed-code` channel with code `666666`, overrideable through `ANT_IAM_SMS_FIXED_CODE`; production deployments can configure another sms4j channel and select it with `ANT_IAM_SMS_BLEND_ID`.

OpenAPI JSON, Swagger UI, health checks, OIDC discovery, JWKS, SAML metadata, CAS validation and OAuth2 token/introspection/revocation endpoints are exposed without sign-in so protocol clients can call them directly; management APIs use the Bearer session token issued by the login endpoints.

## Frontend Projects

The frontend lives in this repository:

- Unified frontend: `Ant-IAM-Frontend`

The frontend is built with Vite, React, TypeScript, Tailwind CSS and Ant Design. It connects to `http://localhost:8080` by default and uses `/login` to obtain a Bearer session token for the portal and console.

Install dependencies:

```bash
cd Ant-IAM-Frontend
pnpm install
```

Start the development server:

```bash
pnpm run dev
```

The frontend uses `http://localhost:5174/` by default. The portal is under `/portal`; the admin console is under `/console`.

Build for production:

```bash
pnpm run build
```

## Example

```bash
TOKEN=$(curl -s \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123456"}' \
  http://localhost:8080/api/v1/authentication/password-login | jq -r '.session.sessionIndex')

curl -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"code":"hq","name":"Headquarters"}' \
  http://localhost:8080/api/v1/organizations
```

Identity source connector configuration can import directory data from JSON. DingTalk and Feishu connectors also support the same structure under `payload` for local dry runs:

```json
{
  "organizations": [
    {"code": "engineering", "name": "Engineering"}
  ],
  "users": [
    {"username": "alice", "displayName": "Alice", "email": "alice@example.com", "organizationCode": "engineering"}
  ],
  "groups": [
    {"code": "admins", "name": "Administrators", "members": ["alice"]}
  ]
}
```

DingTalk connectors can use Open Platform credentials to import departments and department users:

```json
{
  "appKey": "ding-app-key",
  "appSecret": "ding-app-secret",
  "rootDeptId": 1,
  "fetchUserDetail": true
}
```

The DingTalk app must have contact department and member read permissions. The connector prefers the official DingTalk Java SDK for `oapi` contact APIs; set `endpoint` only for private deployments or API gateways.

Feishu connectors can use app credentials to import departments and department users:

```json
{
  "appId": "cli_xxx",
  "appSecret": "feishu-app-secret",
  "rootDepartmentId": "0",
  "departmentIdType": "open_department_id"
}
```

The Feishu app must have contact department and user read permissions. The connector prefers the official Feishu Java SDK for `contact/v3` department children and department user APIs, and follows `has_more` / `page_token` pagination; set `endpoint` only for private deployments or API gateways.

WeCom identity sources can use the corporate ID and contact secret to import departments and users:

```json
{
  "corpId": "wwxxxxxxxx",
  "corpSecret": "wechat-work-contact-secret",
  "rootDeptId": 1
}
```

The WeCom app must have contact department and member read permissions. The connector calls the official WeCom contact APIs directly; set `endpoint` only for private deployments or API gateways. Sync jobs with `cronExpression` are executed by the background scheduler. By default it scans due jobs every 60 seconds; tune it with the `ant-iam.identity-sync.scheduler-delay-ms` property.

Third-party login uses public authorization and callback endpoints. The callback should send back the signed `state` returned by `authorize`:

```bash
curl 'http://localhost:8080/api/v1/authentication/third-party/wechat/authorize?redirectUri=https://iam.example.com/callback&state=demo'
```

```bash
curl -H 'Content-Type: application/json' \
  -d '{"code":"auth-code","state":"authorize-response-state","redirectUri":"https://iam.example.com/callback"}' \
  http://localhost:8080/api/v1/authentication/third-party/wechat/callback
```

WeChat, QQ, Feishu and DingTalk authentication providers use `appId`, `appSecret` and `redirectUri`. Feishu login prefers the official Feishu Java SDK `authen/v1` APIs, and DingTalk login prefers the official DingTalk Java SDK `sns/getuserinfo_bycode` API. WeChat and QQ use their official OAuth endpoints directly. Optional fields include `scope`, `usernameClaim`, `usernamePrefix`, `autoCreateUser`, `sessionTtlMinutes`, `stateTtlSeconds` and platform endpoint overrides.

## Roadmap

- Harden SAML2/CAS adapters with XML signatures and richer protocol binding validation.
- Add guided user-facing MFA enrollment and recovery screens.
- Add background connectors for LDAP and AD.
- Continue native LDAP/AD connector adapters on top of the JSON sync executor.
- Replace SMS and WebAuthn MFA prototype challenge codes with production verifiers.
- Expand risk rules with geo-velocity and richer adaptive MFA actions.
