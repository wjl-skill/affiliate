<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">Sub-ID 多维报表、双引擎 OLAP 切片与 AI 商业洞察</h3>
        <p class="text-xs text-slate-500">按渠道客与 sub1~sub5 流量源细分下钻，支持双引擎 OLAP 列式聚合加速及高管 AI 经营简报</p>
      </div>
      <div class="flex items-center gap-3">
        <DateRangeFilter v-model="selectedRange" />
        <button
          type="button"
          :disabled="loadingInsight"
          class="px-3.5 py-1.5 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="loadAiInsights"
        >
          <span>🤖 {{ loadingInsight ? '生成洞察中...' : 'AI 经营洞察简报' }}</span>
        </button>
        <button
          type="button"
          class="px-3.5 py-1.5 text-xs font-semibold rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 shadow-sm transition-colors flex items-center gap-1.5"
          @click="exportCsv"
        >
          <span>📥 导出 CSV</span>
        </button>
      </div>
    </div>

    <!-- AI 商业经营洞察面板 -->
    <div v-if="executiveInsight" class="bg-slate-900 text-slate-100 rounded-xl p-5 border border-slate-800 shadow-lg space-y-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-400/30">AI EXECUTIVE BRIEFING</span>
          <span class="text-xs font-mono text-slate-400">统计周期: {{ executiveInsight.dateRange }} · ROAS: {{ Number(executiveInsight.overallRoas || 0).toFixed(2) }}x</span>
        </div>
        <button type="button" class="text-xs text-slate-400 hover:text-white" @click="executiveInsight = null">收起 ✕</button>
      </div>
      <div class="text-xs whitespace-pre-wrap leading-relaxed text-slate-200 font-mono bg-slate-800/70 p-3.5 rounded-lg border border-slate-700">
        {{ executiveInsight.aiExecutiveBriefing }}
      </div>
    </div>

    <!-- 顶栏核心效益 KPI 摘要 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200">
        <span class="text-xs font-medium text-slate-400">综合平均 EPC (收益/点击)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">${{ avgEpc }}</div>
        <span class="text-[11px] text-slate-400 mt-1 inline-block">全渠道加权平均</span>
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

    <!-- OLAP 多维切片聚合控制栏与结果 -->
    <div class="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h4 class="text-sm font-bold text-slate-800">⚡ 双引擎 OLAP 多维动态聚簇切片 (OlapReportingEngine)</h4>
          <p class="text-[11px] text-slate-400">支持按 Offer、渠道客、Sub1~Sub3、日期维度实时向量化聚合</p>
        </div>
        <div class="flex items-center gap-2 text-xs">
          <span class="text-slate-500 font-medium">聚合维度:</span>
          <select
            v-model="olapGroupBy"
            class="border border-slate-300 rounded-lg px-2.5 py-1.5 bg-white font-mono text-slate-800"
            @change="loadOlapAggregation"
          >
            <option value="offerId">Offer ID (推广计划)</option>
            <option value="affiliateId">Affiliate ID (渠道客)</option>
            <option value="sub1">Sub-1 (流量子源)</option>
            <option value="date">Date (按日聚合)</option>
          </select>
        </div>
      </div>
      <div v-if="olapRows.length > 0" class="grid grid-cols-1 md:grid-cols-3 gap-3">
        <div v-for="item in olapRows" :key="item.dimensionKey" class="p-3 rounded-lg bg-slate-50 border border-slate-200 flex items-center justify-between text-xs">
          <div>
            <div class="font-mono font-bold text-indigo-700">{{ item.dimensionKey || 'DEFAULT' }}</div>
            <div class="text-[11px] text-slate-500 mt-0.5">Clicks: {{ item.clicks }} · Conv: {{ item.conversions }}</div>
          </div>
          <div class="text-right font-mono">
            <div class="font-bold text-slate-900">Rev: ${{ item.revenue }}</div>
            <div class="text-[11px] text-emerald-600">Profit: ${{ item.profit }}</div>
          </div>
        </div>
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
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const loadingInsight = ref(false)
const selectedRange = ref('7d')
const subIdRows = ref<any[]>([])
const olapGroupBy = ref('offerId')
const olapRows = ref<any[]>([])
const executiveInsight = ref<any>(null)

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
  if (!subIdRows.value.length) return '0.00'
  const sum = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.epc || 0), 0)
  return (sum / subIdRows.value.length).toFixed(4)
})

const avgCr = computed(() => {
  const clicks = subIdRows.value.reduce((acc, r) => acc + r.clicks, 0)
  const convs = subIdRows.value.reduce((acc, r) => acc + r.conversions, 0)
  if (!clicks) return '0.00'
  return ((convs / clicks) * 100).toFixed(2)
})

const avgRpc = computed(() => {
  const clicks = subIdRows.value.reduce((acc, r) => acc + r.clicks, 0)
  const rev = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.revenue || 0), 0)
  if (!clicks) return '0.00'
  return (rev / clicks).toFixed(4)
})

const marginPercent = computed(() => {
  const payout = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.payout || 0), 0)
  const rev = subIdRows.value.reduce((acc, r) => acc + parseFloat(r.revenue || 0), 0)
  if (!rev) return '0.0'
  return (((rev - payout) / rev) * 100).toFixed(1)
})

const getIsoDateRange = () => {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - 14)
  return {
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10)
  }
}

const loadStats = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/affiliate/analytics/subid/list')
    subIdRows.value = (res || []).map(r => ({
      affiliateId: r.affiliateId,
      sub1: r.sub1,
      clicks: Number(r.clicks || 0),
      conversions: Number(r.conversions || 0),
      crPercent: Number(r.crPercent || 0).toFixed(2),
      payout: r.totalPayout,
      revenue: r.totalRevenue,
      epc: r.epc,
      profit: r.margin
    }))
  } catch (err: any) {
    subIdRows.value = []
    showToast(`加载 Sub-ID 报表失败：${err.message || err}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const loadOlapAggregation = async () => {
  try {
    const { from, to } = getIsoDateRange()
    olapRows.value = await fetchApi<any[]>(`/api/v1/reports/olap?from=${from}&to=${to}&groupBy=${encodeURIComponent(olapGroupBy.value)}`) || []
  } catch (e) {
    olapRows.value = []
  }
}

const loadAiInsights = async () => {
  loadingInsight.value = true
  try {
    const { from, to } = getIsoDateRange()
    executiveInsight.value = await fetchApi<any>(`/api/v1/reports/ai-insights?from=${from}&to=${to}`)
    showToast('AI 商业经营洞察简报已生成', 'success')
  } catch (err: any) {
    showToast(`生成 AI 洞察失败：${err.message || err}`, 'error')
  } finally {
    loadingInsight.value = false
  }
}

const exportCsv = () => {
  if (!subIdRows.value.length) {
    showToast('当前无可导出的报表数据', 'warning')
    return
  }
  const header = 'AffiliateID,Sub1,Clicks,Conversions,CR%,EPC,Payout,Revenue,Profit'
  const lines = subIdRows.value.map(r =>
    [r.affiliateId, r.sub1, r.clicks, r.conversions, r.crPercent, r.epc, r.payout, r.revenue, r.profit].join(',')
  )
  const blob = new Blob(['\ufeff' + [header, ...lines].join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `subid_report_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  showToast('报表数据已导出 (CSV 格式)', 'success')
}

onMounted(() => {
  loadStats()
  loadOlapAggregation()
})
</script>
