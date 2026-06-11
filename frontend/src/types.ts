export type DashboardSummary = {
  users: number;
  activeUsers: number;
  organizations: number;
  applications: number;
  identitySources: number;
  activeSessions: number;
  highRiskAssessments: number;
  pendingAccessRequests: number;
};

export type DashboardMetrics = {
  authenticationEvents: Record<string, number>;
  riskAssessments: Record<string, number>;
  syncRuns: Record<string, number>;
};

export type User = {
  id: string;
  username: string;
  displayName: string;
  email?: string;
  mobile?: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'LOCKED' | 'DEPARTED';
  tenantId?: string;
  organizationId?: string;
  groups: string[];
  roles: string[];
};

export type Organization = {
  id: string;
  code: string;
  name: string;
  parentId?: string;
};

export type Application = {
  id: string;
  code: string;
  name: string;
  protocol: 'OIDC' | 'SAML' | 'CAS' | 'FORM' | 'JWT';
  loginUrl?: string;
  tenantId?: string;
  enabled: boolean;
  selfServiceAccessRequestEnabled: boolean;
};

export type IdentitySource = {
  id: string;
  code: string;
  name: string;
  type: 'LDAP' | 'ACTIVE_DIRECTORY' | 'DINGTALK' | 'WECHAT_WORK' | 'FEISHU' | 'SCIM' | 'CUSTOM';
  enabled: boolean;
  tenantId?: string;
};

export type RiskRule = {
  id: string;
  code: string;
  name: string;
  type: string;
  conditionValue?: string;
  threshold: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  enabled: boolean;
};

export type SystemSetting = {
  id: string;
  settingKey: string;
  category: string;
  valueType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON';
  settingValue?: string;
  description?: string;
  sensitive: boolean;
};

export type AuditEvent = {
  id: string;
  actor: string;
  action: string;
  targetType: string;
  targetId: string;
  detail?: string;
  createdAt: string;
};

export type AuditEventList = {
  totalResults: number;
  limit: number;
  resources: AuditEvent[];
};

export type Catalog = {
  modules: Array<{ code: string; name: string; description: string }>;
  applicationProtocols: string[];
  identitySourceTypes: string[];
};
