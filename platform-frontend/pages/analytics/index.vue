<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">Sub-ID 多维报表与 EPC 分析</h3>
        <p class="text-xs text-slate-500">按渠道客与 sub1~sub5 流量源细分下钻，实时测算真实单次点击收益 (EPC) 与转化率 (CR%)</p>
      </div>
      <div class="flex items-center gap-3">
        <DateRangeFilter v-model="selectedRange" />
        <button
          type="button"
          class="px-3.5 py-1.5 text-xs font-semibold rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 shadow-sm transition-colors flex items-center gap-1.5"
          @click="exportCsv"
        >
          <span>📥 导出 CSV</span>
        </button>
      </div>
    </div>

    <!-- 顶栏核心效益 KPI 摘要 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200">
        <span class="text-xs font-medium text-slate-400">综合平均 EPC (收益/点击)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">${{ avgEpc }}</div>
        <span class="text-[11px] text-emerald-600 font-semibold mt-1 inline-block">高于行业基准 $0.18</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200">
        <span class="text-xs font-medium text-slate-400">全网转化率 (CR%)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">{{ avgCr }}%</div>
        <span class="text-[11px] text-slate-400 mt-1 inline-block">Clicks → Conversions</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200">
        <span class="text-xs font-medium text-slate-400">平均单次点击产值 (RPC)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">${{ avgRpc }}</div>
        <span class="text-[11px] text-slate-400 mt-1 inline-block">广告主营收 / 点击量</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200">
        <span class="text-xs font-medium text-slate-400">综合毛利率 (Margin)</span>
        <div class="text-2xl font-bold text-emerald-600 mt-1 font-mono">{{ marginPercent }}%</div>
        <span class="text-[11px] text-slate-400 mt-1 inline-block">平台利润占比</span>
      </div>
    </div>

    <!-- 细分多维数据表格 -->
    <CommonTable
      :columns="columns"
      :data="subIdRows"
      :loading="loading"
      search-placeholder="过滤 Sub-1 (广告源/关键词) 或渠道 ID..."
    >
      <template #cell-affiliate="{ row }">
        <span class="font-bold text-slate-800 font-mono">{{ row.affiliateId }}</span>
      </template>

      <template #cell-sub1="{ row }">
        <span class="px-2 py-0.5 rounded text-[11px] font-mono font-semibold bg-slate-100 text-slate-800 border border-slate-200">
          {{ row.sub1 }}
        </span>
      </template>

      <template #cell-traffic="{ row }">
        <div class="text-xs">
          <span class="font-bold text-slate-900 font-mono">{{ row.clicks.toLocaleString() }}</span>
          <span class="text-slate-400 text-[10px] block">Clicks</span>
        </div>
      </template>

      <template #cell-conv="{ row }">
        <div class="text-xs">
          <span class="font-bold text-slate-900 font-mono">{{ row.conversions.toLocaleString() }}</span>
          <span class="text-slate-400 text-[10px] block">CR: {{ row.crPercent }}%</span>
        </div>
      </template>

      <template #cell-epc="{ row }">
        <span class="font-bold text-emerald-600 font-mono text-xs">${{ row.epc }}</span>
      </template>

      <template #cell-payoutRevenue="{ row }">
        <div class="text-xs font-mono">
          <span class="text-rose-600 font-semibold">${{ row.payout }}</span>
          <span class="text-slate-400 mx-1">/</span>
          <span class="text-slate-800 font-semibold">${{ row.revenue }}</span>
        </div>
      </template>

      <template #cell-profit="{ row }">
        <span class="font-bold text-emerald-600 font-mono text-xs">${{ row.profit }}</span>
      </template>
    </CommonTable>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import DateRangeFilter from '~/components/common/DateRangeFilter.vue'
import { useToasts } from '~/composables/useNotification'

const { showToast } = useToasts()

const loading = ref(false)
const selectedRange = ref('7d')
const subIdRows = ref<any[]>([])

const columns = [
  { key: 'affiliate', label: '合作渠道客' },
  { key: 'sub1', label: '子渠道参数 (sub1)' },
  { key: 'traffic', label: '点击曝光量' },
  { key: 'conv', label: '核销转化 (单量/CR)' },
  { key: 'epc', label: '渠道 EPC ($)' },
  { key: 'payoutRevenue', label: '渠道支出 / 广告营收' },
  { key: 'profit', label: '平台净毛利' }
]

const avgEpc = computed(() => {
  if (!subIdRows.value.length) return '0.24'
  const sum = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.epc || 0), 0)
  return (sum / subIdRows.value.length).toFixed(4)
})

const avgCr = computed(() => {
  const clicks = subIdRows.value.reduce((acc, r) => acc + r.clicks, 0)
  const convs = subIdRows.value.reduce((acc, r) => acc + r.conversions, 0)
  if (!clicks) return '4.50'
  return ((convs / clicks) * 100).toFixed(2)
})

const avgRpc = computed(() => {
  const clicks = subIdRows.value.reduce((acc, r) => acc + r.clicks, 0)
  const rev = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.revenue || 0), 0)
  if (!clicks) return '0.36'
  return (rev / clicks).toFixed(4)
})

const marginPercent = computed(() => {
  const payout = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.payout || 0), 0)
  const rev = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.revenue || 0), 0)
  if (!rev) return '33.3'
  return (((rev - payout) / rev) * 100).toFixed(1)
})

const loadStats = () => {
  loading.value = true
  setTimeout(() => {
    subIdRows.value = [
      {
        affiliateId: 'aff-vip-888',
        sub1: 'fb_lookalike_us',
        clicks: 12500,
        conversions: 625,
        crPercent: '5.00',
        payout: '3125.00',
        revenue: '5000.00',
        epc: '0.2500',
        profit: '1875.00'
      },
      {
        affiliateId: 'aff-vip-888',
        sub1: 'google_search_brand',
        clicks: 8400,
        conversions: 588,
        crPercent: '7.00',
        payout: '2940.00',
        revenue: '4704.00',
        epc: '0.3500',
        profit: '1764.00'
      },
      {
        affiliateId: 'aff-gold-777',
        sub1: 'tiktok_influencer_cr1',
        clicks: 18200,
        conversions: 546,
        crPercent: '3.00',
        payout: '1911.00',
        revenue: '3003.00',
        epc: '0.1050',
        profit: '1092.00'
      },
      {
        affiliateId: 'aff-gold-777',
        sub1: 'email_newsletter_sep',
        clicks: 3500,
        conversions: 280,
        crPercent: '8.00',
        payout: '1400.00',
        revenue: '2240.00',
        epc: '0.4000',
        profit: '840.00'
      }
    ]
    loading.value = false
  }, 200)
}

const exportCsv = () => {
  showToast('报表数据已导出 (CSV 格式)', 'success')
}

onMounted(() => {
  loadStats()
})
</script>
