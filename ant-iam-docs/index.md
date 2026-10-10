---
layout: home
title: Js IAM 文档中心
titleTemplate: false

hero:
  name: 统一身份与访问管理文档
  tagline: 面向企业用户、管理员和系统集成方，快速了解系统的使用方式与对接流程。
  actions:
    - theme: brand
      text: 开始阅读使用说明
      link: /usage
    - theme: alt
      text: 了解产品
      link: /product
    - theme: alt
      text: 查看对接文档
      link: /integration

features:
  - icon: '01'
    title: 产品介绍
    details: 介绍平台定位、核心能力、平台特点与典型应用场景，适合企业决策者与 IT 负责人阅读。
    link: /product
    linkText: 阅读产品介绍
  - icon: '02'
    title: 使用说明
    details: 介绍登录、门户应用、账号安全、会话管理和常见问题，适合普通用户与管理员阅读。
    link: /usage
    linkText: 阅读使用说明
  - icon: '03'
    title: 对接文档
    details: 介绍 OIDC、OAuth2、SAML、CAS、JWT、SCIM 和管理 API 的接入方式与自查清单。
    link: /integration
    linkText: 阅读对接文档
  - icon: '04'
    title: 身份源同步
    details: 第三方系统（HR、OA、ERP 等）通过 SCIM 2.0 把组织架构、人员和用户组同步到平台。
    link: /directory-sync
    linkText: 阅读同步文档
---

## 常用入口

<!-- 指向 IAM 本身的页面，不经过 VitePress 路由，用原生链接 -->
- <a href="/login" target="_self">登录门户</a>
- <a href="/actuator/health" target="_self">服务健康检查</a>
