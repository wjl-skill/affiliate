<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">转化流水与 S2S 归因审核</h3>
        <p class="text-xs text-slate-500">监控广告主服务端回传转化、CTIT 转化耗时差风控质检及人工审批流水线</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3.5 py-2 text-xs font-semibold rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 shadow-sm transition-colors flex items-center gap-1.5"
          @click="showSimModal = true"
        >
          <span>🧪</span>
          <span>S2S 回传联调模拟器</span>
        </button>
        <button
          type="button"
          class="px-3 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="loadConversions"
        >
          <span>🔄 刷新</span>
        </button>
      </div>
    </div>

    <!-- 转化流水表格 -->
    <CommonTable
      :columns="columns"
      :data="conversions"
      :loading="loading"
      search-placeholder="搜索订单流水号 txId、转化 ID 或渠道..."
    >
      <template #cell-conversionInfo="{ row }">
        <div class="flex flex-col">
          <span class="font-bold text-slate-900 font-mono">{{ row.id }}</span>
          <span class="text-[11px] text-slate-400 font-mono">订单号 (txId): {{ row.txId }}</span>
        </div>
      </template>

      <template #cell-offerAffiliate="{ row }">
        <div class="text-xs">
          <span class="font-semibold text-slate-800">{{ row.offerId }}</span>
          <span class="text-slate-400 block text-[11px]">渠道客: {{ row.affiliateId }}</span>
        </div>
      </template>

      <!-- CTIT 耗时质检指示器 -->
      <template #cell-ctit="{ row }">
        <div class="text-xs">
          <span
            v-if="row.ctitSeconds < 3"
            class="inline-flex items-center gap-1 font-bold text-rose-700 bg-rose-50 px-2 py-0.5 rounded border border-rose-200"
          >
            ⚠️ {{ row.ctitSeconds }}s (点击注入作弊预警)
          </span>
          <span
            v-else
            class="inline-flex items-center gap-1 text-slate-700 font-mono bg-slate-50 px-2 py-0.5 rounded"
          >
            ⏱️ {{ formatCtit(row.ctitSeconds) }}
          </span>
        </div>
      </template>

      <template #cell-pricing="{ row }">
        <div class="text-xs">
          <span class="text-emerald-600 font-bold">${{ row.payout }}</span>
          <span class="text-slate-400 mx-1">/</span>
          <span class="text-slate-700">${{ row.revenue }}</span>
        </div>
      </template>

      <template #cell-source="{ row }">
        <div class="flex flex-col gap-1 text-xs">
          <span class="font-mono text-slate-700">{{ row.sub1 || '-' }}</span>
          <span
            class="inline-flex w-fit items-center px-2 py-0.5 rounded text-[11px] font-semibold border"
            :class="row.postbackStatus === 'DELIVERED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : row.postbackStatus === 'FAILED' ? 'bg-rose-50 text-rose-700 border-rose-200' : 'bg-slate-50 text-slate-500 border-slate-200'"
          >
            {{ row.postbackStatus === 'DELIVERED' ? '已回传' : row.postbackStatus === 'FAILED' ? '回传失败' : '待回传' }}
          </span>
        </div>
      </template>

      <template #cell-status="{ row }">
        <div class="flex flex-col gap-0.5">
          <StatusTag :status="row.status" />
          <span v-if="row.rejectionReason" class="text-[10px] text-rose-500 font-mono">{{ row.rejectionReason }}</span>
        </div>
      </template>

      <!-- 审核操作按钮 -->
      <template #cell-actions="{ row }">
        <div v-if="row.status === 'PENDING' || row.status === 'FRAUD_SUSPECTED'" class="flex items-center gap-1.5">
          <button
            type="button"
            class="px-2 py-1 text-xs font-semibold rounded bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors"
            @click="approveConversion(row.id)"
          >
            ✓ 通过
          </button>
          <button
            type="button"
            class="px-2 py-1 text-xs font-semibold rounded bg-rose-50 text-rose-700 hover:bg-rose-100 transition-colors"
            @click="openRejectModal(row.id)"
          >
            ✕ 驳回
          </button>
        </div>
        <span v-else class="text-[11px] text-slate-400 italic">已归档</span>
      </template>
    </CommonTable>

    <!-- 弹窗 1: S2S 回传联调模拟器 -->
    <ModalDialog
      :show="showSimModal"
      title="S2S Postback 服务端回传联调模拟器"
      confirm-text="触发 Postback 回传"
      :loading="simulating"
      @close="showSimModal = false"
      @confirm="submitPostbackSim"
    >
      <div class="space-y-4 text-xs">
        <div class="p-3 bg-amber-50 text-amber-800 rounded-lg border border-amber-200">
          💡 本模拟器直接调用 <code class="font-mono bg-amber-100 px-1 py-0.5 rounded">/affiliate/postback</code> 接口，测试 CTIT 耗时与防重复 txId 拦截。
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">会话点击 ID (click_id)</label>
          <input
            v-model="simForm.clickId"
            type="text"
            required
            placeholder="c_abc123..."
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">广告主订单流水号 (txid - 查重键)</label>
          <input
            v-model="simForm.txId"
            type="text"
            required
            placeholder="tx_ord_999"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">销售金额 (Sale Amount $)</label>
          <input
            v-model="simForm.saleAmount"
            type="number"
            step="0.01"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
      </div>
    </ModalDialog>

    <!-- 弹窗 2: 驳回原因填写 -->
    <ModalDialog
      :show="showRejectModal"
      title="驳回转化单"
      confirm-text="确认驳回"
      @close="showRejectModal = false"
      @confirm="confirmReject"
    >
      <div class="space-y-3 text-xs">
        <p class="text-slate-600">请选择或输入驳回该转化单的原因：</p>
        <select v-model="rejectReason" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
          <option value="CTIT_FAST_CONVERSION">CTIT_FAST_CONVERSION (转化耗时过短，疑似自动化脚本作弊)</option>
          <option value="ADVERTISER_CANCELLED">ADVERTISER_CANCELLED (广告主端客户退款或取消订单)</option>
          <option value="GEO_DISCREPANCY">GEO_DISCREPANCY (地域不匹配，非目标国家真实访客)</option>
          <option value="DUPLICATE_ORDER">DUPLICATE_ORDER (重复上报订单)</option>
        </select>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const simulating = ref(false)
