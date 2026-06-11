export type ApiConfig = {
  baseUrl: string;
  username: string;
  password: string;
};

export type RequestOptions = {
  query?: Record<string, string | number | boolean | null | undefined>;
  body?: unknown;
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
};

export class ApiClient {
  constructor(private readonly config: ApiConfig) {}

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const url = new URL(path, this.config.baseUrl);
    Object.entries(options.query ?? {}).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
    const headers = new Headers({
      Authorization: `Basic ${btoa(`${this.config.username}:${this.config.password}`)}`,
    });
    if (options.body !== undefined) {
      headers.set('Content-Type', 'application/json');
    }
    const response = await fetch(url, {
      method: options.method ?? (options.body === undefined ? 'GET' : 'POST'),
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
    if (!response.ok) {
      const message = await response.text();
      throw new Error(message || `${response.status} ${response.statusText}`);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    const contentType = response.headers.get('content-type') ?? '';
    if (!contentType.includes('application/json')) {
      return (await response.text()) as T;
    }
    return response.json() as Promise<T>;
  }
}

export const defaultConfig: ApiConfig = {
  baseUrl: localStorage.getItem('antiam.api.baseUrl') ?? 'http://localhost:8080',
  username: localStorage.getItem('antiam.api.username') ?? 'admin',
  password: localStorage.getItem('antiam.api.password') ?? 'admin123456',
};

export function saveConfig(config: ApiConfig) {
  localStorage.setItem('antiam.api.baseUrl', config.baseUrl);
  localStorage.setItem('antiam.api.username', config.username);
  localStorage.setItem('antiam.api.password', config.password);
}
