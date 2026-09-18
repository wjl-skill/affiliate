<template>
  <div class="flex h-screen bg-slate-50 text-slate-900 overflow-hidden">
    <!-- 侧边栏导航 (Sidebar) -->
    <aside class="w-64 bg-slate-900 text-slate-300 flex flex-col flex-shrink-0 z-20 shadow-xl border-r border-slate-800">
      <!-- 平台 Logo 与名称 -->
      <div class="h-16 flex items-center gap-3 px-6 bg-slate-950 border-b border-slate-800/80">
        <div class="w-8 h-8 rounded-lg bg-gradient-to-tr from-brand-600 to-indigo-400 flex items-center justify-center text-white font-black text-lg shadow-md shadow-brand-500/20">
          A
        </div>
        <div>
          <h1 class="text-sm font-bold tracking-tight text-white">Affiliate Platform</h1>
          <p class="text-[10px] text-slate-400 font-mono tracking-wider uppercase">Net & Programmatic</p>
        </div>
      </div>

      <!-- 动态菜单导航列表 -->
      <nav class="flex-1 px-3 py-4 space-y-4 overflow-y-auto">
        <!-- 业务中心分组 -->
        <div>
          <div class="px-3 text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2">
            网盟业务中心
          </div>
          <div class="space-y-1">
            <NuxtLink
              v-for="item in businessMenus"
              :key="item.path"
              :to="item.path"
              class="flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition-all group"
              :class="route.path === item.path ? 'bg-brand-600 text-white shadow-sm shadow-brand-600/30 font-semibold' : 'text-slate-400 hover:text-white hover:bg-slate-800/60'"
            >
              <span class="text-sm">{{ item.icon }}</span>
              <span>{{ item.title }}</span>
            </NuxtLink>
          </div>
        </div>

        <!-- 系统管理分组 -->
        <div>
          <div class="px-3 text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2">
            系统管理中心
          </div>
          <div class="space-y-1">
            <NuxtLink
              v-for="item in systemMenus"
              :key="item.path"
              :to="item.path"
              class="flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition-all group"
              :class="route.path === item.path ? 'bg-brand-600 text-white shadow-sm shadow-brand-600/30 font-semibold' : 'text-slate-400 hover:text-white hover:bg-slate-800/60'"
            >
              <span class="text-sm">{{ item.icon }}</span>
              <span>{{ item.title }}</span>
            </NuxtLink>
          </div>
        </div>
      </nav>

      <!-- 底部系统运行状态 -->
      <div class="p-4 border-t border-slate-800 bg-slate-950/40 text-xs">
        <div class="flex items-center justify-between text-slate-400">
          <span>后端 API 服务</span>
          <span class="inline-flex items-center gap-1 font-semibold" :class="apiOnline ? 'text-emerald-400' : 'text-rose-400'">
            <span class="w-1.5 h-1.5 rounded-full animate-ping" :class="apiOnline ? 'bg-emerald-400' : 'bg-rose-400'"></span>
            {{ apiOnline ? '在线' : '未连通' }}
          </span>
        </div>
        <div class="mt-2 text-[11px] text-slate-500 font-mono">
          PostgreSQL · Redis · S3 · DNS
        </div>
      </div>
    </aside>

    <!-- 右侧内容工作区 -->
    <div class="flex-1 flex flex-col min-w-0 overflow-hidden">
      <!-- 顶部工作栏 (Topbar) -->
      <header class="h-16 bg-white border-b border-slate-200 flex items-center justify-between px-8 z-10">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-slate-400 uppercase tracking-wider">控制台 /</span>
          <h2 class="text-sm font-bold text-slate-800">{{ currentPageTitle }}</h2>
        </div>

        <div class="flex items-center gap-4">
          <!-- 租户切换选择器 -->
          <div class="flex items-center gap-2 text-xs bg-slate-100 rounded-lg px-3 py-1.5 border border-slate-200">
            <span class="text-slate-500 font-medium">租户:</span>
            <select v-model="currentTenant" class="bg-transparent font-semibold text-slate-800 focus:outline-none cursor-pointer">
              <option v-for="t in tenantList" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </div>

          <!-- 当前登录用户与退出 -->
          <div class="flex items-center gap-2.5 pl-3 border-l border-slate-200">
            <div class="w-8 h-8 rounded-full bg-gradient-to-tr from-brand-600 to-indigo-400 flex items-center justify-center font-bold text-white text-xs">
              {{ userInitials }}
            </div>
            <div class="text-left">
              <p class="text-xs font-bold text-slate-800 leading-none">{{ authUser?.displayName || authUser?.username || '未登录' }}</p>
              <span class="text-[10px] text-brand-600 font-semibold uppercase">{{ primaryRole }}</span>
            </div>
            <button
              class="ml-1 text-xs font-semibold text-slate-500 hover:text-rose-600 border border-slate-200 hover:border-rose-200 rounded-lg px-2.5 py-1 transition-colors"
              :disabled="loggingOut"
              @click="handleLogout"
            >
              {{ loggingOut ? '...' : '退出' }}
            </button>
          </div>
        </div>
      </header>

      <!-- 业务主体路由页面 -->
      <main class="flex-1 overflow-y-auto p-8">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '~/composables/useApi'
