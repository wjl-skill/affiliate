<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">财务出账与账期结算 (Settlement & Invoices)</h3>
        <p class="text-xs text-slate-500">依据 Net-7 / Net-15 / Net-30 账期将已审核通过的转化归集生成正式付款账单</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showGenerateModal = true"
      >
        <span>🧾</span>
        <span>周期出账核算</span>
      </button>
    </div>

    <!-- 发票账单表格 -->
    <CommonTable
      :columns="columns"
      :data="invoices"
      :loading="loading"
      search-placeholder="搜索发票 ID 或渠道客..."
    >
      <template #cell-invoiceInfo="{ row }">
        <div class="flex flex-col">
          <span class="font-bold text-slate-900 font-mono">{{ row.id }}</span>
          <span class="text-[11px] text-slate-400">账期: {{ row.billingCycle || '-' }} · 出账: {{ formatDate(row.createdAt) }}</span>
        </div>
      </template>

      <template #cell-affiliate="{ row }">
        <span class="font-semibold text-slate-800 font-mono">{{ row.affiliateId }}</span>
      </template>

      <template #cell-amount="{ row }">
        <span class="text-sm font-bold text-emerald-600 font-mono">${{ row.amount }}</span>
      </template>

      <template #cell-count="{ row }">
        <span class="text-xs font-medium text-slate-700">{{ row.conversionCount }} 单</span>
      </template>

      <template #cell-term="{ row }">
        <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-slate-100 text-slate-700 border border-slate-200">
          {{ row.paymentTerm }}
        </span>
      </template>

      <template #cell-status="{ row }">
        <div class="flex flex-col gap-0.5">
          <StatusTag :status="row.status" />
          <span v-if="row.paidAt" class="text-[11px] text-slate-400">打款: {{ formatDate(row.paidAt) }}</span>
        </div>
      </template>

      <!-- 操作按钮 -->
      <template #cell-actions="{ row }">
        <div v-if="row.status === 'GENERATED'">
          <button
            type="button"
            class="px-2.5 py-1 text-xs font-semibold rounded bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors"
            @click="markAsPaid(row)"
          >
            💳 确认打款
          </button>
        </div>
        <span v-else class="text-[11px] text-slate-400 italic">已核销</span>
      </template>
    </CommonTable>

    <!-- 周期出账弹窗 -->
    <ModalDialog
      :show="showGenerateModal"
      title="渠道周期出账核算"
      confirm-text="生成出账发票"
      @close="showGenerateModal = false"
      @confirm="generateInvoice"
    >
      <div class="space-y-4 text-xs">
        <div class="p-3 bg-slate-50 text-slate-700 rounded-lg border border-slate-200">
          💡 系统将自动聚合该渠道客当前所有处于 <code class="font-bold text-emerald-700">APPROVED</code> 状态的待结佣金，并校验是否达到约定起提门槛（如 $100）。
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">选择待出账渠道客 ID</label>
          <input
            v-model="targetAffiliateId"
            type="text"
            required
            placeholder="请输入渠道客 ID (如合作方列表中的 ID)"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
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
const showGenerateModal = ref(false)
const targetAffiliateId = ref('')
const invoices = ref<any[]>([])

const columns = [
  { key: 'invoiceInfo', label: '结算发票 ID' },
  { key: 'affiliate', label: '结算渠道客' },
  { key: 'amount', label: '应付打款金额' },
  { key: 'count', label: '核销单量' },
  { key: 'term', label: '账期规则' },
  { key: 'status', label: '打款状态' },
  { key: 'actions', label: '操作' }
]

const formatDate = (isoStr: string) => {
  if (!isoStr) return '刚刚'
  return new Date(isoStr).toLocaleDateString()
}

const loadInvoices = async () => {
  loading.value = true
  try {
    invoices.value = await fetchApi<any[]>('/api/v1/affiliate/invoices') || []
  } catch (err: any) {
    invoices.value = []
    showToast(`加载结算发票失败：${err.message || err}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const generateInvoice = async () => {
  const affiliateId = targetAffiliateId.value.trim()
  if (!affiliateId) {
    showToast('请输入待出账渠道客 ID', 'warning')
    return
  }
  try {
    const res = await fetchApi<any>(`/api/v1/affiliate/invoices/generate?affiliateId=${encodeURIComponent(affiliateId)}`, {
      method: 'POST'
    })
    if (res) {
      await loadInvoices()
      showToast('结算发票生成成功！', 'success')
      showGenerateModal.value = false
    } else {
      showToast('未生成发票：该渠道客暂无达到起提门槛的已审核通过转化', 'warning', 5000)
    }
  } catch (err: any) {
    showToast(`出账核算失败：${err.message || err}`, 'error', 5000)
  }
}

const markAsPaid = async (row: any) => {
  try {
    await fetchApi(`/api/v1/affiliate/invoices/${row.id}/mark-paid`, { method: 'POST' })
    showToast(`发票 ${row.id} 已确认打款核销`, 'success')
    await loadInvoices()
  } catch (err: any) {
    showToast(`确认打款失败：${err.message || err}`, 'error', 5000)
  }
}

onMounted(() => {
  loadInvoices()
})
</script>
