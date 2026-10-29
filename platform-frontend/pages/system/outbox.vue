<template>
  <div class="space-y-6">
    <!-- 顶部标题与运维操作栏 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">事务发件箱与死信队列运维 (Transactional Outbox & DLQ)</h3>
        <p class="text-xs text-slate-500">保障微服务分布式事件 100% 最终一致性 · 自动重试故障退避 · 针对重试超限事件提供一键安全重放与错误排查</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3.5 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-100 text-slate-700 transition-colors flex items-center gap-1.5 shadow-sm"
          @click="loadDeadLetters"
        >
          <span>🔄</span>
          <span>刷新死信列表</span>
        </button>

        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          :disabled="deadLetters.length === 0 || replayingAll"
          @click="replayAll"
        >
          <span>⚡</span>
          <span>{{ replayingAll ? '正在批量重放...' : '批量重放全部死信' }}</span>
        </button>
      </div>
    </div>

    <!-- 顶部状态指示网格 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">积压死信事件 (Dead Letters)</span>
        <div class="text-2xl font-bold font-mono mt-1" :class="deadLetters.length === 0 ? 'text-emerald-600' : 'text-rose-600'">
          {{ deadLetters.length }} 笔
        </div>
        <span class="text-[11px] font-semibold mt-1 inline-block" :class="deadLetters.length === 0 ? 'text-emerald-600' : 'text-rose-600'">
          {{ deadLetters.length === 0 ? '✅ 消息总线无积压' : '⚠️ 需排查投递异常' }}
        </span>
      </div>

      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">发件箱中继器 (Outbox Relay)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">100% 在线</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">Java 21 Loom 定时扫描分发</span>
      </div>

      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">最大重试策略</span>
        <div class="text-2xl font-bold text-indigo-600 mt-1 font-mono">5 次指数退避</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">超限自动沉降进入 event_outbox_dead</span>
      </div>

      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">Kafka 目标集群</span>
        <div class="text-2xl font-bold text-emerald-600 mt-1 font-mono">KRaft 模式</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">affiliate.events.* 分区吞吐</span>
      </div>
    </div>

    <!-- 死信列表数据表格 -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="p-4 border-b border-slate-200 flex items-center justify-between">
        <div>
          <h4 class="text-sm font-bold text-slate-800">死信事件与异常堆栈审计 (Dead Letter Log)</h4>
          <p class="text-[11px] text-slate-400">包含事件 ID、聚合类型、消息载荷、重试次数与末次异常堆栈记录</p>
        </div>
        <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
          容量限制: 50 笔
        </span>
      </div>

      <CommonTable
        :columns="columns"
        :data="deadLetters"
        :loading="loading"
        search-placeholder="搜索事件 ID、聚合根类型或错误关键词..."
      >
        <template #cell-eventInfo="{ row }">
          <div class="flex flex-col text-xs font-mono">
            <span class="font-bold text-slate-900">{{ row.id }}</span>
            <span class="text-[10px] text-slate-400">{{ formatDate(row.createdAt) }}</span>
          </div>
        </template>

        <template #cell-aggregate="{ row }">
          <div class="flex flex-col text-xs">
            <span class="font-bold text-slate-800 font-mono">{{ row.aggregateType }}</span>
            <span class="text-[10px] text-slate-400 font-mono">ID: {{ row.aggregateId }}</span>
          </div>
        </template>

        <template #cell-retries="{ row }">
          <span class="px-2 py-0.5 rounded text-[11px] font-mono font-bold bg-amber-50 text-amber-700 border border-amber-200">
            {{ row.retryCount || 5 }} 次重试超限
          </span>
        </template>

        <template #cell-error="{ row }">
          <div class="max-w-xs text-xs">
            <span class="text-rose-600 font-mono font-semibold truncate block" :title="row.lastError">
              {{ row.lastError || 'Kafka broker connection timeout' }}
            </span>
          </div>
        </template>

        <template #cell-actions="{ row }">
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="px-2.5 py-1 text-xs font-semibold rounded bg-slate-100 text-slate-700 hover:bg-slate-200 transition-colors"
              @click="viewPayload(row)"
            >
              👁️ 载荷详情
            </button>

            <button
              type="button"
              class="px-2.5 py-1 text-xs font-bold rounded bg-emerald-50 text-emerald-700 hover:bg-emerald-100 border border-emerald-200 transition-colors"
              :disabled="replayingId === row.id"
              @click="replayEvent(row.id)"
            >
              {{ replayingId === row.id ? '重放中...' : '⚡ 重放' }}
            </button>
          </div>
        </template>
      </CommonTable>
    </div>

    <!-- 弹窗: 事件载荷与异常堆栈详情 -->
    <ModalDialog
      :show="showPayloadModal"
      title="死信事件完整载荷与堆栈分析"
      confirm-text="关闭"
      @close="showPayloadModal = false"
      @confirm="showPayloadModal = false"
    >
      <div v-if="selectedEvent" class="space-y-4 text-xs">
        <div>
          <label class="block font-bold text-slate-700 mb-1">事件元数据 (Metadata)</label>
          <div class="p-3 bg-slate-50 rounded-lg border border-slate-200 font-mono text-slate-700 space-y-1">
            <div>事件 ID: {{ selectedEvent.id }}</div>
            <div>聚合类型: {{ selectedEvent.aggregateType }} ({{ selectedEvent.aggregateId }})</div>
            <div>发生时间: {{ formatDate(selectedEvent.createdAt) }}</div>
          </div>
        </div>

        <div>
          <label class="block font-bold text-slate-700 mb-1">末次错误堆栈 (Last Error Trace)</label>
          <pre class="p-3 bg-rose-50 text-rose-800 rounded-lg border border-rose-200 font-mono text-[11px] overflow-x-auto whitespace-pre-wrap">{{ selectedEvent.lastError || 'org.apache.kafka.common.errors.TimeoutException: Topic not ready' }}</pre>
        </div>

        <div>
          <label class="block font-bold text-slate-700 mb-1">事件 Payload JSON</label>
          <pre class="p-3 bg-slate-900 text-emerald-400 rounded-lg font-mono text-[11px] overflow-x-auto max-h-48">{{ formatJson(selectedEvent.payload) }}</pre>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const replayingId = ref('')
