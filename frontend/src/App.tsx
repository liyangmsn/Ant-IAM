import {
  Activity,
  AlertTriangle,
  AppWindow,
  Building2,
  CheckCircle2,
  DatabaseZap,
  FileClock,
  Gauge,
  KeyRound,
  Loader2,
  PlugZap,
  RefreshCcw,
  Save,
  Search,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  UserPlus,
  Users,
  XCircle,
} from 'lucide-react';
import { ReactNode, useEffect, useMemo, useState } from 'react';
import { ApiClient, ApiConfig, defaultConfig, saveConfig } from './api';
import { loadDashboard, loadWorkspace } from './data';
import type {
  Application,
  AuditEvent,
  Catalog,
  DashboardMetrics,
  DashboardSummary,
  IdentitySource,
  Organization,
  RiskRule,
  SystemSetting,
  User,
} from './types';

type Section = 'dashboard' | 'users' | 'organizations' | 'applications' | 'identitySources' | 'risk' | 'settings' | 'audit';

type Workspace = {
  catalog?: Catalog;
  users: User[];
  organizations: Organization[];
  applications: Application[];
  identitySources: IdentitySource[];
  riskRules: RiskRule[];
  settings: SystemSetting[];
  auditEvents: AuditEvent[];
};

type Mutate = (label: string, task: () => Promise<unknown>) => Promise<void>;

const emptyWorkspace: Workspace = {
  users: [],
  organizations: [],
  applications: [],
  identitySources: [],
  riskRules: [],
  settings: [],
  auditEvents: [],
};

const navItems: Array<{ id: Section; label: string; icon: ReactNode }> = [
  { id: 'dashboard', label: '总览', icon: <Gauge size={18} /> },
  { id: 'users', label: '用户', icon: <Users size={18} /> },
  { id: 'organizations', label: '组织', icon: <Building2 size={18} /> },
  { id: 'applications', label: '应用', icon: <AppWindow size={18} /> },
  { id: 'identitySources', label: '身份源', icon: <PlugZap size={18} /> },
  { id: 'risk', label: '风险', icon: <AlertTriangle size={18} /> },
  { id: 'settings', label: '设置', icon: <Settings size={18} /> },
  { id: 'audit', label: '审计', icon: <FileClock size={18} /> },
];

const titles: Record<Section, string> = {
  dashboard: '控制台总览',
  users: '用户目录',
  organizations: '组织架构',
  applications: '应用访问',
  identitySources: '身份源同步',
  risk: '风险控制',
  settings: '系统设置',
  audit: '审计事件',
};

const inputClass =
  'min-h-9 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-600 focus:ring-4 focus:ring-blue-600/10';
const panelClass = 'min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white';
const panelHeaderClass = 'flex min-h-12 items-center gap-2 border-b border-slate-200 px-4 text-amber-700';
const primaryButtonClass =
  'inline-flex min-h-9 items-center justify-center gap-2 rounded-md border border-teal-800 bg-teal-800 px-4 text-sm font-semibold text-white transition hover:bg-teal-900 disabled:cursor-not-allowed disabled:opacity-60';

