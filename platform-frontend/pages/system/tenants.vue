<template>
  <div class="space-y-6">
    <!-- 顶部标题与新建租户按钮 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900 tracking-tight">多租户组织管理 (Multi-Tenancy)</h3>
        <p class="text-xs text-slate-500 mt-0.5">全平台顶层数据隔离单元与预算空间，支持独立企业入驻、配额限额与运行时启停调度</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3 py-1.5 text-xs font-semibold rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors flex items-center gap-1.5"
          @click="loadTenants"
        >
          <span>🔄</span>
          <span>刷新列表</span>
        </button>
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showCreateModal = true"
        >
          <span>➕</span>
          <span>创建新租户</span>
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-5">
      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-brand-50 text-brand-600 flex items-center justify-center text-xl font-bold">
          🏢
        </div>
        <div>
          <p class="text-xs text-slate-400 font-medium">总注册租户组织</p>
          <p class="text-xl font-bold text-slate-900 mt-0.5">{{ tenants.length }} <span class="text-xs text-slate-400 font-normal">个</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center text-xl font-bold">
          ✅
        </div>
        <div>
          <p class="text-xs text-slate-400 font-medium">正常激活投放中</p>
          <p class="text-xl font-bold text-slate-900 mt-0.5">{{ activeTenantCount }} <span class="text-xs text-slate-400 font-normal">个</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center text-xl font-bold">
          🎯
        </div>
        <div>
          <p class="text-xs text-slate-400 font-medium">当前管理工作台租户</p>
          <p class="text-base font-bold text-brand-600 font-mono mt-0.5 truncate max-w-[180px]">{{ currentTenant }}</p>
        </div>
      </div>
    </div>

    <!-- 租户管理列表 -->
    <CommonTable
      :columns="columns"
      :data="tenants"
      :loading="loading"
      search-placeholder="搜索租户 ID 或企业名称..."
    >
      <template #cell-tenantInfo="{ row }">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-slate-100 border border-slate-200 flex items-center justify-center text-xs font-bold text-slate-700">
            {{ row.name ? row.name.slice(0, 1).toUpperCase() : 'T' }}
          </div>
          <div>
            <div class="flex items-center gap-2">
              <span class="font-bold text-slate-900">{{ row.name }}</span>
              <span
                v-if="row.id === currentTenant"
                class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-brand-50 text-brand-700 border border-brand-200"
              >
                当前会话
              </span>
            </div>
            <span class="text-xs text-slate-400 font-mono block mt-0.5">ID: {{ row.id }}</span>
          </div>
        </div>
      </template>

      <template #cell-status="{ row }">
        <StatusTag :status="row.status || (row.active ? 'ACTIVE' : 'SUSPENDED')">
          {{ row.status === 'ACTIVE' || row.active ? '正常运行' : '冻结挂起' }}
        </StatusTag>
      </template>

      <template #cell-createdAt="{ row }">
        <span class="text-xs font-mono text-slate-600">
          {{ formatDate(row.createdAt) }}
        </span>
      </template>

      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <!-- 切换进入该租户 -->
          <button
            type="button"
            class="px-2.5 py-1 text-xs font-semibold rounded-lg border transition-colors"
            :class="row.id === currentTenant ? 'bg-slate-50 text-slate-400 border-slate-200 cursor-not-allowed' : 'bg-white hover:bg-brand-50 text-brand-600 border-brand-200 hover:border-brand-400'"
            :disabled="row.id === currentTenant"
            @click="switchTenant(row.id)"
          >
            {{ row.id === currentTenant ? '当前空间' : '进入该空间' }}
          </button>

          <!-- 激活/冻结切换 -->
          <button
            type="button"
            class="px-2.5 py-1 text-xs font-semibold rounded-lg border transition-colors"
            :class="isTenantActive(row) ? 'hover:bg-rose-50 text-rose-600 border-rose-200' : 'hover:bg-emerald-50 text-emerald-600 border-emerald-200'"
            :disabled="togglingId === row.id"
            @click="toggleTenantStatus(row)"
          >
            {{ togglingId === row.id ? '处理中...' : (isTenantActive(row) ? '冻结租户' : '激活恢复') }}
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 创建租户模态框 -->
    <ModalDialog
      :show="showCreateModal"
      title="创建新租户组织"
      description="为新入驻的企业广告主、代理商或独立网盟团队分配全新的隔离数据空间"
      confirm-text="确认创建"
      :confirm-disabled="creating || !createForm.id || !createForm.name"
      @confirm="submitCreateTenant"
      @cancel="showCreateModal = false"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">
            租户唯一标识 (Tenant ID) <span class="text-rose-500">*</span>
          </label>
          <input
            v-model="createForm.id"
            type="text"
            required
            placeholder="例如: tenant-us-alpha"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none"
          />
          <p class="text-[11px] text-slate-400 mt-1">全局唯一英文字符串，用于请求头 X-Tenant-ID 与数据库顶层分片隔离</p>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">
            企业 / 组织全称 <span class="text-rose-500">*</span>
          </label>
          <input
            v-model="createForm.name"
            type="text"
            required
            placeholder="例如: Alpha Digital Media Ltd."
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 focus:ring-2 focus:ring-brand-500 focus:outline-none"
          />
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">初始状态</label>
          <select
            v-model="createForm.status"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 bg-white focus:ring-2 focus:ring-brand-500 focus:outline-none"
          >
            <option value="ACTIVE">ACTIVE - 正常激活可用</option>
            <option value="SUSPENDED">SUSPENDED - 冻结（需人工审核后开放）</option>
          </select>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { currentTenant, fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const creating = ref(false)
const togglingId = ref<string | null>(null)
const showCreateModal = ref(false)

const tenants = ref<any[]>([])

const createForm = ref({
  id: '',
  name: '',
  status: 'ACTIVE'
})

const columns = [
  { key: 'tenantInfo', label: '租户信息 / 组织名称' },
  { key: 'status', label: '运行状态' },
  { key: 'createdAt', label: '创建入驻时间' },
  { key: 'actions', label: '管理操作' }
]

const activeTenantCount = computed(() => {
  return tenants.value.filter(t => isTenantActive(t)).length
})

const isTenantActive = (t: any) => {
  if (t.status) return t.status === 'ACTIVE'
  return Boolean(t.active)
}

const formatDate = (isoString?: string) => {
  if (!isoString) return '--'
  try {
    const d = new Date(isoString)
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return isoString
  }
}

const loadTenants = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/tenants')
    if (res && Array.isArray(res) && res.length > 0) {
      tenants.value = res
    } else {
      // 离线兜底
      tenants.value = [
        { id: 'tenant-1', name: 'Global Media Group', status: 'ACTIVE', createdAt: new Date(Date.now() - 86400000 * 30).toISOString() },
        { id: 'tenant-apac', name: 'APAC Affiliate Network', status: 'ACTIVE', createdAt: new Date(Date.now() - 86400000 * 15).toISOString() },
        { id: 'tenant-na', name: 'North America Performance', status: 'ACTIVE', createdAt: new Date(Date.now() - 86400000 * 5).toISOString() },
        { id: 'tenant-sandbox', name: 'Test QA Sandbox', status: 'SUSPENDED', createdAt: new Date(Date.now() - 86400000 * 2).toISOString() }
      ]
    }
  } catch (err: any) {
    showToast(`获取租户列表失败: ${err.message || '网络异常'}`, 'warning')
    tenants.value = [
      { id: 'tenant-1', name: 'Global Media Group', status: 'ACTIVE', createdAt: new Date().toISOString() },
      { id: 'tenant-sandbox', name: 'Test Sandbox', status: 'SUSPENDED', createdAt: new Date().toISOString() }
    ]
  } finally {
    loading.value = false
  }
}

