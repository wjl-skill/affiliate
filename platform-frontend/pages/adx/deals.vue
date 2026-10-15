<template>
  <div class="space-y-6">
    <!-- 头部操作区 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">ADX 私有交易市场协议 (OpenRTB 2.5 PMP Deals)</h3>
        <p class="text-xs text-slate-500">管理保量合约 (PG)、优先交易 (Preferred Deal) 与席位白名单私有竞价规则及清算底价</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showCreateModal = true"
        >
          <span>➕ 注册私有 Deal 合约</span>
        </button>
      </div>
    </div>

    <!-- PMP 核心指标卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">活跃 Deal 总数</span>
        <div class="text-2xl font-bold text-slate-800 font-mono mt-1">{{ activeDealsCount }}</div>
        <span class="text-[10px] text-emerald-600 font-medium">白名单专属竞价通道</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">PG 保量合约 (Tier 1)</span>
        <div class="text-2xl font-bold text-indigo-600 font-mono mt-1">{{ pgCount }}</div>
        <span class="text-[10px] text-slate-500">最高优先级出价优先撮合</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">Preferred Deal (Tier 2)</span>
        <div class="text-2xl font-bold text-sky-600 font-mono mt-1">{{ preferredCount }}</div>
        <span class="text-[10px] text-slate-500">固定清算底价洽购</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">Private Auction (Tier 3)</span>
        <div class="text-2xl font-bold text-amber-600 font-mono mt-1">{{ privateAuctionCount }}</div>
        <span class="text-[10px] text-slate-500">限定 DSP 席位二价竞价</span>
      </div>
    </div>

    <!-- 撮合沙箱试算工具 -->
    <div class="bg-indigo-50/70 border border-indigo-200/80 rounded-xl p-4">
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-2">
          <span class="text-base">🧪</span>
          <h4 class="text-xs font-bold text-indigo-950 uppercase tracking-wide">PMP 竞价撮合与席位授权实时沙箱</h4>
        </div>
        <span class="text-[11px] text-indigo-600 font-medium">调用 POST /api/v1/adx/deals/match 校验 OpenRTB 出价</span>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
        <div>
          <label class="block text-[11px] font-semibold text-slate-700 mb-1">Deal ID</label>
          <input
            v-model="matchForm.dealId"
            type="text"
            placeholder="例如: deal_vip_1001"
            class="w-full text-xs font-mono border border-slate-300 rounded-lg px-3 py-2 bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div>
          <label class="block text-[11px] font-semibold text-slate-700 mb-1">DSP Buyer Seat (席位)</label>
          <input
            v-model="matchForm.buyerSeat"
            type="text"
            placeholder="例如: dsp_bidder_01"
            class="w-full text-xs font-mono border border-slate-300 rounded-lg px-3 py-2 bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div>
          <label class="block text-[11px] font-semibold text-slate-700 mb-1">买方出价 CPM (USD)</label>
          <input
            v-model="matchForm.bidPrice"
            type="number"
            step="0.01"
            placeholder="例如: 4.50"
            class="w-full text-xs font-mono border border-slate-300 rounded-lg px-3 py-2 bg-white text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div>
          <button
            type="button"
            :disabled="testingMatch"
            class="w-full py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white transition-colors shadow-sm disabled:bg-slate-300"
            @click="testMatch"
          >
            {{ testingMatch ? '撮合校验中...' : '⚡ 校验出价匹配' }}
          </button>
        </div>
      </div>

      <!-- 撮合结果提示 -->
      <div v-if="matchResult" class="mt-3 p-3 rounded-lg border text-xs flex items-center justify-between" :class="matchResult.isMatched ? 'bg-emerald-50 border-emerald-200 text-emerald-900' : 'bg-rose-50 border-rose-200 text-rose-900'">
        <div class="flex items-center gap-2">
          <span class="text-sm">{{ matchResult.isMatched ? '✅' : '❌' }}</span>
          <span class="font-bold">{{ matchResult.isMatched ? '出价匹配成功！' : '出价未通过 Deal 规则限制：' }}</span>
          <span>{{ matchResult.message }}</span>
        </div>
        <div v-if="matchResult.isMatched" class="flex items-center gap-3">
          <span class="font-mono bg-emerald-200/80 text-emerald-900 px-2 py-0.5 rounded text-[11px] font-bold">优先级权重: {{ matchResult.priority }}</span>
          <span class="text-[11px] font-semibold text-emerald-700">清算类型: {{ matchResult.deal?.dealType }}</span>
        </div>
      </div>
    </div>

    <!-- Deal 列表数据表 -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="p-4 border-b border-slate-200 flex items-center justify-between">
        <h4 class="text-sm font-bold text-slate-800">已生效的私有 Deal 清单</h4>
        <button
          type="button"
          class="text-xs text-indigo-600 hover:text-indigo-800 font-semibold"
          @click="loadDeals"
        >
          🔄 刷新列表
        </button>
      </div>

      <CommonTable
        :columns="columns"
        :data="deals"
        :loading="loading"
        search-placeholder="搜索 Deal ID 或广告主..."
      >
        <template #cell-dealId="{ row }">
          <div class="font-mono text-xs font-bold text-slate-800">{{ row.dealId }}</div>
        </template>

        <template #cell-advertiser="{ row }">
          <div class="text-xs text-slate-700 font-semibold">{{ row.advertiserId }}</div>
        </template>

        <template #cell-dealType="{ row }">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold" :class="getTypeBadge(row.dealType)">
            {{ row.dealType }}
          </span>
        </template>

        <template #cell-floorPrice="{ row }">
          <span class="font-mono text-xs font-bold text-emerald-600">${{ Number(row.floorPrice).toFixed(2) }}</span>
        </template>

        <template #cell-seats="{ row }">
          <div class="flex flex-wrap gap-1 max-w-xs">
            <span
              v-for="s in row.allowedSeats"
              :key="s"
              class="px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 text-[10px] font-mono border border-slate-200"
            >{{ s }}</span>
            <span v-if="!row.allowedSeats || row.allowedSeats.length === 0" class="text-[11px] text-slate-400 italic">全席位公开 (Open)</span>
          </div>
        </template>

        <template #cell-status="{ row }">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold" :class="row.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'">
            {{ row.active ? '生效中' : '已暂停' }}
          </span>
        </template>

        <template #cell-actions="{ row }">
          <button
            type="button"
            class="text-xs text-rose-600 hover:text-rose-800 font-semibold"
            @click="deleteDeal(row.dealId)"
          >
            删除
          </button>
        </template>
      </CommonTable>
    </div>

    <!-- 新建 Deal 模态框 -->
    <div v-if="showCreateModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h4 class="text-sm font-bold text-slate-900">注册新 PMP 私有交易合约</h4>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showCreateModal = false">✕</button>
        </div>

        <div class="space-y-3 text-xs">
          <div>
            <label class="block font-semibold text-slate-700 mb-1">Deal ID (唯一标识)</label>
            <input v-model="createForm.dealId" type="text" placeholder="如 deal_nike_pg_01" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">广告主 ID (Advertiser)</label>
            <input v-model="createForm.advertiserId" type="text" placeholder="如 adv_nike_global" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">交易类型 (Deal Type)</label>
            <select v-model="createForm.dealType" class="w-full border border-slate-300 rounded-lg p-2.5 bg-white focus:ring-1 focus:ring-indigo-500 font-semibold">
              <option value="PROGRAMMATIC_GUARANTEED">PROGRAMMATIC_GUARANTEED (Tier 1 保量合约)</option>
              <option value="PREFERRED_DEAL">PREFERRED_DEAL (Tier 2 优先洽购)</option>
              <option value="PRIVATE_AUCTION">PRIVATE_AUCTION (Tier 3 白名单私有竞价)</option>
            </select>
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">最低保护出价底价 Floor Price (USD CPM)</label>
            <input v-model="createForm.floorPrice" type="number" step="0.01" placeholder="如 3.50" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">准入 DSP 席位白名单 (英文逗号分隔，留空允许全席位)</label>
            <input v-model="createForm.allowedSeatsStr" type="text" placeholder="如 dsp_google,dsp_ttd,seat_bytedance" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
        </div>

        <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
          <button type="button" class="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700" @click="showCreateModal = false">取消</button>
          <button type="button" :disabled="submitting" class="px-4 py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white disabled:bg-slate-300" @click="submitCreate">
            {{ submitting ? '提交中...' : '确认注册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const submitting = ref(false)
const testingMatch = ref(false)
const showCreateModal = ref(false)

const deals = ref<any[]>([])

const matchForm = ref({
  dealId: '',
  buyerSeat: '',
  bidPrice: 5.0
})
const matchResult = ref<any>(null)

const createForm = ref({
  dealId: '',
  advertiserId: '',
  dealType: 'PREFERRED_DEAL',
  floorPrice: 2.50,
  allowedSeatsStr: ''
})

const columns = [
  { key: 'dealId', label: 'Deal ID', slot: 'dealId' },
  { key: 'advertiser', label: '广告主 ID', slot: 'advertiser' },
  { key: 'dealType', label: '协议模式', slot: 'dealType' },
  { key: 'floorPrice', label: '底价 (USD CPM)', slot: 'floorPrice' },
  { key: 'seats', label: '受邀买方席位 (wseat)', slot: 'seats' },
  { key: 'status', label: '状态', slot: 'status' },
  { key: 'actions', label: '操作', slot: 'actions' }
]

const activeDealsCount = computed(() => deals.value.filter(d => d.active).length)
const pgCount = computed(() => deals.value.filter(d => d.dealType === 'PROGRAMMATIC_GUARANTEED').length)
const preferredCount = computed(() => deals.value.filter(d => d.dealType === 'PREFERRED_DEAL').length)
const privateAuctionCount = computed(() => deals.value.filter(d => d.dealType === 'PRIVATE_AUCTION').length)

const getTypeBadge = (type: string) => {
  switch (type) {
    case 'PROGRAMMATIC_GUARANTEED': return 'bg-purple-100 text-purple-800 border border-purple-200'
    case 'PREFERRED_DEAL': return 'bg-sky-100 text-sky-800 border border-sky-200'
    case 'PRIVATE_AUCTION': return 'bg-amber-100 text-amber-800 border border-amber-200'
    default: return 'bg-slate-100 text-slate-800'
  }
}

const loadDeals = async () => {
  loading.value = true
  try {
    const list = await fetchApi<any[]>('/api/v1/adx/deals') || []
    deals.value = list
  } catch (err: any) {
    deals.value = []
    showToast(`加载 PMP Deal 列表失败：${err.message || err}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const submitCreate = async () => {
  if (!createForm.value.dealId || !createForm.value.advertiserId) {
    showToast('请完整填写 Deal ID 与广告主信息', 'warning')
    return
  }
  submitting.value = true
  try {
    const seats = createForm.value.allowedSeatsStr
      ? createForm.value.allowedSeatsStr.split(',').map(s => s.trim()).filter(Boolean)
      : []

    await fetchApi('/api/v1/adx/deals', {
      method: 'POST',
      body: {
        dealId: createForm.value.dealId,
        advertiserId: createForm.value.advertiserId,
        dealType: createForm.value.dealType,
        floorPrice: createForm.value.floorPrice,
        allowedSeats: seats,
        active: true
      }
    })
    showToast(`成功注册私有交易合约 ${createForm.value.dealId}`, 'success')
    showCreateModal.value = false
    createForm.value = { dealId: '', advertiserId: '', dealType: 'PREFERRED_DEAL', floorPrice: 2.50, allowedSeatsStr: '' }
    await loadDeals()
  } catch (err: any) {
    showToast(`注册 Deal 失败：${err.message || err}`, 'error', 5000)
  } finally {
    submitting.value = false
  }
}

const deleteDeal = async (dealId: string) => {
  if (!confirm(`确定删除私有交易合约 ${dealId} 吗？`)) return
  try {
    await fetchApi(`/api/v1/adx/deals/${dealId}`, { method: 'DELETE' })
    showToast(`Deal ${dealId} 已删除`, 'success')
    await loadDeals()
  } catch (err: any) {
    showToast(`删除失败：${err.message || err}`, 'error', 5000)
  }
}

const testMatch = async () => {
  if (!matchForm.value.dealId || !matchForm.value.buyerSeat) {
    showToast('请填写测试 Deal ID 与买方席位', 'warning')
    return
  }
  testingMatch.value = true
  try {
    const res = await fetchApi<any>('/api/v1/adx/deals/match', {
      method: 'POST',
      body: {
        dealId: matchForm.value.dealId,
        buyerSeat: matchForm.value.buyerSeat,
        bidPrice: matchForm.value.bidPrice
      }
    })
    matchResult.value = res
  } catch (err: any) {
    matchResult.value = { isMatched: false, message: err.message || '请求失败' }
  } finally {
    testingMatch.value = false
  }
}

onMounted(() => {
  loadDeals()
})
</script>
