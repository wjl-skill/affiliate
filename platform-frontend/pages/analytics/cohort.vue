<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">Cohort 留存衰减与生命周期价值 (LTV) 矩阵</h3>
        <p class="text-xs text-slate-500">按获客日期跟踪用户留存率 (D1~D30) 与单客累积创收 LTV 爬升曲线，科学评估获客成本 (CAC) 回本周期</p>
      </div>
      <div class="flex items-center gap-3">
        <DateRangeFilter v-model="selectedRange" />
      </div>
    </div>

    <!-- 核心指标摘要 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">总获客用户数 (Acquired Users)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">14,250 人</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">过去 30 天自然与买量总和</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">平均获客成本 (Blended CAC)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">$4.20</div>
        <span class="text-[11px] text-emerald-600 font-semibold mt-1 inline-block">单客获客支出</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">平均投资回收期 (Avg Payback)</span>
        <div class="text-2xl font-bold text-indigo-600 mt-1 font-mono">D4.8 天</div>
        <span class="text-[11px] text-indigo-600 font-semibold mt-1 inline-block">LTV 覆盖 CAC 均线</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">D30 整体投资回报率 (ROAS)</span>
        <div class="text-2xl font-bold text-emerald-600 mt-1 font-mono">+168.4%</div>
        <span class="text-[11px] text-emerald-600 font-semibold mt-1 inline-block">净利润率 +68.4%</span>
      </div>
    </div>

    <!-- Cohort 热力图分析表格 -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="p-4 border-b border-slate-200 flex items-center justify-between">
        <div>
          <h4 class="text-sm font-bold text-slate-800">留存率与 LTV 滚动衰减热力矩阵 (Cohort Matrix)</h4>
          <p class="text-[11px] text-slate-400">留存率单元格根据比例深浅渲染 · 标绿表示 LTV 达成回本覆盖</p>
        </div>
        <div class="flex items-center gap-2 text-xs">
          <span class="flex items-center gap-1"><span class="w-2.5 h-2.5 rounded bg-emerald-500"></span> 高留存 (&gt;40%)</span>
          <span class="flex items-center gap-1"><span class="w-2.5 h-2.5 rounded bg-blue-500"></span> 中留存 (20-40%)</span>
          <span class="flex items-center gap-1"><span class="w-2.5 h-2.5 rounded bg-slate-200"></span> 衰减 (&lt;20%)</span>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-slate-50 border-b border-slate-200 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
              <th class="py-3 px-4">获客日期</th>
              <th class="py-3 px-3">获客数</th>
              <th class="py-3 px-3">总支出</th>
              <th class="py-3 px-3">CAC</th>
              <th class="py-3 px-3 text-center bg-slate-100/70">D1 留存</th>
              <th class="py-3 px-3 text-center bg-slate-100/70">D3 留存</th>
              <th class="py-3 px-3 text-center bg-slate-100/70">D7 留存</th>
              <th class="py-3 px-3 text-center bg-slate-100/70">D14 留存</th>
              <th class="py-3 px-3 text-center bg-slate-100/70">D30 留存</th>
              <th class="py-3 px-3 text-right">D0 LTV</th>
              <th class="py-3 px-3 text-right">D3 LTV</th>
              <th class="py-3 px-3 text-right">D7 LTV</th>
              <th class="py-3 px-3 text-right">D30 LTV</th>
              <th class="py-3 px-3 text-center">回本天数</th>
              <th class="py-3 px-4 text-right">D30 ROI</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 text-xs font-mono">
            <tr v-for="row in cohortData" :key="row.date" class="hover:bg-slate-50/60 transition-colors">
              <td class="py-3 px-4 font-bold text-slate-800">{{ row.date }}</td>
              <td class="py-3 px-3 text-slate-700">{{ row.users.toLocaleString() }}</td>
              <td class="py-3 px-3 text-slate-700">${{ row.spend }}</td>
              <td class="py-3 px-3 font-semibold text-slate-800">${{ row.cac }}</td>

              <!-- 留存热力单元格 -->
              <td
                v-for="day in [1, 3, 7, 14, 30]"
                :key="day"
                class="py-3 px-3 text-center font-bold"
                :style="getHeatmapStyle(row.retention[day])"
              >
                {{ row.retention[day] ? `${row.retention[day]}%` : '-' }}
              </td>

              <!-- 累计 LTV 单元格 -->
              <td class="py-3 px-3 text-right text-slate-700">${{ row.ltv[0] }}</td>
              <td class="py-3 px-3 text-right text-slate-700">${{ row.ltv[3] }}</td>
              <td class="py-3 px-3 text-right font-semibold" :class="Number(row.ltv[7]) >= Number(row.cac) ? 'text-emerald-600 font-bold' : 'text-slate-700'">
                ${{ row.ltv[7] }}
              </td>
              <td class="py-3 px-3 text-right font-bold text-emerald-600">
                ${{ row.ltv[30] }}
              </td>

              <!-- 回本状态 -->
              <td class="py-3 px-3 text-center">
                <span
                  v-if="row.paybackDay !== null"
                  class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200"
                >
                  D{{ row.paybackDay }} 回本
                </span>
                <span v-else class="text-[10px] text-slate-400">未回本</span>
              </td>

              <!-- D30 ROI -->
              <td class="py-3 px-4 text-right font-bold" :class="row.roiD30 >= 0 ? 'text-emerald-600' : 'text-rose-600'">
                {{ row.roiD30 >= 0 ? `+${row.roiD30}%` : `${row.roiD30}%` }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import DateRangeFilter from '~/components/common/DateRangeFilter.vue'
import { useApi } from '~/composables/useApi'

const { fetchApi } = useApi()
const selectedRange = ref('30d')
const loading = ref(false)

const getHeatmapStyle = (rate: number | undefined) => {
  if (!rate) return {}
  if (rate >= 45) {
    return { backgroundColor: '#dcfce7', color: '#15803d' } // emerald-100 / emerald-700
  } else if (rate >= 30) {
    return { backgroundColor: '#e0e7ff', color: '#4338ca' } // indigo-100 / indigo-700
  } else if (rate >= 20) {
    return { backgroundColor: '#f0fdf4', color: '#166534' }
  } else {
    return { backgroundColor: '#f8fafc', color: '#64748b' } // slate-50 / slate-500
  }
}

const cohortData = ref([
  {
    date: '2026-08-01',
    users: 2400,
    spend: '9600.00',
    cac: '4.00',
    retention: { 1: 52.4, 3: 41.2, 7: 32.8, 14: 24.5, 30: 18.2 },
    ltv: { 0: '2.10', 3: '3.80', 7: '5.40', 30: '11.20' },
    paybackDay: 7,
    roiD30: 180.0
  },
  {
    date: '2026-08-05',
    users: 3100,
    spend: '13020.00',
    cac: '4.20',
    retention: { 1: 55.0, 3: 44.5, 7: 35.1, 14: 26.0, 30: 19.8 },
    ltv: { 0: '2.30', 3: '4.30', 7: '6.10', 30: '12.50' },
    paybackDay: 3,
    roiD30: 197.6
  },
  {
    date: '2026-08-10',
    users: 2850,
    spend: '12825.00',
    cac: '4.50',
    retention: { 1: 48.2, 3: 36.4, 7: 28.0, 14: 20.1, 30: 15.0 },
    ltv: { 0: '1.90', 3: '3.40', 7: '4.80', 30: '9.80' },
    paybackDay: 7,
    roiD30: 117.8
  },
  {
    date: '2026-08-15',
    users: 3500,
    spend: '14000.00',
    cac: '4.00',
    retention: { 1: 53.8, 3: 42.0, 7: 33.5, 14: 25.0, 30: 18.9 },
    ltv: { 0: '2.20', 3: '4.10', 7: '5.90', 30: '11.80' },
    paybackDay: 3,
    roiD30: 195.0
  },
  {
    date: '2026-08-20',
    users: 2400,
    spend: '10320.00',
    cac: '4.30',
    retention: { 1: 51.0, 3: 39.5, 7: 31.0, 14: 22.8, 30: 16.5 },
    ltv: { 0: '2.00', 3: '3.90', 7: '5.20', 30: '10.50' },
    paybackDay: 7,
    roiD30: 144.2
  }
])

const loadCohort = async () => {
  loading.value = true
  try {
    const res: any = await fetchApi('/api/v1/reports/cohort')
    if (res && res.rows && res.rows.length > 0) {
      cohortData.value = res.rows.map((r: any) => ({
        date: r.cohortDate,
        users: r.cohortSize,
        spend: r.acquisitionCost,
        cac: r.cac,
        retention: r.retentionRates || {},
        ltv: r.cumulativeLtv || {},
        paybackDay: r.paybackDay,
        roiD30: r.roiD30 || 0
      }))
    }
  } catch (ignored) {} finally {
    loading.value = false
  }
}

watch(selectedRange, () => {
  loadCohort()
})

onMounted(() => {
  loadCohort()
})
</script>