import { useAuth } from '~/composables/useAuth'
import { useToasts } from '~/composables/useNotification'

const route = useRoute()
const { currentTenant, fetchApi } = useApi()
const { user: authUser, logout } = useAuth()
const { showToast } = useToasts()

const loggingOut = ref(false)
const userInitials = computed(() => {
  const name = authUser.value?.displayName || authUser.value?.username || '?'
  return name.slice(0, 2).toUpperCase()
})
const primaryRole = computed(() => authUser.value?.roles?.[0] || 'VIEWER')

const handleLogout = async () => {
  loggingOut.value = true
  await logout()
  window.location.href = '/login'
}

// 本地导航配置：接口不可用时保证侧边栏可正常浏览（数据页面均会展示真实错误空态）
const defaultBusinessMenus = [
  { id: 'm-dash', title: '监控大盘', path: '/', icon: '📊' },
  { id: 'm-off', title: 'Offer 推广计划', path: '/offers', icon: '🎯' },
  { id: 'm-smart', title: 'SmartLink 分流', path: '/smartlinks', icon: '⚡' },
  { id: 'm-aff', title: '渠道客管理', path: '/affiliates', icon: '🤝' },
  { id: 'm-conv', title: '转化与 S2S 归因', path: '/conversions', icon: '🔄' },
  { id: 'm-fraud', title: '4D反作弊风控', path: '/affiliate/anti-fraud', icon: '🛡️' },
  { id: 'm-fin', title: '财务出账结算', path: '/finance', icon: '💰' },
  { id: 'm-payout', title: '批量打款与结汇', path: '/billing/payouts', icon: '💳' },
  { id: 'm-rep', title: 'Sub-ID 多维报表', path: '/analytics', icon: '📈' },
  { id: 'm-cohort', title: 'Cohort留存与LTV', path: '/analytics/cohort', icon: '🧬' },
  { id: 'm-macro', title: '宏参数与映射', path: '/affiliate/macros', icon: '🧩' }
]

const defaultSystemMenus = [
  { id: 'm-user', title: '用户管理', path: '/system/users', icon: '👥' },
  { id: 'm-role', title: '角色管理', path: '/system/roles', icon: '🛡️' },
  { id: 'm-menu', title: '菜单管理', path: '/system/menus', icon: '📑' },
  { id: 'm-perm', title: '权限字典', path: '/system/permissions', icon: '🔑' },
  { id: 'm-s3', title: 'S3 存储配置', path: '/system/s3', icon: '🗄️' },
  { id: 'm-dom', title: '域名池管理', path: '/system/domains', icon: '🌐' }
]

const businessMenus = ref<any[]>(defaultBusinessMenus)
const systemMenus = ref<any[]>(defaultSystemMenus)

// 租户列表从 GET /api/v1/tenants 加载；加载失败时仅保留当前租户 ID
const tenantList = ref<Array<{ id: string; name: string }>>([{ id: currentTenant.value, name: currentTenant.value }])
const apiOnline = ref(false)

const allMenus = computed(() => [...businessMenus.value, ...systemMenus.value])

// 切换租户后整页刷新，确保各页面接口以新的 X-Tenant-ID 重新拉取数据
watch(currentTenant, () => {
  window.location.reload()
})

const currentPageTitle = computed(() => {
  const item = allMenus.value.find(i => i.path === route.path)
  return item ? item.title : '系统总览'
})

const loadDynamicMenus = async () => {
  try {
    const tree = await fetchApi<any[]>('/api/v1/system/menus/user-tree?role=SUPER_ADMIN')
    if (tree && tree.length > 0) {
      businessMenus.value = tree.filter(m => !m.path.startsWith('/system/'))
      systemMenus.value = tree.filter(m => m.path.startsWith('/system/'))
    }
  } catch (err: any) {
    showToast(`加载动态菜单失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

const loadTenants = async () => {
  try {
    const list = await fetchApi<any[]>('/api/v1/tenants')
    apiOnline.value = true
    if (list && list.length > 0) {
      tenantList.value = list.map(t => ({ id: t.id, name: `${t.name} (${t.id})` }))
      if (!list.some(t => t.id === currentTenant.value)) {
        currentTenant.value = list[0].id
      }
    }
  } catch (err: any) {
    apiOnline.value = false
    showToast(`加载租户列表失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

onMounted(() => {
  loadTenants()
  loadDynamicMenus()
})
</script>
