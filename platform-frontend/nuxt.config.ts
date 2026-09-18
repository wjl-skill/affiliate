// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2026-09-03',
  ssr: false, // 网盟管理后台采用 SPA/CSR 纯客户端交互模式，保障低时延与富状态操作
  future: {
    compatibilityVersion: 4 // 开启 Nuxt 4 规范与现代架构体系
  },
  devtools: { enabled: false },
  modules: [
    '@nuxtjs/tailwindcss'
  ],
  app: {
    head: {
      title: 'Affiliate Platform - 商业级网盟运营管理后台',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: '新一代高性能商业级广告网络与效果营销管理系统' }
      ],
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: 'data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🚀</text></svg>' }
      ]
    }
  },
  runtimeConfig: {
    public: {
      // 同源为空字符串时走 Nitro 代理；独立部署可指向后端网关地址
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '',
      tenantId: process.env.NUXT_PUBLIC_TENANT_ID || 'tenant-1'
    }
  },
  nitro: {
    routeRules: {
      '/api/**': {
        proxy: 'http://localhost:8080/api/**'
      },
      // 点击追踪与 S2S Postback 回传链路不在 /api 前缀内，需同样代理到后端；
      // 仅精确匹配这两个后端端点，避免吞掉 /affiliate/macros、/affiliate/anti-fraud 等前端页面路由
      '/affiliate/click': {
        proxy: 'http://localhost:8080/affiliate/click'
      },
      '/affiliate/postback': {
        proxy: 'http://localhost:8080/affiliate/postback'
      }
    }
  }
})