export function App() {
  const [config, setConfig] = useState<ApiConfig>(defaultConfig);
  const [active, setActive] = useState<Section>('dashboard');
  const [query, setQuery] = useState('');
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [workspace, setWorkspace] = useState<Workspace>(emptyWorkspace);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<'idle' | 'ok' | 'error'>('idle');
  const [message, setMessage] = useState('等待连接');
  const api = useMemo(() => new ApiClient(config), [config]);
  const filtered = useMemo(() => filterWorkspace(workspace, query), [workspace, query]);

  async function refresh() {
    setLoading(true);
    setStatus('idle');
    try {
      const [[nextSummary, nextMetrics], [catalog, users, organizations, applications, identitySources, riskRules, settings, auditList]] =
        await Promise.all([loadDashboard(api), loadWorkspace(api)]);
      setSummary(nextSummary);
      setMetrics(nextMetrics);
      setWorkspace({
        catalog,
        users,
        organizations,
        applications,
        identitySources,
        riskRules,
        settings,
        auditEvents: auditList.resources,
      });
      setStatus('ok');
      setMessage('已同步');
    } catch (error) {
      setStatus('error');
      setMessage(error instanceof Error ? error.message : '连接失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [api]);

  function updateConfig(next: ApiConfig) {
    setConfig(next);
    saveConfig(next);
  }

  async function mutate(label: string, task: () => Promise<unknown>) {
    setLoading(true);
    try {
      await task();
      setStatus('ok');
      setMessage(`${label}成功`);
      await refresh();
    } catch (error) {
      setStatus('error');
      setMessage(error instanceof Error ? error.message : `${label}失败`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900 md:grid md:grid-cols-[248px_minmax(0,1fr)]">
      <aside className="flex min-h-full flex-col bg-slate-900 px-3 py-4 text-slate-100 md:min-h-screen">
        <div className="mb-5 flex items-center gap-3 px-2">
          <ShieldCheck size={24} />
          <div>
            <strong className="block text-lg leading-6">Ant IAM</strong>
            <span className="hidden text-xs text-slate-400 sm:inline">Console</span>
          </div>
        </div>
        <nav className="grid grid-cols-4 gap-1 md:grid-cols-1">
          {navItems.map((item) => (
            <button
              key={item.id}
              className={`flex min-h-10 items-center justify-center gap-2 rounded-md px-3 text-sm transition md:justify-start ${
                active === item.id ? 'bg-emerald-50 text-teal-900' : 'text-slate-200 hover:bg-slate-800'
              }`}
              onClick={() => setActive(item.id)}
              title={item.label}
            >
              {item.icon}
              <span className="hidden md:inline">{item.label}</span>
            </button>
          ))}
        </nav>
        <div className="mt-auto hidden items-center gap-2 px-2 py-3 text-sm text-slate-300 md:flex">
          <StatusPill status={status} />
          <span className="truncate">{message}</span>
        </div>
      </aside>

      <main className="min-w-0 p-4 md:p-6">
        <header className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="mb-1 text-xs font-semibold uppercase text-slate-500">Ant IAM</p>
            <h1 className="text-2xl font-bold tracking-normal text-slate-950 md:text-3xl">{titles[active]}</h1>
          </div>
          <div className="flex items-center gap-2">
            <label className="flex h-10 w-full items-center gap-2 rounded-md border border-slate-300 bg-white px-3 md:w-72">
              <Search size={16} className="text-slate-500" />
              <input
                className="w-full border-0 bg-transparent text-sm outline-none"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="搜索资源"
              />
            </label>
            <button
              className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-800 transition hover:border-slate-400"
              onClick={refresh}
              disabled={loading}
              title="刷新"
            >
              {loading ? <Loader2 className="animate-spin" size={18} /> : <RefreshCcw size={18} />}
            </button>
          </div>
        </header>

        <ConnectionBar config={config} onChange={updateConfig} />

        {active === 'dashboard' && <Dashboard summary={summary} metrics={metrics} workspace={workspace} />}
        {active === 'users' && <UsersPage api={api} users={filtered.users} organizations={workspace.organizations} mutate={mutate} />}
        {active === 'organizations' && <OrganizationsPage api={api} organizations={filtered.organizations} mutate={mutate} />}
        {active === 'applications' && <ApplicationsPage api={api} applications={filtered.applications} mutate={mutate} />}
        {active === 'identitySources' && (
          <IdentitySourcesPage api={api} sources={filtered.identitySources} catalog={workspace.catalog} mutate={mutate} />
        )}
        {active === 'risk' && <RiskPage api={api} rules={filtered.riskRules} mutate={mutate} />}
        {active === 'settings' && <SettingsPage api={api} settings={filtered.settings} mutate={mutate} />}
        {active === 'audit' && <AuditPage events={filtered.auditEvents} />}
      </main>
    </div>
  );
}

function ConnectionBar({ config, onChange }: { config: ApiConfig; onChange: (config: ApiConfig) => void }) {
  const [draft, setDraft] = useState(config);
  useEffect(() => setDraft(config), [config]);

  return (
    <form
      className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-3 md:grid-cols-[minmax(240px,1.4fr)_minmax(150px,0.7fr)_minmax(150px,0.7fr)_auto] md:items-end"
      onSubmit={(event) => {
        event.preventDefault();
        onChange(draft);
      }}
    >
      <Field label="API">
        <input className={inputClass} value={draft.baseUrl} onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })} />
      </Field>
      <Field label="账号">
        <input className={inputClass} value={draft.username} onChange={(event) => setDraft({ ...draft, username: event.target.value })} />
      </Field>
      <Field label="密码">
        <input
          className={inputClass}
          type="password"
          value={draft.password}
          onChange={(event) => setDraft({ ...draft, password: event.target.value })}
        />
      </Field>
      <button className={primaryButtonClass} type="submit">
        <Save size={16} />
        保存
      </button>
    </form>
  );
}

function Dashboard({ summary, metrics, workspace }: { summary: DashboardSummary | null; metrics: DashboardMetrics | null; workspace: Workspace }) {
  const stats = [
    ['用户', summary?.users ?? workspace.users.length, Users],
    ['活跃用户', summary?.activeUsers ?? workspace.users.filter((user) => user.status === 'ACTIVE').length, CheckCircle2],
    ['组织', summary?.organizations ?? workspace.organizations.length, Building2],
    ['应用', summary?.applications ?? workspace.applications.length, AppWindow],
    ['身份源', summary?.identitySources ?? workspace.identitySources.length, PlugZap],
    ['活跃会话', summary?.activeSessions ?? 0, Activity],
    ['高风险', summary?.highRiskAssessments ?? 0, AlertTriangle],
    ['待审批', summary?.pendingAccessRequests ?? 0, FileClock],
  ] as const;

  return (
    <section className="grid gap-4 xl:grid-cols-2">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:col-span-2 xl:grid-cols-8">
        {stats.map(([label, value, Icon]) => (
          <div className="grid min-h-24 content-center gap-1 rounded-lg border border-slate-200 bg-white p-4" key={label}>
            <Icon size={20} className="text-teal-800" />
            <strong className="text-2xl leading-7">{value}</strong>
            <span className="text-sm text-slate-500">{label}</span>
          </div>
        ))}
      </div>
      <section className={`${panelClass} xl:col-span-2`}>
        <PanelHeader icon={<Activity size={18} />} title="认证事件" />
        <BarList data={metrics?.authenticationEvents ?? {}} />
      </section>
      <section className={panelClass}>
        <PanelHeader icon={<AlertTriangle size={18} />} title="风险分布" />
        <BarList data={metrics?.riskAssessments ?? {}} />
      </section>
      <section className={panelClass}>
        <PanelHeader icon={<DatabaseZap size={18} />} title="同步结果" />
        <BarList data={metrics?.syncRuns ?? {}} />
      </section>
    </section>
  );
}

function UsersPage({ api, users, organizations, mutate }: { api: ApiClient; users: User[]; organizations: Organization[]; mutate: Mutate }) {
  const [form, setForm] = useState({ username: '', displayName: '', email: '', mobile: '', organizationId: '', initialPassword: '' });
  return (
    <ResourceLayout
      form={
        <ResourcePanel icon={<UserPlus size={18} />} title="创建用户">
          <form
            className="grid gap-3 p-4"
            onSubmit={(event) => {
              event.preventDefault();
              void mutate('创建用户', () =>
                api.request('/api/v1/users', {
                  body: clean({ ...form, organizationId: form.organizationId || undefined, initialPassword: form.initialPassword || undefined }),
                }),
              );
            }}
          >
            <input className={inputClass} required placeholder="账号" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} />
            <input className={inputClass} required placeholder="姓名" value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} />
            <input className={inputClass} placeholder="邮箱" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
            <input className={inputClass} placeholder="手机号" value={form.mobile} onChange={(event) => setForm({ ...form, mobile: event.target.value })} />
            <select className={inputClass} value={form.organizationId} onChange={(event) => setForm({ ...form, organizationId: event.target.value })}>
              <option value="">未绑定组织</option>
              {organizations.map((organization) => (
                <option key={organization.id} value={organization.id}>
                  {organization.name}
                </option>
              ))}
            </select>
            <input
              className={inputClass}
              placeholder="初始密码"
              type="password"
              value={form.initialPassword}
              onChange={(event) => setForm({ ...form, initialPassword: event.target.value })}
            />
            <SubmitButton icon={<UserPlus size={16} />} label="创建" />
          </form>
        </ResourcePanel>
      }
      table={
        <DataPanel title="用户列表">
          <DataTable
            columns={['账号', '姓名', '状态', '邮箱', '手机号', '操作']}
            rows={users.map((user) => [
              user.username,
              user.displayName,
              <StatusBadge key="status" value={user.status} />,
              user.email ?? '-',
              user.mobile ?? '-',
              <RowActions
                key="actions"
                actions={[
                  ['激活', () => mutate('激活用户', () => api.request(`/api/v1/users/${user.id}/activations`, { method: 'POST' }))],
                  ['暂停', () => mutate('暂停用户', () => api.request(`/api/v1/users/${user.id}/suspensions`, { method: 'POST' }))],
                  ['锁定', () => mutate('锁定用户', () => api.request(`/api/v1/users/${user.id}/locks`, { method: 'POST' }))],
                ]}
              />,
            ])}
          />
        </DataPanel>
      }
    />
  );
}

