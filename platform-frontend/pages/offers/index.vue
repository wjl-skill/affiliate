<template>
  <div class="space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">Offer 推广计划管理</h3>
        <p class="text-xs text-slate-500">管理广告主入驻的 CPA/CPS/CPI 等效果营销计划与转化 Cap 配额</p>
      </div>
      <NuxtLink
        to="/offers/create"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
      >
        <span>➕</span>
        <span>新建推广计划</span>
      </NuxtLink>
    </div>

    <!-- 数据表格 -->
    <CommonTable
      :columns="columns"
      :data="offers"
      :loading="loading"
      search-placeholder="搜索 Offer 名称、ID 或广告主..."
    >
      <!-- 自定义计划信息列 -->
      <template #cell-title="{ row }">
        <div class="flex flex-col">
          <span class="font-bold text-slate-900">{{ row.title }}</span>
          <span class="text-[11px] text-slate-400 font-mono">ID: {{ row.id }} · 广告主: {{ row.advertiserId }}</span>
        </div>
      </template>

      <!-- 自定义计费模式 -->
      <template #cell-payoutType="{ row }">
        <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">
          {{ row.payoutType }}
        </span>
      </template>

      <!-- 自定义佣金与营收 -->
      <template #cell-pricing="{ row }">
        <div class="text-xs">
          <span class="text-emerald-600 font-bold">${{ row.defaultPayout }}</span>
          <span class="text-slate-400 mx-1">/</span>
          <span class="text-slate-700 font-medium">${{ row.defaultRevenue }}</span>
        </div>
      </template>

      <!-- 自定义状态 -->
      <template #cell-status="{ row }">
        <StatusTag :status="row.status" />
      </template>

      <!-- 自定义转化 Cap -->
      <template #cell-dailyConversionCap="{ row }">
        <div class="text-xs">
          <span v-if="row.dailyConversionCap > 0" class="font-semibold text-slate-800">
            {{ row.dailyConversionCap }} 单/日
          </span>
          <span v-else class="text-slate-400 italic">不设上限</span>
          <div v-if="row.fallbackOfferId" class="text-[10px] text-brand-600 mt-0.5">
            兜底: {{ row.fallbackOfferId }}
          </div>
        </div>
      </template>

      <!-- 操作列 -->
      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <CopyButton :text="`http://localhost:8080/affiliate/click?offer_id=${row.id}&aff_id=AFF_ID`">
            推广短链
          </CopyButton>
        </div>
      </template>
    </CommonTable>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import CopyButton from '~/components/common/CopyButton.vue'
import { useApi } from '~/composables/useApi'

const { fetchApi } = useApi()
const loading = ref(false)
const offers = ref<any[]>([])

const columns = [
  { key: 'title', label: '推广计划 (Title / ID)' },
  { key: 'payoutType', label: '计费类型' },
  { key: 'pricing', label: '渠道佣金 / 广告主收费' },
  { key: 'status', label: '状态' },
  { key: 'dailyConversionCap', label: '转化配额 (Cap)' },
  { key: 'actions', label: '操作' }
]

const loadOffers = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/affiliate/offers')
    offers.value = res
  } catch (err) {
    // 降级演示初始数据
    offers.value = [
      {
        id: 'off-101',
        advertiserId: 'adv-nike',
        title: 'Nike Summer Shoes 2026',
        landingPageUrl: 'https://nike.com/buy?click_id={click_id}',
        payoutType: 'CPA',
        defaultPayout: '5.0000',
        defaultRevenue: '8.0000',
        status: 'ACTIVE',
        dailyConversionCap: 200,
        fallbackOfferId: 'off-102'
      },
      {
        id: 'off-102',
        advertiserId: 'adv-adidas',
        title: 'Adidas Running Shoes Fallback',
        landingPageUrl: 'https://adidas.com/buy?click_id={click_id}',
        payoutType: 'CPA',
        defaultPayout: '3.5000',
        defaultRevenue: '5.5000',
        status: 'ACTIVE',
        dailyConversionCap: 0
      },
      {
        id: 'off-201',
        advertiserId: 'adv-fintech',
        title: 'Credit Card Approval Lead',
        landingPageUrl: 'https://bank.com/card?click_id={click_id}',
        payoutType: 'CPL',
        defaultPayout: '18.0000',
        defaultRevenue: '25.0000',
        status: 'ACTIVE',
        dailyConversionCap: 50
      }
    ]
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadOffers()
})
</script>
