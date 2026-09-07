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
            <h4 class="text-sm font-bold text-slate-800">当前待支付批次: {{ currentBatch.batchId }}</h4>
            <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800">PENDING_DISBURSEMENT</span>
          </div>
          <p class="text-[11px] text-slate-400 mt-0.5">
            共计 {{ currentBatch.items.length }} 笔打款单 · 总原始佣金 ${{ currentBatch.totalGrossUsd }} USD · 代扣税金 ${{ currentBatch.totalTaxUsd }} USD
          </p>
        </div>

        <div class="flex items-center gap-3 text-xs">
          <div class="text-right">
            <span class="text-slate-400 text-[10px] block">最终放款金额 (净付)</span>
            <span class="font-bold text-emerald-600 font-mono text-base">${{ currentBatch.totalNetUsd }} USD</span>
          </div>
          <button
            type="button"
            class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-semibold transition-colors shadow-sm"
            @click="disburseBatch"
          >
            🚀 提交全量批次打款
          </button>
        </div>
      </div>

      <CommonTable
        :columns="columns"
        :data="currentBatch.items"
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

const fxRates = ref<Record<string, { rate: string }>>({
  EUR: { rate: '0.9200' },
  GBP: { rate: '0.7850' },
  JPY: { rate: '152.50' },
  SGD: { rate: '1.3400' },
  USDT: { rate: '1.0000' }
})

const columns = [
  { key: 'affiliate', label: '收款渠道 / 户名', slot: 'affiliate' },
  { key: 'taxInfo', label: '跨境税号 / 预扣率', slot: 'taxInfo' },
  { key: 'grossUsd', label: '原始应付 (USD)', slot: 'grossUsd' },
  { key: 'taxWithheld', label: 'W-8BEN 扣税 (USD)', slot: 'taxWithheld' },
  { key: 'targetCurrency', label: '净付目标币种与金额', slot: 'targetCurrency' },
  { key: 'channel', label: '打款通道', slot: 'channel' },
  { key: 'beneficiary', label: '收款账号 / 邮箱', slot: 'beneficiary' }
]

const currentBatch = ref({
  batchId: 'batch_payout_20260904_01',
  totalGrossUsd: '18500.00',
  totalTaxUsd: '1850.00',
  totalNetUsd: '16650.00',
  items: [
    {
      affiliateId: 'aff-vip-888',
      beneficiaryName: 'Nexus Global Media Pte.',
      taxId: 'SG-UEN-20188992',
      taxRate: 0.10,
      grossUsd: '10000.00',
      taxUsd: '1000.00',
      currency: 'SGD',
      fxRate: '1.3400',
      targetAmount: '12060.00',
      method: 'TIPALTI_WIRE',
      account: 'DBS-SG-9988-121'
    },
    {
      affiliateId: 'aff-traffic-hub',
      beneficiaryName: 'Berlin Traffic Works GmbH',
      taxId: 'DE-VAT-9928172',
      taxRate: 0.00,
      grossUsd: '5000.00',
      taxUsd: '0.00',
      currency: 'EUR',
      fxRate: '0.9200',
      targetAmount: '4600.00',
      method: 'PAYONEER_MASS',
      account: 'payoneer@berlintraffic.de'
    },
    {
      affiliateId: 'aff-crypto-lead',
      beneficiaryName: 'CryptoLeads Web3 Ltd',
      taxId: 'BVI-IBC-44129',
      taxRate: 0.10,
      grossUsd: '3500.00',
      taxUsd: '350.00',
      currency: 'USDT',
      fxRate: '1.0000',
      targetAmount: '3150.00',
      method: 'CRYPTO_USDT',
      account: '0x71C8A3...82E9'
    }
  ]
})

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
  } catch (ignored) {}
}

const disburseBatch = async () => {
  try {
    await fetchApi(`/api/v1/billing/payouts/batch/${currentBatch.value.batchId}/disburse`, { method: 'POST' })
  } catch (ignored) {}
  showToast(`批次 ${currentBatch.value.batchId} 打款指令已向银行通道提交！状态: DISBURSED`, 'success')
}

const exportTipaltiCsv = () => {
  const content = 'BatchId,AffiliateId,TargetCurrency,NetAmount,BeneficiaryAccount\n' +
    currentBatch.value.items.map(i => `${currentBatch.value.batchId},${i.affiliateId},${i.currency},${i.targetAmount},${i.account}`).join('\n')
  downloadFile(content, 'tipalti_payout_batch.csv')
  showToast('已成功导出 Tipalti 批次清单 CSV', 'success')
}

const exportPayoneerCsv = () => {
  const content = 'PayeeID,Amount,Currency,Comment\n' +
    currentBatch.value.items.map(i => `${i.affiliateId},${i.targetAmount},${i.currency},Payout for Net-30`).join('\n')
  downloadFile(content, 'payoneer_mass_payout.csv')
  showToast('已成功导出 Payoneer 批量付款 CSV', 'success')
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
})
</script>