function OrganizationsPage({ api, organizations, mutate }: { api: ApiClient; organizations: Organization[]; mutate: Mutate }) {
  const [form, setForm] = useState({ code: '', name: '', parentId: '' });
  return (
    <ResourceLayout
      form={
        <ResourcePanel icon={<Building2 size={18} />} title="创建组织">
          <SimpleForm onSubmit={() => mutate('创建组织', () => api.request('/api/v1/organizations', { body: clean(form) }))}>
            <input className={inputClass} required placeholder="编码" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} />
            <input className={inputClass} required placeholder="名称" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            <select className={inputClass} value={form.parentId} onChange={(event) => setForm({ ...form, parentId: event.target.value })}>
              <option value="">根组织</option>
              {organizations.map((organization) => (
                <option key={organization.id} value={organization.id}>
                  {organization.name}
                </option>
              ))}
            </select>
            <SubmitButton icon={<Building2 size={16} />} label="创建" />
          </SimpleForm>
        </ResourcePanel>
      }
      table={
        <DataPanel title="组织列表">
          <DataTable columns={['编码', '名称', '父组织']} rows={organizations.map((item) => [item.code, item.name, item.parentId ?? '-'])} />
        </DataPanel>
      }
    />
  );
}

function ApplicationsPage({ api, applications, mutate }: { api: ApiClient; applications: Application[]; mutate: Mutate }) {
  const [form, setForm] = useState({ code: '', name: '', protocol: 'OIDC', loginUrl: '', selfServiceAccessRequestEnabled: true });
  return (
    <ResourceLayout
      form={
        <ResourcePanel icon={<AppWindow size={18} />} title="创建应用">
          <SimpleForm onSubmit={() => mutate('创建应用', () => api.request('/api/v1/access/applications', { body: clean(form) }))}>
            <input className={inputClass} required placeholder="编码" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} />
            <input className={inputClass} required placeholder="名称" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            <select className={inputClass} value={form.protocol} onChange={(event) => setForm({ ...form, protocol: event.target.value })}>
              {['OIDC', 'SAML', 'CAS', 'FORM', 'JWT'].map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
            <input className={inputClass} placeholder="登录地址" value={form.loginUrl} onChange={(event) => setForm({ ...form, loginUrl: event.target.value })} />
            <Checkbox checked={form.selfServiceAccessRequestEnabled} onChange={(checked) => setForm({ ...form, selfServiceAccessRequestEnabled: checked })}>
              开放自助申请
            </Checkbox>
            <SubmitButton icon={<AppWindow size={16} />} label="创建" />
          </SimpleForm>
        </ResourcePanel>
      }
      table={
        <DataPanel title="应用列表">
          <DataTable
            columns={['编码', '名称', '协议', '状态', '登录地址', '操作']}
            rows={applications.map((app) => [
              app.code,
              app.name,
              app.protocol,
              <StatusBadge key="enabled" value={app.enabled ? 'ENABLED' : 'DISABLED'} />,
              app.loginUrl ?? '-',
              <RowActions
                key="actions"
                actions={[
                  ['启用', () => mutate('启用应用', () => api.request(`/api/v1/access/applications/${app.id}/activations`, { method: 'POST' }))],
                  ['停用', () => mutate('停用应用', () => api.request(`/api/v1/access/applications/${app.id}/suspensions`, { method: 'POST' }))],
                ]}
              />,
            ])}
          />
        </DataPanel>
      }
    />
  );
}