const switchTenant = (id: string) => {
  currentTenant.value = id
  showToast(`已切换至租户空间: ${id}，正在同步工作台上下文`, 'success')
  setTimeout(() => {
    window.location.reload()
  }, 400)
}

const toggleTenantStatus = async (row: any) => {
  togglingId.value = row.id
  const nextActive = !isTenantActive(row)
  try {
    const updated = await fetchApi<any>(`/api/v1/tenants/${row.id}/active`, {
      method: 'POST',
      body: { active: nextActive }
    })
    row.status = updated?.status || (nextActive ? 'ACTIVE' : 'SUSPENDED')
    row.active = nextActive
    showToast(`租户 [${row.name}] 状态已变更为: ${nextActive ? '正常激活' : '已冻结'}`, 'success')
  } catch (err: any) {
    showToast(`租户状态变更失败: ${err.message || '网络或接口异常'}`, 'error')
  } finally {
    togglingId.value = null
  }
}

const submitCreateTenant = async () => {
  creating.value = true
  try {
    const payload = {
      id: createForm.value.id.trim(),
      name: createForm.value.name.trim(),
      status: createForm.value.status,
      createdAt: new Date().toISOString()
    }
    const created = await fetchApi<any>('/api/v1/tenants', {
      method: 'POST',
      body: payload
    })
    tenants.value.unshift(created || payload)
    showToast(`新租户 [${payload.name}] 创建成功！`, 'success')
    showCreateModal.value = false
    createForm.value = { id: '', name: '', status: 'ACTIVE' }
  } catch (err: any) {
    showToast(`创建租户失败: ${err.message || '接口调用异常'}`, 'error')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadTenants()
})
</script>
