import { defineConfig } from 'vitepress';

// 文档站部署在 IAM 前端同域的 /docs/ 下，由 nginx 直接提供构建产物。
export default defineConfig({
  lang: 'zh-CN',
  title: 'Js IAM 文档中心',
  description: '系统企业身份与访问管理文档中心',
  base: '/docs/',
  cleanUrls: false,
  lastUpdated: false,
  srcExclude: ['README.md'],
  head: [['link', { rel: 'icon', href: '/docs/favicon.svg' }]],
  themeConfig: {
    logo: '/favicon.svg',
    siteTitle: 'Js IAM 文档中心',
    nav: [
      { text: '产品介绍', link: '/product', activeMatch: '/product' },
      { text: '使用说明', link: '/usage', activeMatch: '/usage' },
      { text: '对接文档', link: '/integration', activeMatch: '/integration' },
    ],
    sidebar: {
      '/product': [
        {
          text: '产品介绍',
          items: [
            { text: '产品定位', link: '/product#positioning' },
            { text: '企业常见难题', link: '/product#challenges' },
            { text: '平台能力', link: '/product#capabilities' },
            { text: '平台特点', link: '/product#features' },
            { text: '典型场景', link: '/product#scenarios' },
            { text: '交付形态', link: '/product#delivery' },
            { text: '后续规划', link: '/product#roadmap' },
          ],
        },
      ],
      '/usage': [
        {
          text: '使用说明',
          items: [
            { text: '登录系统', link: '/usage#login' },
            { text: '门户中心', link: '/usage#portal' },
            { text: '账号安全', link: '/usage#account' },
            { text: '会话与日志', link: '/usage#sessions' },
            { text: '常见问题', link: '/usage#faq' },
          ],
        },
      ],
      '/integration': [
        {
          text: '对接文档',
          items: [
            { text: '接入前准备', link: '/integration#prepare' },
            { text: '选择接入方式', link: '/integration#choose' },
            { text: '调用约定', link: '/integration#conventions' },
            { text: 'OIDC / OAuth2', link: '/integration#oidc' },
            { text: '应用内权限', link: '/integration#app-permissions' },
            { text: 'SAML 2.0', link: '/integration#saml' },
            { text: 'CAS', link: '/integration#cas' },
            { text: 'JWT', link: '/integration#jwt' },
            { text: 'SCIM / 管理 API', link: '/integration#directory' },
            { text: '接入自查', link: '/integration#checklist' },
          ],
        },
      ],
    },
    outline: { level: [2, 3], label: '本页目录' },
    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
          modal: {
            noResultsText: '没有找到相关结果',
            resetButtonTitle: '清除搜索条件',
            footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
          },
        },
      },
    },
    docFooter: { prev: '上一页', next: '下一页' },
    darkModeSwitchLabel: '外观',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式',
    sidebarMenuLabel: '目录',
    returnToTopLabel: '回到顶部',
    footer: { message: 'Js IAM 文档中心 · 以当前部署环境的管理员配置为准' },
  },
});