function IdentitySourcesPage({ api, sources, catalog, mutate }: { api: ApiClient; sources: IdentitySource[]; catalog?: Catalog; mutate: Mutate }) {
  const typeOptions = catalog?.identitySourceTypes?.length
    ? catalog.identitySourceTypes
    : ['LDAP', 'ACTIVE_DIRECTORY', 'DINGTALK', 'WECHAT_WORK', 'FEISHU', 'SCIM', 'CUSTOM'];
  const [form, setForm] = useState({ code: '', name: '', type: typeOptions[0] ?? 'CUSTOM' });
  return (
    <ResourceLayout
      form={
        <ResourcePanel icon={<PlugZap size={18} />} title="创建身份源">
          <SimpleForm onSubmit={() => mutate('创建身份源', () => api.request('/api/v1/identity-sources', { body: form }))}>
            <input className={inputClass} required placeholder="编码" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} />
            <input className={inputClass} required placeholder="名称" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            <select className={inputClass} value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })}>
              {typeOptions.map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
            <SubmitButton icon={<PlugZap size={16} />} label="创建" />
          </SimpleForm>
        </ResourcePanel>
      }
      table={
        <DataPanel title="身份源列表">
          <DataTable
            columns={['编码', '名称', '类型', '状态', '操作']}
            rows={sources.map((source) => [
              source.code,
              source.name,
              source.type,
              <StatusBadge key="enabled" value={source.enabled ? 'ENABLED' : 'DISABLED'} />,
              <RowActions
                key="actions"
                actions={[
                  ['启用', () => mutate('启用身份源', () => api.request(`/api/v1/identity-sources/${source.id}/activations`, { method: 'POST' }))],
                  ['停用', () => mutate('停用身份源', () => api.request(`/api/v1/identity-sources/${source.id}/suspensions`, { method: 'POST' }))],
                ]}
              />,
            ])}
          />
        </DataPanel>
      }
    />
  );
}

