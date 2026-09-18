<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-950 px-4"
       style="background-image: radial-gradient(ellipse at top, rgba(79,70,229,0.25), transparent 60%)">
    <div class="w-full max-w-md">
      <!-- 品牌区 -->
      <div class="flex items-center justify-center gap-3 mb-8">
        <div class="w-11 h-11 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-400 flex items-center justify-center text-white font-black text-2xl shadow-lg shadow-brand-500/30">
          A
        </div>
        <div>
          <h1 class="text-lg font-bold text-white tracking-tight">Affiliate Platform</h1>
          <p class="text-[11px] text-slate-400 font-mono uppercase tracking-widest">Net & Programmatic 运营后台</p>
        </div>
      </div>

      <!-- 登录卡片 -->
      <div class="bg-white rounded-2xl shadow-2xl border border-slate-200 p-8">
        <h2 class="text-base font-bold text-slate-900">账号登录</h2>
        <p class="text-xs text-slate-500 mt-1 mb-6">请输入管理后台账号凭证，登录状态有效期 12 小时</p>

        <form class="space-y-4" @submit.prevent="handleLogin">
          <div>
            <label class="block text-xs font-semibold text-slate-600 mb-1.5">用户名</label>
            <input
              v-model.trim="form.username"
              type="text"
              autocomplete="username"
              placeholder="如：admin"
              class="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
            />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-600 mb-1.5">密码</label>
            <input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入登录密码"
              class="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
            />
          </div>

          <p v-if="errorMsg" class="text-xs font-medium text-rose-600 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2">
            {{ errorMsg }}
          </p>

          <button
            type="submit"
            :disabled="loading"
            class="w-full rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-bold py-2.5 shadow-md shadow-brand-600/30 transition-colors"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </button>
        </form>

        <div class="mt-6 pt-4 border-t border-slate-100 text-[11px] text-slate-400 leading-relaxed">
          初始演示账号：admin / alex_bd / sarah_fin，默认密码 Admin@123
        </div>
      </div>

      <p class="text-center text-[11px] text-slate-500 mt-6">
        受保护系统 · 所有操作均记录审计日志
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ layout: false })

const route = useRoute()
const { login, safeRedirect } = useAuth()

const form = ref({ username: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

const handleLogin = async () => {
  errorMsg.value = ''
  if (!form.value.username || !form.value.password) {
    errorMsg.value = '请输入用户名与密码'
    return
  }
  loading.value = true
  try {
    await login(form.value.username, form.value.password)
    window.location.href = safeRedirect(route.query.redirect)
  } catch (err: any) {
    errorMsg.value = err?.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
