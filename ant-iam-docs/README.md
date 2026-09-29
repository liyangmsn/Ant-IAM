# 系统文档项目

独立的用户文档站点，包含：

- `usage.html`：面向普通用户和管理员的使用说明。
- `integration.html`：面向开发者的 OIDC、OAuth2、SAML、CAS、JWT、SCIM 和管理 API 对接说明。

本项目无构建依赖，直接用静态 Web 服务器运行：

```bash
cd ant-iam-docs
python3 -m http.server 8080
```

在当前部署中，文档通过 `/docs/` 路径提供访问。
