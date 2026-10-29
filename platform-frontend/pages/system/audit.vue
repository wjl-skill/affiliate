<template>
  <div class="space-y-6">
    <!-- 顶部标题与操作 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900 tracking-tight">安全与操作审计流水 (Security Audit Log)</h3>
        <p class="text-xs text-slate-500 mt-0.5">全量留存用户特权变更、角色提权、菜单路由维护、系统对账及敏感操作的可追溯不可篡改审计证据链</p>
      </div>
      <div class="flex items-center gap-3">
        <!-- 快速过滤选择 -->
        <select
          v-model="selectedAction"
          class="text-xs bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-slate-700 font-medium focus:outline-none focus:ring-2 focus:ring-brand-500 cursor-pointer"
        >
          <option value="ALL">全部动作类型</option>
          <option value="SAVE_USER">用户创建/更新 (SAVE_USER)</option>
          <option value="DELETE_USER">用户删除 (DELETE_USER)</option>
          <option value="SAVE_ROLE">角色授权调整 (SAVE_ROLE)</option>
          <option value="DELETE_ROLE">角色删除 (DELETE_ROLE)</option>
          <option value="SAVE_MENU">动态菜单维护 (SAVE_MENU)</option>
          <option value="SYSTEM_BOOT_VERIFY">系统安全核验 (SYSTEM_BOOT)</option>
        </select>

        <select
          v-model="limitCount"
          class="text-xs bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-slate-700 font-medium focus:outline-none focus:ring-2 focus:ring-brand-500 cursor-pointer"
          @change="loadAuditEvents"
        >
          <option :value="50">近 50 条</option>
          <option :value="100">近 100 条</option>
          <option :value="200">近 200 条</option>
        </select>

        <button
          type="button"
          class="px-3 py-1.5 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="loadAuditEvents"
        >
          <span>🔄</span>
          <span>刷新审计流</span>
        </button>
      </div>
    </div>

    <!-- 审计事件统计 -->
    <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">📜</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">捕获审计事件</p>
          <p class="text-lg font-bold text-slate-900 mt-0.5">{{ events.length }} <span class="text-xs text-slate-400 font-normal">条</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">👤</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">特权操作人员数</p>
          <p class="text-lg font-bold text-slate-900 mt-0.5">{{ uniqueActorsCount }} <span class="text-xs text-slate-400 font-normal">人</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">🛡️</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">安全鉴权模式</p>
          <p class="text-sm font-bold text-emerald-600 mt-1">RBAC + HMAC 双重签名</p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">🔒</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">存储持久化合规</p>
          <p class="text-sm font-bold text-indigo-600 mt-1">PostgreSQL + 环形内存备份</p>
        </div>
      </div>
    </div>

    <!-- 审计流水表格 -->
    <CommonTable
      :columns="columns"
      :data="filteredEvents"
      :loading="loading"
      search-placeholder="搜索操作人 ID、资源类型或资源 ID..."
    >
      <template #cell-timestamp="{ row }">
        <span class="text-xs font-mono text-slate-600">
          {{ formatDate(row.createdAt) }}
        </span>
      </template>

      <template #cell-actor="{ row }">
        <div class="flex items-center gap-2">
          <div class="w-6 h-6 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center text-[11px] font-bold text-slate-700">
            {{ (row.actorId || 'SYS').slice(0, 2).toUpperCase() }}
          </div>
          <div>
            <span class="font-bold text-slate-900 text-xs">{{ row.actorId || 'system' }}</span>
            <span class="text-[10px] text-slate-400 font-mono block">Tenant: {{ row.tenantId }}</span>
          </div>
        </div>
      </template>

      <template #cell-action="{ row }">
        <span
          class="px-2 py-0.5 rounded text-[11px] font-bold font-mono uppercase"
          :class="getActionBadgeClass(row.action)"
        >
          {{ row.action }}
        </span>
      </template>

      <template #cell-resource="{ row }">
        <div>
          <span class="text-xs font-semibold text-slate-800">{{ row.resourceType }}</span>
          <span class="text-[11px] text-slate-400 font-mono block mt-0.5 truncate max-w-xs">ID: {{ row.resourceId }}</span>
        </div>
      </template>

      <template #cell-payload="{ row }">
        <div class="text-[11px] font-mono text-slate-600 max-w-sm truncate bg-slate-50 px-2 py-1 rounded border border-slate-100">
          {{ row.payload || '{}' }}
        </div>
      </template>

      <template #cell-actions="{ row }">
        <button
          type="button"
          class="px-2.5 py-1 text-xs font-semibold rounded-lg bg-slate-50 hover:bg-brand-50 text-brand-600 border border-slate-200 hover:border-brand-200 transition-colors"
          @click="openDetails(row)"
        >
          查看存证
        </button>
      </template>
    </CommonTable>

    <!-- 存证详情模态框 -->
    <ModalDialog
      :show="showDetailModal"
      title="审计存证不可篡改详情"
      description="包含操作发生时的完整上下文字段、受影响资源快照与安全调用栈"
      confirm-text="已知悉"
      @confirm="showDetailModal = false"
      @cancel="showDetailModal = false"
    >
      <div v-if="selectedEvent" class="space-y-4 text-xs">
        <div class="grid grid-cols-2 gap-3 bg-slate-50 p-3 rounded-lg border border-slate-100 font-mono text-[11px]">
          <div>
            <span class="text-slate-400 block">事件流水号 (Event ID):</span>
            <span class="font-bold text-slate-900 break-all">{{ selectedEvent.id }}</span>
          </div>
          <div>
            <span class="text-slate-400 block">发生时刻 (Timestamp):</span>
            <span class="font-bold text-slate-900">{{ formatDate(selectedEvent.createdAt) }}</span>
          </div>
          <div>
            <span class="text-slate-400 block">执行者 (Actor ID):</span>
            <span class="font-bold text-slate-900">{{ selectedEvent.actorId }} ({{ selectedEvent.tenantId }})</span>
          </div>
          <div>
            <span class="text-slate-400 block">执行动作 (Action):</span>
            <span class="font-bold text-brand-600">{{ selectedEvent.action }}</span>
          </div>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">变更载荷快照 (Structured Payload Diff)</label>
          <pre class="bg-slate-900 text-emerald-400 p-4 rounded-xl font-mono text-xs overflow-x-auto max-h-60 leading-relaxed border border-slate-800">{{ formatPayload(selectedEvent.payload) }}</pre>
        </div>

        <div class="p-3 bg-amber-50 rounded-lg border border-amber-200 text-amber-800 text-[11px] leading-relaxed">
          <strong>合规与存证提示：</strong>所有审计事件通过租户统一安全中间件截获并记录，已绑定租户上下文，满足 SOC2 及 GDPR 对特权操作追溯合规要求。
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { currentTenant, fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const events = ref<any[]>([])
const selectedAction = ref('ALL')
const limitCount = ref(50)

const showDetailModal = ref(false)
const selectedEvent = ref<any>(null)

const columns = [
  { key: 'timestamp', label: '操作时间戳' },
  { key: 'actor', label: '执行人 / 租户' },
  { key: 'action', label: '审计动作' },
  { key: 'resource', label: '操作对象与目标 ID' },
  { key: 'payload', label: '变更载荷快照' },
  { key: 'actions', label: '操作' }
]

const filteredEvents = computed(() => {
  if (selectedAction.value === 'ALL') {
    return events.value
  }
  return events.value.filter(e => e.action === selectedAction.value)
})

const uniqueActorsCount = computed(() => {
  const set = new Set(events.value.map(e => e.actorId || 'system'))
  return set.size
})

const formatDate = (isoString?: string) => {
  if (!isoString) return '--'
  try {
    const d = new Date(isoString)
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return isoString
  }
}

const formatPayload = (raw?: string) => {
  if (!raw) return '{}'
  try {
    const parsed = JSON.parse(raw)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return raw
  }
}

const getActionBadgeClass = (action: string) => {
  if (action?.includes('DELETE')) return 'bg-rose-50 text-rose-700 border border-rose-200'
  if (action?.includes('SAVE') || action?.includes('CREATE')) return 'bg-emerald-50 text-emerald-700 border border-emerald-200'
  if (action?.includes('REPLAY') || action?.includes('RECONCILE')) return 'bg-amber-50 text-amber-700 border border-amber-200'
  return 'bg-blue-50 text-blue-700 border border-blue-200'
}

const openDetails = (row: any) => {
  selectedEvent.value = row
  showDetailModal.value = true
}

const loadAuditEvents = async () => {
  loading.value = true
  try {
    const tenantParam = encodeURIComponent(currentTenant.value || 'tenant-1')
    const res = await fetchApi<any[]>(`/api/v1/system/audit-events?tenantId=${tenantParam}&limit=${limitCount.value}`)
    events.value = Array.isArray(res) ? res : []
  } catch (err: any) {
    showToast(`拉取审计流水失败: ${err.message || '网络异常'}`, 'error')
    events.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAuditEvents()
})
</script>
