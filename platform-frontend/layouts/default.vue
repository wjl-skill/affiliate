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

      <!-- 底部系统健康状态 -->
      <div class="p-4 border-t border-slate-800 bg-slate-950/40 text-xs">
        <div class="flex items-center justify-between text-slate-400">
          <span>RTB 撮合引擎</span>
          <span class="inline-flex items-center gap-1 text-emerald-400 font-semibold">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping"></span>
            0.10ms P99
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
              <option value="tenant-1">Tenant-1 (生产空间)</option>
              <option value="tenant-vip">Tenant-VIP (大户独享)</option>
            </select>
          </div>

          <!-- 用户头像与角色 -->
          <div class="flex items-center gap-2.5 pl-3 border-l border-slate-200">
            <div class="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center font-bold text-slate-600 text-xs">
              AD
            </div>
            <div class="text-left">
              <p class="text-xs font-bold text-slate-800 leading-none">admin</p>
              <span class="text-[10px] text-brand-600 font-semibold uppercase">SUPER_ADMIN</span>
            </div>
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
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '~/composables/useApi'

const route = useRoute()
const { currentTenant, fetchApi } = useApi()

// 默认预置菜单 (提供离线快速渲染与接口降级兜底)
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
  { id: 'm-cohort', title: 'Cohort留存与LTV', path: '/analytics/cohort', icon: '🧬' }
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

const allMenus = computed(() => [...businessMenus.value, ...systemMenus.value])

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
  } catch (err) {
    // 降级使用 default menus
  }
}

onMounted(() => {
  loadDynamicMenus()
})
</script>
