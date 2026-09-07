<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">权限清单字典 (Permissions Catalog)</h3>
        <p class="text-xs text-slate-500">全平台 13 个业务子域的标准功能操作权限与 API 资源鉴权标识</p>
      </div>
      <div class="relative w-64">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索权限标识或名称..."
          class="w-full rounded-lg border border-slate-200 pl-8 pr-3 py-1.5 text-xs text-slate-900 focus:border-brand-500"
        />
        <span class="absolute left-2.5 top-2 text-slate-400 text-xs">🔍</span>
      </div>
    </div>

    <!-- 模块分组网格 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
      <div
        v-for="(perms, moduleName) in filteredModules"
        :key="moduleName"
        class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-3"
      >
        <div class="flex items-center justify-between border-b border-slate-100 pb-2">
          <h4 class="font-bold text-slate-900 text-sm flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-brand-500"></span>
            <span>{{ moduleName }}</span>
          </h4>
          <span class="text-xs text-slate-400 font-mono">{{ perms.length }} 项</span>
        </div>

        <div class="space-y-2">
          <div
            v-for="p in perms"
            :key="p.code"
            class="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-100 hover:bg-slate-100/60 transition-colors"
          >
            <div>
              <p class="text-xs font-bold text-slate-800">{{ p.name }}</p>
              <p class="text-[11px] text-slate-400 font-mono">{{ p.code }}</p>
            </div>
            <span
              class="px-2 py-0.5 rounded text-[10px] font-semibold uppercase font-mono"
              :class="p.type === 'BUTTON' ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-indigo-50 text-indigo-700 border border-indigo-200'"
            >
              {{ p.type }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useApi } from '~/composables/useApi'

const { fetchApi } = useApi()
const searchQuery = ref('')
const modules = ref<Record<string, any[]>>({})

const filteredModules = computed(() => {
  if (!searchQuery.value.trim()) return modules.value
  const q = searchQuery.value.toLowerCase().trim()
  const result: Record<string, any[]> = {}

  for (const [mod, perms] of Object.entries(modules.value)) {
    const matched = perms.filter(p => p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q))
    if (matched.length > 0) {
      result[mod] = matched
    }
  }
  return result
})

const loadPermissions = async () => {
  try {
    const res = await fetchApi<Record<string, any[]>>('/api/v1/system/permissions')
    modules.value = res
  } catch (err) {
    modules.value = {
      '用户管理': [
        { code: 'system:user:read', name: '用户查看', type: 'API' },
        { code: 'system:user:write', name: '用户维护', type: 'BUTTON' }
      ],
      '角色管理': [
        { code: 'system:role:read', name: '角色查看', type: 'API' },
        { code: 'system:role:write', name: '角色授权', type: 'BUTTON' }
      ],
      'Offer计划': [
        { code: 'offer:read', name: '计划查询', type: 'API' },
        { code: 'offer:write', name: '计划维护', type: 'BUTTON' }
      ],
      '渠道客': [
        { code: 'affiliate:read', name: '渠道查看', type: 'API' },
        { code: 'affiliate:write', name: '渠道维护', type: 'BUTTON' }
      ],
      '财务结算': [
        { code: 'finance:settle', name: '账期结算', type: 'BUTTON' }
      ]
    }
  }
}

onMounted(() => {
  loadPermissions()
})
</script>