function RiskPage({ api, rules, mutate }: { api: ApiClient; rules: RiskRule[]; mutate: Mutate }) {
  const [form, setForm] = useState({ code: '', name: '', type: 'IP_CONTAINS', conditionValue: '', threshold: 1, riskLevel: 'MEDIUM' });
  return (
    <ResourceLayout
      form={
        <ResourcePanel icon={<AlertTriangle size={18} />} title="创建风险规则">
          <SimpleForm onSubmit={() => mutate('创建风险规则', () => api.request('/api/v1/risk/rules', { body: clean(form) }))}>
            <input className={inputClass} required placeholder="编码" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} />
            <input className={inputClass} required placeholder="名称" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            <select className={inputClass} value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })}>
              {['IP_CONTAINS', 'USER_AGENT_CONTAINS', 'FAILED_LOGIN_COUNT', 'DEVICE_FINGERPRINT_CONTAINS', 'DEVICE_FINGERPRINT_CHANGED', 'GEO_LOCATION_NOT_ALLOWED'].map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
            <input className={inputClass} placeholder="匹配值" value={form.conditionValue} onChange={(event) => setForm({ ...form, conditionValue: event.target.value })} />
            <input className={inputClass} type="number" min={0} value={form.threshold} onChange={(event) => setForm({ ...form, threshold: Number(event.target.value) })} />
            <select className={inputClass} value={form.riskLevel} onChange={(event) => setForm({ ...form, riskLevel: event.target.value })}>
              {['LOW', 'MEDIUM', 'HIGH'].map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
            <SubmitButton icon={<AlertTriangle size={16} />} label="创建" />
          </SimpleForm>
        </ResourcePanel>
      }
      table={
        <DataPanel title="风险规则">
          <DataTable
            columns={['编码', '名称', '类型', '等级', '状态', '操作']}
            rows={rules.map((rule) => [
              rule.code,
              rule.name,
              rule.type,
              <StatusBadge key="level" value={rule.riskLevel} />,
              <StatusBadge key="enabled" value={rule.enabled ? 'ENABLED' : 'DISABLED'} />,
              <RowActions
                key="actions"
                actions={[
                  ['启用', () => mutate('启用规则', () => api.request(`/api/v1/risk/rules/${rule.id}/activations`, { method: 'POST' }))],
                  ['停用', () => mutate('停用规则', () => api.request(`/api/v1/risk/rules/${rule.id}/suspensions`, { method: 'POST' }))],
                ]}
              />,
            ])}
          />
        </DataPanel>
      }
    />
  );
}