const showSimModal = ref(false)
const showRejectModal = ref(false)
const rejectingId = ref('')
const rejectReason = ref('CTIT_FAST_CONVERSION')

const conversions = ref<any[]>([])

const simForm = ref({
  clickId: '',
  txId: '',
  saleAmount: ''
})

const columns = [
  { key: 'conversionInfo', label: '转化 ID / 订单号 (txId)' },
  { key: 'offerAffiliate', label: 'Offer / 渠道客' },
  { key: 'ctit', label: 'CTIT 转化耗时' },
  { key: 'pricing', label: '佣金 Payout / 营收' },
  { key: 'source', label: '来源 Sub1 / 回传状态' },
  { key: 'status', label: '审核状态' },
  { key: 'actions', label: '操作' }
]

const formatCtit = (seconds: number) => {
  if (seconds < 60) return `${seconds} 秒`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}分 ${s}秒`
}

const loadConversions = async () => {
  loading.value = true
  try {
    conversions.value = await fetchApi<any[]>('/api/v1/affiliate/conversions') || []
  } catch (err: any) {
    conversions.value = []
    showToast(`加载转化流水失败：${err.message || err}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const approveConversion = async (id: string) => {
  try {
    const updated = await fetchApi<any>(`/api/v1/affiliate/conversions/${id}/approve`, { method: 'POST' })
    const item = conversions.value.find(c => c.id === id)
    if (item && updated) Object.assign(item, updated)
    else if (item) item.status = 'APPROVED'
    showToast(`转化 ${id} 已审核通过`, 'success')
  } catch (err: any) {
    showToast(`审核通过失败：${err.message || err}`, 'error', 5000)
  }
}

const openRejectModal = (id: string) => {
  rejectingId.value = id
  showRejectModal.value = true
}

const confirmReject = async () => {
  try {
    const updated = await fetchApi<any>(`/api/v1/affiliate/conversions/${rejectingId.value}/reject`, {
      method: 'POST',
      body: { reason: rejectReason.value }
    })
    const item = conversions.value.find(c => c.id === rejectingId.value)
    if (item && updated) Object.assign(item, updated)
    else if (item) item.status = 'REJECTED'
    showRejectModal.value = false
    showToast(`转化单已驳回`, 'warning')
  } catch (err: any) {
    showToast(`驳回操作失败：${err.message || err}`, 'error', 5000)
  }
}

const submitPostbackSim = async () => {
  if (!simForm.value.clickId.trim() || !simForm.value.txId.trim()) {
    showToast('请填写真实的 click_id 与 txid', 'warning')
    return
  }
  simulating.value = true
  try {
    await fetchApi(`/affiliate/postback?click_id=${encodeURIComponent(simForm.value.clickId.trim())}&txid=${encodeURIComponent(simForm.value.txId.trim())}&sale_amount=${encodeURIComponent(simForm.value.saleAmount || '0')}`, {
      method: 'POST'
    })
    showToast('Postback 回传触发成功', 'success')
    showSimModal.value = false
    loadConversions()
  } catch (err: any) {
    showToast(`模拟回传失败：${err.message || err}`, 'error', 5000)
  } finally {
    simulating.value = false
  }
}

onMounted(() => {
  loadConversions()
})
</script>
