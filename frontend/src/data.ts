import type { ApiClient } from './api';
import type {
  Application,
  AuditEventList,
  Catalog,
  DashboardMetrics,
  DashboardSummary,
  IdentitySource,
  Organization,
  RiskRule,
  SystemSetting,
  User,
} from './types';

export function loadDashboard(api: ApiClient) {
  return Promise.all([
    api.request<DashboardSummary>('/api/v1/dashboard/summary'),
    api.request<DashboardMetrics>('/api/v1/dashboard/metrics'),
  ]);
}

export function loadWorkspace(api: ApiClient) {
  return Promise.all([
    api.request<Catalog>('/api/v1/catalog'),
    api.request<User[]>('/api/v1/users'),
    api.request<Organization[]>('/api/v1/organizations'),
    api.request<Application[]>('/api/v1/access/applications'),
    api.request<IdentitySource[]>('/api/v1/identity-sources'),
    api.request<RiskRule[]>('/api/v1/risk/rules'),
    api.request<SystemSetting[]>('/api/v1/settings'),
    api.request<AuditEventList>('/api/v1/audit-events', { query: { limit: 12 } }),
  ]);
}
