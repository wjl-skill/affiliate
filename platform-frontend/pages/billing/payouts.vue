<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">出海跨币种批量打款中心 (Mass Payouts & Global FX Center)</h3>
        <p class="text-xs text-slate-500">支持 Tipalti / Payoneer / PayPal 批量打款文件生成、W-8BEN 跨境预扣税自动计算及多币种实时点差锁汇</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3.5 py-1.5 text-xs font-semibold rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 shadow-sm transition-colors flex items-center gap-1.5"
          @click="exportTipaltiCsv"
        >
          <span>📥 导出 Tipalti CSV</span>
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="exportPayoneerCsv"
        >
          <span>📥 导出 Payoneer CSV</span>
        </button>
      </div>
    </div>

    <!-- 实时汇率与点差看板 -->
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
      <div
        v-for="(fx, curr) in fxRates"
        :key="curr"
        class="bg-white rounded-xl p-3 border border-slate-200 shadow-sm text-center"
      >
        <span class="text-[10px] font-bold text-slate-400 font-mono">USD / {{ curr }}</span>
        <div class="text-lg font-bold text-slate-900 font-mono mt-0.5">{{ fx.rate }}</div>
        <span class="text-[10px] text-indigo-600 font-medium block">点差: 1.5%</span>
      </div>
    </div>

    <!-- 批次核算与打款执行明细 -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="p-4 border-b border-slate-200 flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="flex items-center gap-2">
            <h4 class="text-sm font-bold text-slate-800">
              当前批次:
              <select
                v-if="batchList.length"
                class="ml-1 text-xs font-mono border border-slate-300 rounded-md px-2 py-1 bg-white text-slate-800"
                v-model="selectedBatchId"
                @change="loadBatchDetail"
              >
                <option v-for="b in batchList" :key="b.id" :value="b.id">{{ b.batchNumber || b.id }}</option>
              </select>
              <span v-else class="text-slate-400 font-normal text-xs">暂无批次</span>
            </h4>
            <span
              v-if="currentBatch"
              class="px-2 py-0.5 rounded text-[10px] font-bold"
              :class="currentBatch.status === 'DISBURSED' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'"
            >{{ currentBatch.status }}</span>
          </div>
          <p class="text-[11px] text-slate-400 mt-0.5">
            共计 {{ items.length }} 笔打款单 · 总原始佣金 ${{ currentBatch?.totalGrossUsd ?? '0.00' }} USD · 代扣税金 ${{ currentBatch?.totalTaxUsd ?? '0.00' }} USD
          </p>
        </div>

        <div class="flex items-center gap-3 text-xs">
          <div class="text-right">
            <span class="text-slate-400 text-[10px] block">最终放款金额 (净付)</span>
            <span class="font-bold text-emerald-600 font-mono text-base">${{ currentBatch?.totalNetUsd ?? '0.00' }} USD</span>
          </div>
          <button
            type="button"
            :disabled="!currentBatch || currentBatch.status !== 'DRAFT' || disbursing"
            class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-300 disabled:cursor-not-allowed text-white font-semibold transition-colors shadow-sm"
            @click="disburseBatch"
          >
            🚀 提交全量批次打款
          </button>
        </div>
      </div>

      <CommonTable
        :columns="columns"
        :data="items"
        :loading="loading"
        search-placeholder="搜索渠道 ID 或税号..."
      >
        <template #cell-affiliate="{ row }">
          <div class="flex flex-col text-xs font-mono">
            <span class="font-bold text-slate-900">{{ row.affiliateId }}</span>
            <span class="text-[10px] text-slate-400">{{ row.beneficiaryName }}</span>
          </div>
        </template>

        <template #cell-taxInfo="{ row }">
          <div class="text-xs">
            <span class="font-mono text-slate-700">{{ row.taxId }}</span>
            <span class="ml-1.5 px-1.5 py-0.5 rounded text-[10px] font-semibold" :class="row.taxRate > 0 ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-600'">
              W-8BEN: {{ row.taxRate * 100 }}%
            </span>
          </div>
        </template>

        <template #cell-grossUsd="{ row }">
          <span class="text-xs font-mono font-bold text-slate-800">${{ row.grossUsd }}</span>
        </template>

        <template #cell-taxWithheld="{ row }">
          <span class="text-xs font-mono text-rose-600 font-semibold">-${{ row.taxUsd }}</span>
        </template>

        <template #cell-targetCurrency="{ row }">
          <div class="text-xs font-mono">
            <span class="font-bold text-emerald-600">{{ row.targetAmount }} {{ row.currency }}</span>
            <span class="text-[10px] text-slate-400 block">汇率: {{ row.fxRate }}</span>
          </div>
        </template>

        <template #cell-channel="{ row }">
          <span class="px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-800 border border-slate-200">
            {{ row.method }}
          </span>
        </template>

        <template #cell-beneficiary="{ row }">
          <span class="text-xs font-mono text-slate-600">{{ row.account }}</span>
        </template>
      </CommonTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()
const loading = ref(false)
const disbursing = ref(false)

