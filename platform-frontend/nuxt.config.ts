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
  nitro: {
    routeRules: {
      '/api/**': {
        proxy: 'http://localhost:8080/api/**'
      }
    }
  }
})
