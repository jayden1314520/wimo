export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  devtools: { enabled: false },
  ssr: true,

  modules: ['@nuxtjs/i18n'],
  css: ['~/assets/css/main.css'],

  app: {
    // GitHub Pages 子路径部署时由 NUXT_APP_BASE_URL=/wimo/ 注入，默认 /
    baseURL: process.env.NUXT_APP_BASE_URL || '/',
    head: {
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'theme-color', content: '#ffffff' }
      ],
      link: [{ rel: 'icon', type: 'image/svg+xml', href: `${process.env.NUXT_APP_BASE_URL || '/'}favicon.svg` }]
    }
  },

  i18n: {
    strategy: 'prefix',            // /zh/...  /en/... 两种语言均有独立 URL，利于 SEO
    defaultLocale: 'zh',
    langDir: 'locales',            // 相对 i18n/ 目录
    locales: [
      { code: 'zh', language: 'zh-CN', name: '简体中文', file: 'zh.json', dir: 'ltr' },
      { code: 'en', language: 'en-US', name: 'English', file: 'en.json', dir: 'ltr' }
      // 新增语种：在这里加一项 + 在 i18n/locales/ 下加对应 json 即可
    ],
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: 'wimo_locale',
      redirectOn: 'root',
      alwaysRedirect: false,
      fallbackLocale: 'zh'
    },
    bundle: { optimizeTranslationDirective: false }
  },

  nitro: {
    compressPublicAssets: true,
    // 开发环境把 /prod-api 代理到若依后端，规避跨域
    devProxy: {
      '/prod-api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  },

  runtimeConfig: {
    public: {
      // 生产环境用 NUXT_PUBLIC_API_BASE 覆盖成后端域名，如 https://api.wimo.com
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/prod-api'
    }
  }
})