const fxRates = ref<Record<string, { rate: string }>>({})
const batchList = ref<any[]>([])
const selectedBatchId = ref('')
const currentBatch = ref<any>(null)
const items = ref<any[]>([])

const columns = [
  { key: 'affiliate', label: '收款渠道 / 户名', slot: 'affiliate' },
  { key: 'taxInfo', label: '跨境税号 / 预扣率', slot: 'taxInfo' },
  { key: 'grossUsd', label: '原始应付 (USD)', slot: 'grossUsd' },
  { key: 'taxWithheld', label: 'W-8BEN 扣税 (USD)', slot: 'taxWithheld' },
  { key: 'targetCurrency', label: '净付目标币种与金额', slot: 'targetCurrency' },
  { key: 'channel', label: '打款通道', slot: 'channel' },
  { key: 'beneficiary', label: '收款账号 / 邮箱', slot: 'beneficiary' }
]

const loadRates = async () => {
  try {
    const data: any = await fetchApi('/api/v1/billing/fx/rates')
    if (data) {
      for (const cur of ['EUR', 'GBP', 'JPY', 'SGD', 'USDT']) {
        if (data[cur] !== undefined) {
          fxRates.value[cur] = { rate: String(data[cur]) }
        }
      }
    }
  } catch (err: any) {
    showToast(`加载实时汇率失败：${err.message || err}`, 'error', 5000)
  }
}

const loadBatches = async () => {
  loading.value = true
  try {
    batchList.value = await fetchApi<any[]>('/api/v1/billing/payouts/batches') || []
    if (batchList.value.length) {
      selectedBatchId.value = batchList.value[0].id
      await loadBatchDetail()
    }
  } catch (err: any) {
    batchList.value = []
    showToast(`加载放款批次列表失败：${err.message || err}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const loadBatchDetail = async () => {
  if (!selectedBatchId.value) return
  loading.value = true
  try {
    currentBatch.value = await fetchApi<any>(`/api/v1/billing/payouts/batch/${selectedBatchId.value}`)
    items.value = await fetchApi<any[]>(`/api/v1/billing/payouts/batch/${selectedBatchId.value}/items`) || []
  } catch (err: any) {
    currentBatch.value = null
    items.value = []
    showToast(`加载批次明细失败：${err.message || err}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const disburseBatch = async () => {
  if (!currentBatch.value) return
  disbursing.value = true
  try {
    const res: any = await fetchApi(`/api/v1/billing/payouts/batch/${currentBatch.value.id}/disburse`, { method: 'POST' })
    if (res?.success) {
      showToast(`批次 ${currentBatch.value.id} 打款指令已提交，状态: DISBURSED`, 'success')
      await loadBatches()
    } else {
      showToast(`批次 ${currentBatch.value.id} 打款下发失败，请检查批次状态`, 'error', 5000)
    }
  } catch (err: any) {
    showToast(`打款下发失败：${err.message || err}`, 'error', 5000)
  } finally {
    disbursing.value = false
  }
}

const buildManifest = () => ({
  batchId: currentBatch.value.id,
  totalPayees: items.value.length,
  totalGrossUsd: currentBatch.value.totalGrossUsd,
  totalTaxUsd: currentBatch.value.totalTaxUsd,
  totalNetUsd: currentBatch.value.totalNetUsd,
  generatedAt: currentBatch.value.createdAt,
  items: items.value.map(i => ({
    affiliateId: i.affiliateId,
    accountName: i.beneficiaryName,
    paymentAccount: i.account,
    payoutMethod: i.method,
    grossUsd: i.grossUsd,
    taxWithheldUsd: i.taxUsd,
    netUsd: Number(i.grossUsd || 0) - Number(i.taxUsd || 0),
    targetCurrency: i.currency,
    fxRate: i.fxRate,
    targetCurrencyAmount: i.targetAmount
  }))
})

const exportTipaltiCsv = async () => {
  if (!currentBatch.value) {
    showToast('请先选择有效批次', 'warning')
    return
  }
  try {
    const csv = await fetchApi<string>('/api/v1/billing/payouts/batch/export/tipalti', {
      method: 'POST',
      body: buildManifest(),
      responseType: 'text'
    } as any)
    downloadFile(csv, 'tipalti_payout_batch.csv')
    showToast('已成功导出 Tipalti 批次清单 CSV', 'success')
  } catch (err: any) {
    showToast(`导出 Tipalti CSV 失败：${err.message || err}`, 'error', 5000)
  }
}

const exportPayoneerCsv = async () => {
  if (!currentBatch.value) {
    showToast('请先选择有效批次', 'warning')
    return
  }
  try {
    const csv = await fetchApi<string>('/api/v1/billing/payouts/batch/export/payoneer', {
      method: 'POST',
      body: buildManifest(),
      responseType: 'text'
    } as any)
    downloadFile(csv, 'payoneer_mass_payout.csv')
    showToast('已成功导出 Payoneer 批量付款 CSV', 'success')
  } catch (err: any) {
    showToast(`导出 Payoneer CSV 失败：${err.message || err}`, 'error', 5000)
  }
}

const downloadFile = (content: string, filename: string) => {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  loadRates()
  loadBatches()
})
</script>
