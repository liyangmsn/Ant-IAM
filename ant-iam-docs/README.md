# 系统文档站

基于 [VitePress](https://vitepress.dev/) 的用户文档站点，部署在前端同域的 `/docs/` 路径下。

| 页面 | 读者 |
| --- | --- |
| `product.md` 产品介绍 | 企业决策者与 IT 负责人 |
| `usage.md` 使用说明 | 普通用户和管理员 |
| `integration.md` 对接文档 | 接入 OIDC、OAuth2、SAML、CAS、JWT、SCIM 的开发者 |

本站是对外公开的精简版；内部完整的接入指南、设计方案在仓库根目录的 `docs/` 下，不会发布到本站。

## 本地开发

```bash
cd ant-iam-docs
pnpm install
pnpm dev        # http://localhost:5175/docs/ ，修改后实时刷新
pnpm build      # 输出到 .vitepress/dist
```

前端开发服务器（`ant-iam-frontend` 的 `pnpm dev`）会在 `/docs/` 下提供 `.vitepress/dist` 的构建产物，与生产 nginx 行为一致，需要先执行一次 `pnpm build`。

## 编写约定

- 页面里的流程图是内嵌 SVG，配色用 `.vitepress/theme/custom.css` 中的 `.box`、`.accent`、`.ok` 等类，已适配深色模式。
- 指向 IAM 自身页面（如 `/login`、`/.well-known/openid-configuration`）的链接请写成 `<a href="/login" target="_self">`，否则会被 VitePress 当作站内页面处理。
- 控制台「协议配置」页会链接到 `/docs/integration.html#oidc` 等锚点，修改对接文档的标题时请保留 `{#oidc}`、`{#saml}`、`{#cas}`、`{#jwt}`、`{#choose}` 这些自定义锚点。

## 部署

`Dockerfile.frontend` 中的 `docsdist` 阶段执行 `pnpm build`，产物复制到 nginx 的 `/usr/share/nginx/html/docs/`。