function SettingsPage({ api, settings, mutate }: { api: ApiClient; settings: SystemSetting[]; mutate: Mutate }) {
  const [form, setForm] = useState({ settingKey: '', category: 'security', valueType: 'STRING', settingValue: '', description: '', sensitive: false });
  return (
    <ResourceLayout
      form={
        <ResourcePanel icon={<SlidersHorizontal size={18} />} title="保存配置">
          <SimpleForm onSubmit={() => mutate('保存配置', () => api.request('/api/v1/settings', { body: clean(form) }))}>
            <input className={inputClass} required placeholder="配置键" value={form.settingKey} onChange={(event) => setForm({ ...form, settingKey: event.target.value })} />
            <input className={inputClass} required placeholder="分类" value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value })} />
            <select className={inputClass} value={form.valueType} onChange={(event) => setForm({ ...form, valueType: event.target.value })}>
              {['STRING', 'NUMBER', 'BOOLEAN', 'JSON'].map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
            <textarea className={`${inputClass} min-h-24 resize-y`} placeholder="配置值" value={form.settingValue} onChange={(event) => setForm({ ...form, settingValue: event.target.value })} />
            <input className={inputClass} placeholder="说明" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
            <Checkbox checked={form.sensitive} onChange={(checked) => setForm({ ...form, sensitive: checked })}>
              敏感配置
            </Checkbox>
            <SubmitButton icon={<Save size={16} />} label="保存" />
          </SimpleForm>
        </ResourcePanel>
      }
      table={
        <DataPanel title="配置列表">
          <DataTable
            columns={['键', '分类', '类型', '值', '敏感']}
            rows={settings.map((setting) => [setting.settingKey, setting.category, setting.valueType, setting.settingValue ?? '-', setting.sensitive ? '是' : '否'])}
          />
        </DataPanel>
      }
    />
  );
}

function AuditPage({ events }: { events: AuditEvent[] }) {
  return (
    <DataPanel title="最近审计事件">
      <DataTable
        columns={['时间', '操作者', '动作', '目标', '详情']}
        rows={events.map((event) => [
          new Date(event.createdAt).toLocaleString(),
          event.actor ?? '-',
          event.action,
          `${event.targetType}:${event.targetId}`,
          event.detail ?? '-',
        ])}
      />
    </DataPanel>
  );
}

function ResourceLayout({ form, table }: { form: ReactNode; table: ReactNode }) {
  return <section className="grid items-start gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">{form}{table}</section>;
}

function ResourcePanel({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <section className={`${panelClass} lg:sticky lg:top-4`}>
      <PanelHeader icon={icon} title={title} />
      {children}
    </section>
  );
}

function DataPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className={panelClass}>
      <PanelHeader icon={<DatabaseZap size={18} />} title={title} />
      {children}
    </section>
  );
}

function PanelHeader({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <div className={panelHeaderClass}>
      {icon}
      <h2 className="text-base font-bold text-slate-950">{title}</h2>
    </div>
  );
}

function SimpleForm({ children, onSubmit }: { children: ReactNode; onSubmit: () => Promise<void> }) {
  return (
    <form
      className="grid gap-3 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        void onSubmit();
      }}
    >
      {children}
    </form>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid gap-1 text-xs font-medium text-slate-500">
      {label}
      {children}
    </label>
  );
}

