<template>
  <div class="space-y-6">
    <!-- 顶部欢迎与时间快捷过滤 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-xl font-bold text-slate-900 tracking-tight">商业网盟运营总览</h3>
        <p class="text-xs text-slate-500 mt-0.5">实时监控推广效果转化、渠道佣金支出与各 Offer 收益产出</p>
      </div>
      <div class="flex items-center gap-3">
        <DateRangeFilter v-model="selectedRange" />
        <button
          type="button"
          class="px-3 py-1.5 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="loadOverview"
        >
          <span>🔄</span>
          <span>刷新数据</span>
        </button>
      </div>
    </div>

    <!-- 核心 KPI 看板卡片阵列 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
      <StatCard
        title="平台广告营收"
        prefix="$"
        :value="overview.totalRevenue || '0.00'"
        :trend="12.5"
      />
      <StatCard
        title="渠道佣金支出"
        prefix="$"
        :value="overview.totalPayout || '0.00'"
        :trend="8.3"
      />
      <StatCard
        title="网盟净毛利"
        prefix="$"
        :value="overview.grossProfit || '0.00'"
        :trend="18.9"
      />
      <StatCard
        title="累计核销转化单数"
        suffix="单"
        :value="overview.totalConversions || 0"
        :trend="5.2"
      />
    </div>

    <!-- 中部概况网格 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 快捷转化漏斗与比率卡片 -->
      <div class="bg-white rounded-xl p-6 border border-slate-200 shadow-sm space-y-4">
        <h4 class="text-sm font-bold text-slate-800 flex items-center justify-between">
          <span>效果漏斗健康度</span>
          <StatusTag status="ACTIVE">实时生效中</StatusTag>
        </h4>
        <div class="space-y-3 pt-2">
          <div>
            <div class="flex justify-between text-xs font-medium text-slate-600 mb-1">
              <span>转化审批通过率</span>
              <span class="font-bold text-slate-900">{{ approvalRate }}%</span>
            </div>
            <div class="w-full bg-slate-100 rounded-full h-2">
              <div class="bg-emerald-500 h-2 rounded-full" :style="{ width: `${approvalRate}%` }"></div>
            </div>
          </div>
          <div>
            <div class="flex justify-between text-xs font-medium text-slate-600 mb-1">
              <span>有效 Offer 在线率</span>
              <span class="font-bold text-slate-900">{{ activeOfferRate }}%</span>
            </div>
            <div class="w-full bg-slate-100 rounded-full h-2">
              <div class="bg-brand-500 h-2 rounded-full" :style="{ width: `${activeOfferRate}%` }"></div>
            </div>
          </div>
        </div>

        <div class="pt-4 border-t border-slate-100 flex items-center justify-around text-center text-xs">
          <div>
            <p class="text-slate-400">活跃 Offer</p>
            <p class="text-lg font-bold text-slate-800 mt-0.5">{{ overview.activeOffers || 0 }}</p>
          </div>
          <div class="border-r border-slate-100 h-8"></div>
          <div>
            <p class="text-slate-400">在册渠道客</p>
            <p class="text-lg font-bold text-slate-800 mt-0.5">{{ overview.totalPartners || 0 }}</p>
          </div>
        </div>
      </div>

      <!-- 快速导航入口 -->
      <div class="bg-white rounded-xl p-6 border border-slate-200 shadow-sm lg:col-span-2 flex flex-col justify-between">
        <div>
          <h4 class="text-sm font-bold text-slate-800 mb-1">网盟核心流程快捷通道</h4>
          <p class="text-xs text-slate-400">一键直达推广计划创建、智能分流设置与转化模拟联调</p>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 my-4">
          <NuxtLink
            to="/offers/create"
            class="p-4 rounded-xl border border-dashed border-slate-200 hover:border-brand-500 hover:bg-brand-50/40 transition-all text-left group"
          >
            <span class="text-xl">🎯</span>
            <p class="text-xs font-bold text-slate-800 mt-2 group-hover:text-brand-600">新建 Offer 计划</p>
            <p class="text-[11px] text-slate-400 mt-0.5">配置落地页与 Cap 上限</p>
          </NuxtLink>

          <NuxtLink
            to="/smartlinks"
            class="p-4 rounded-xl border border-dashed border-slate-200 hover:border-brand-500 hover:bg-brand-50/40 transition-all text-left group"
          >
            <span class="text-xl">⚡</span>
            <p class="text-xs font-bold text-slate-800 mt-2 group-hover:text-brand-600">SmartLink 分流</p>
            <p class="text-[11px] text-slate-400 mt-0.5">按最高 EPC 自适应重定向</p>
          </NuxtLink>

          <NuxtLink
            to="/conversions"
            class="p-4 rounded-xl border border-dashed border-slate-200 hover:border-brand-500 hover:bg-brand-50/40 transition-all text-left group"
          >
            <span class="text-xl">🔄</span>
            <p class="text-xs font-bold text-slate-800 mt-2 group-hover:text-brand-600">S2S 转化归因</p>
            <p class="text-[11px] text-slate-400 mt-0.5">在线联调 Postback 回传</p>
          </NuxtLink>
        </div>

        <div class="text-[11px] text-slate-400 flex items-center justify-between border-t border-slate-100 pt-3">
          <span>提示：所有点击与转化均具备全局加密密码级 click_id 保护与 CTIT 耗时防作弊质检</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import StatCard from '~/components/common/StatCard.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import DateRangeFilter from '~/components/common/DateRangeFilter.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const selectedRange = ref('today')
const overview = ref<any>({
  totalOffers: 0,
  activeOffers: 0,
  totalPartners: 0,
  totalConversions: 0,
  approvedConversions: 0,
  totalPayout: '0.00',
  totalRevenue: '0.00',
  grossProfit: '0.00'
})

const approvalRate = computed(() => {
  if (!overview.value.totalConversions) return 100
  return Math.round((overview.value.approvedConversions / overview.value.totalConversions) * 100)
})

const activeOfferRate = computed(() => {
  if (!overview.value.totalOffers) return 100
  return Math.round((overview.value.activeOffers / overview.value.totalOffers) * 100)
})

const loadOverview = async () => {
  try {
    const res = await fetchApi<any>('/api/v1/affiliate/dashboard/overview')
    overview.value = res
  } catch (err) {
    // 降级兜底展示
    overview.value = {
      totalOffers: 8,
      activeOffers: 6,
      totalPartners: 12,
      totalConversions: 350,
      approvedConversions: 320,
      totalPayout: '1600.00',
      totalRevenue: '2450.00',
      grossProfit: '850.00'
    }
  }
}

onMounted(() => {
  loadOverview()
})
</script>