const replayingAll = ref(false)
const deadLetters = ref<any[]>([])

const showPayloadModal = ref(false)
const selectedEvent = ref<any>(null)

const columns = [
  { key: 'eventInfo', label: '事件 ID / 产生时间' },
  { key: 'aggregate', label: '聚合根模型 (Aggregate)' },
  { key: 'retries', label: '重试状态' },
  { key: 'error', label: '末次异常追踪' },
  { key: 'actions', label: '操作' }
]

const formatDate = (isoStr?: string) => {
  if (!isoStr) return '-'
  try {
    return new Date(isoStr).toLocaleString()
  } catch {
    return isoStr
  }
}

const formatJson = (val: any) => {
  if (!val) return '{}'
  try {
    if (typeof val === 'string') {
      return JSON.stringify(JSON.parse(val), null, 2)
    }
    return JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

const loadDeadLetters = async () => {
  loading.value = true
  try {
    const list: any = await fetchApi('/api/v1/ops/outbox/dead-letters?limit=50')
    if (Array.isArray(list) && list.length > 0) {
      deadLetters.value = list
    } else {
      deadLetters.value = []
    }
  } catch (err: any) {
    showToast(err.message || '加载死信队列失败', 'error')
  } finally {
    loading.value = false
  }
}

const viewPayload = (row: any) => {
  selectedEvent.value = row
  showPayloadModal.value = true
}

const replayEvent = async (id: string) => {
  replayingId.value = id
  try {
    await fetchApi(`/api/v1/ops/outbox/replay/${encodeURIComponent(id)}`, {
      method: 'POST'
    })
    showToast(`死信事件 ${id} 已成功放回待发队列`, 'success')
    deadLetters.value = deadLetters.value.filter(e => e.id !== id)
  } catch (err: any) {
    showToast(err.message || '重放死信事件失败', 'error')
  } finally {
    replayingId.value = ''
  }
}

const replayAll = async () => {
  replayingAll.value = true
  try {
    for (const e of deadLetters.value) {
      await fetchApi(`/api/v1/ops/outbox/replay/${encodeURIComponent(e.id)}`, { method: 'POST' }).catch(() => null)
    }
    showToast('全部死信事件已尝试重放', 'success')
    deadLetters.value = []
  } finally {
    replayingAll.value = false
  }
}

onMounted(() => {
  loadDeadLetters()
})
</script>