function Checkbox({ checked, onChange, children }: { checked: boolean; onChange: (checked: boolean) => void; children: ReactNode }) {
  return (
    <label className="flex items-center gap-2 text-sm text-slate-800">
      <input className="h-4 w-4 rounded border-slate-300 text-teal-800" type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
      {children}
    </label>
  );
}

function DataTable({ columns, rows }: { columns: string[]; rows: ReactNode[][] }) {
  return (
    <div className="overflow-auto">
      <table className="min-w-[720px] w-full border-collapse">
        <thead>
          <tr>
            {columns.map((column) => (
              <th className="border-b border-slate-200 bg-slate-100 px-4 py-3 text-left text-sm font-bold text-slate-600" key={column}>
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="border-b border-slate-200 px-4 py-8 text-center text-sm text-slate-500">
                暂无数据
              </td>
            </tr>
          ) : (
            rows.map((row, rowIndex) => (
              <tr className="hover:bg-slate-50" key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <td className="border-b border-slate-200 px-4 py-3 text-sm leading-5 text-slate-800" key={cellIndex}>
                    {cell}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

function RowActions({ actions }: { actions: Array<[string, () => void]> }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {actions.map(([label, action]) => (
        <button className="min-h-8 rounded-md border border-slate-300 bg-white px-2.5 text-xs font-medium text-slate-800 hover:border-slate-400" key={label} type="button" onClick={action}>
          {label}
        </button>
      ))}
    </div>
  );
}

function SubmitButton({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <button className={primaryButtonClass} type="submit">
      {icon}
      {label}
    </button>
  );
}

function StatusPill({ status }: { status: 'idle' | 'ok' | 'error' }) {
  if (status === 'ok') {
    return <CheckCircle2 className="text-emerald-400" size={16} />;
  }
  if (status === 'error') {
    return <XCircle className="text-red-400" size={16} />;
  }
  return <KeyRound className="text-amber-400" size={16} />;
}

function StatusBadge({ value }: { value: string }) {
  const color = badgeColor(value);
  return <span className={`inline-flex min-h-6 items-center rounded-full border px-2 text-xs font-bold ${color}`}>{value}</span>;
}

function BarList({ data }: { data: Record<string, number> }) {
  const entries = Object.entries(data);
  const max = Math.max(...entries.map(([, value]) => value), 1);
  if (entries.length === 0) {
    return <div className="p-7 text-center text-sm text-slate-500">暂无指标</div>;
  }
  return (
    <div className="grid gap-3 p-4">
      {entries.map(([label, value]) => (
        <div className="grid grid-cols-[140px_1fr_44px] items-center gap-3 text-sm sm:grid-cols-[180px_1fr_44px]" key={label}>
          <span className="truncate text-slate-600">{label}</span>
          <div className="h-2.5 overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-gradient-to-r from-teal-800 to-amber-600" style={{ width: `${(value / max) * 100}%` }} />
          </div>
          <strong className="text-right">{value}</strong>
        </div>
      ))}
    </div>
  );
}

function badgeColor(value: string) {
  const normalized = value.toLowerCase();
  if (['active', 'enabled', 'low'].includes(normalized)) {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700';
  }
  if (['medium', 'suspended'].includes(normalized)) {
    return 'border-amber-200 bg-amber-50 text-amber-700';
  }
  if (['high', 'locked', 'departed', 'disabled'].includes(normalized)) {
    return 'border-red-200 bg-red-50 text-red-700';
  }
  return 'border-slate-200 bg-slate-50 text-slate-700';
}

function filterWorkspace(workspace: Workspace, query: string): Workspace {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return workspace;
  }
  const match = (value: unknown) => JSON.stringify(value).toLowerCase().includes(normalized);
  return {
    ...workspace,
    users: workspace.users.filter(match),
    organizations: workspace.organizations.filter(match),
    applications: workspace.applications.filter(match),
    identitySources: workspace.identitySources.filter(match),
    riskRules: workspace.riskRules.filter(match),
    settings: workspace.settings.filter(match),
    auditEvents: workspace.auditEvents.filter(match),
  };
}

function clean<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== '')) as T;
}
