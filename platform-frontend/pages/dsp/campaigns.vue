<template>
  <div class="space-y-6">
    <!-- 头部操作区 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">DSP 广告活动预算与实时控频中心 (DSP Budget & PID Pacing)</h3>
        <p class="text-xs text-slate-500">毫秒级竞价前原子预占 (Reserve)、胜出确认扣款 (Confirm)、未胜出原路返还 (Release) 与预算水泛滥告警</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showConfigModal = true"
        >
          <span>⚙️ 配置活动预算池</span>
        </button>
      </div>
    </div>

    <!-- 预算控盘看板 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">当前查询活动 ID</span>
        <div class="text-lg font-bold text-slate-800 font-mono mt-1 truncate">{{ activeCampaignId }}</div>
        <span class="text-[10px] text-slate-500 font-mono">状态: {{ campaignInfo?.status || 'ACTIVE' }}</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">可用预算余额 (Balance)</span>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-1">${{ Number(campaignInfo?.balance || 0).toFixed(2) }}</div>
        <span class="text-[10px] text-slate-500">总池预算额度</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">日消耗上限 (Daily Budget)</span>
        <div class="text-2xl font-bold text-indigo-600 font-mono mt-1">${{ Number(campaignInfo?.dailyBudget || 0).toFixed(2) }}</div>
        <span class="text-[10px] text-slate-500">单日平滑消耗限制</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">水位告警等级</span>
        <div class="text-2xl font-bold font-mono mt-1" :class="alertLevel > 0 ? 'text-amber-600' : 'text-slate-400'">
          Level {{ alertLevel }}
        </div>
        <span class="text-[10px]" :class="alertLevel > 0 ? 'text-amber-600 font-semibold' : 'text-slate-500'">
          {{ alertLevel === 0 ? '平稳消耗中' : '达到告警阈值' }}
        </span>
      </div>
    </div>

    <!-- 竞价前原子预占与闭环测试器 -->
    <div class="bg-slate-900 text-white rounded-2xl p-6 shadow-xl border border-slate-800">
      <div class="flex items-center justify-between pb-4 border-b border-slate-800 mb-4">
        <div>
          <h4 class="text-sm font-bold tracking-wide text-white">⚡ RTB 毫秒级预算原子预占与胜出闭环试验台</h4>
          <p class="text-xs text-slate-400 mt-0.5">模拟 SSP 下发竞价请求时，DSP 在 20ms 内完成资金扣减并在胜出/未胜出时流转状态机</p>
        </div>
        <span class="px-2.5 py-1 rounded bg-slate-800 text-[11px] font-mono text-indigo-400 border border-slate-700">Atomicity & Redis Lock</span>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">活动 ID (Campaign ID)</label>
          <input
            v-model="reserveForm.campaignId"
            type="text"
            class="w-full text-xs font-mono bg-slate-800/90 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-indigo-500"
          />
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">受众用户 ID (User ID)</label>
          <input
            v-model="reserveForm.userId"
            type="text"
            class="w-full text-xs font-mono bg-slate-800/90 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-indigo-500"
          />
        </div>
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">预占出价金 (USD)</label>
          <input
            v-model="reserveForm.amount"
            type="number"
            step="0.001"
            class="w-full text-xs font-mono bg-slate-800/90 border border-slate-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-indigo-500"
          />
        </div>
        <div class="flex gap-2">
          <button
            type="button"
            :disabled="reserving"
            class="flex-1 py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white transition-colors disabled:bg-slate-700"
            @click="doReserve"
          >
            {{ reserving ? '预占中...' : '🔒 竞价前原子预占' }}
          </button>
        </div>
      </div>

      <!-- 当前预占流水操作 -->
      <div v-if="lastReservation" class="mt-4 p-4 rounded-xl bg-slate-800/80 border border-slate-700 text-xs flex flex-wrap items-center justify-between gap-3">
        <div class="space-y-1 font-mono">
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full bg-amber-400 animate-pulse"></span>
            <span class="font-bold text-amber-400">预占成功 Reservation ID:</span>
            <span class="text-white">{{ lastReservation.id }}</span>
          </div>
          <div class="text-slate-400 text-[11px]">
            金额: ${{ Number(lastReservation.amount).toFixed(4) }} USD · 活动: {{ lastReservation.campaignId }} · 用户: {{ lastReservation.userId }}
          </div>
        </div>

        <div class="flex items-center gap-2">
          <button
            type="button"
            :disabled="confirming"
            class="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold transition-colors disabled:bg-slate-700"
            @click="doConfirm"
          >
            {{ confirming ? '确认中...' : '✅ 胜出并转正扣款 (Confirm)' }}
          </button>
          <button
            type="button"
            :disabled="releasing"
            class="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold transition-colors disabled:bg-slate-700"
            @click="doRelease"
          >
            {{ releasing ? '释放中...' : '↩️ 未胜出原路释放 (Release)' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 预算追加注资与水位告警测试 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-4">
        <h4 class="text-sm font-bold text-slate-800">💰 活动预算追加注资 (Credit Budget)</h4>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block font-semibold text-slate-700 mb-1">追加金额 (USD)</label>
            <input v-model="creditAmount" type="number" step="1.0" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <button
            type="button"
            :disabled="crediting"
            class="w-full py-2.5 text-xs font-bold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors disabled:bg-slate-300"
            @click="doCredit"
          >
            {{ crediting ? '注资中...' : '追加注资到当前活动' }}
          </button>
        </div>
      </div>

      <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-4">
        <h4 class="text-sm font-bold text-slate-800">🚨 水位消耗告警测试 (Check Alert Threshold)</h4>
        <div class="space-y-3 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">当前消耗金额 (USD)</label>
              <input v-model="alertTestSpend" type="number" step="1.0" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">日预算总额 (USD)</label>
              <input v-model="alertTestBudget" type="number" step="1.0" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
            </div>
          </div>
          <button
            type="button"
            class="w-full py-2.5 text-xs font-bold rounded-lg bg-amber-600 hover:bg-amber-700 text-white transition-colors"
            @click="checkAlert"
          >
            试算告警水位触发
          </button>
        </div>
      </div>
    </div>

    <!-- 配置模态框 -->
    <div v-if="showConfigModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-md w-full p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h4 class="text-sm font-bold text-slate-900">配置活动预算额度</h4>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showConfigModal = false">✕</button>
        </div>

        <div class="space-y-3 text-xs">
          <div>
            <label class="block font-semibold text-slate-700 mb-1">广告活动 ID</label>
            <input v-model="configForm.campaignId" type="text" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">总活动预算 (USD)</label>
            <input v-model="configForm.amount" type="number" step="10.0" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">单日预算上限 (USD)</label>
            <input v-model="configForm.dailyBudget" type="number" step="10.0" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
        </div>

        <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
          <button type="button" class="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700" @click="showConfigModal = false">取消</button>
          <button type="button" :disabled="savingConfig" class="px-4 py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white disabled:bg-slate-300" @click="saveConfig">
            {{ savingConfig ? '保存中...' : '确认配置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const activeCampaignId = ref('camp_summer_2026')
const campaignInfo = ref<any>(null)
const alertLevel = ref(0)

const reserving = ref(false)
const confirming = ref(false)
const releasing = ref(false)
const crediting = ref(false)
const savingConfig = ref(false)
const showConfigModal = ref(false)

const reserveForm = ref({
  campaignId: 'camp_summer_2026',
  userId: 'usr_888999',
  amount: 0.15
})

const lastReservation = ref<any>(null)
const creditAmount = ref(500)
const alertTestSpend = ref(850)
const alertTestBudget = ref(1000)

const configForm = ref({
  campaignId: 'camp_summer_2026',
  amount: 5000,
  dailyBudget: 1000
})

const loadCampaignBudget = async () => {
  try {
    const data = await fetchApi<any>(`/api/v1/budget/campaigns/${activeCampaignId.value}`)
    campaignInfo.value = data
  } catch (err: any) {
    campaignInfo.value = { balance: 0, dailyBudget: 0, status: 'OFFLINE' }
  }
}

const doReserve = async () => {
  reserving.value = true
  try {
    const res = await fetchApi<any>('/api/v1/budget/reserve', {
      method: 'POST',
      body: {
        campaignId: reserveForm.value.campaignId,
        userId: reserveForm.value.userId,
        amount: reserveForm.value.amount
      }
    })
    lastReservation.value = res
    showToast(`成功预占 $${Number(res.amount).toFixed(4)} USD`, 'success')
    await loadCampaignBudget()
  } catch (err: any) {
    showToast(`预算预占失败：${err.message || err}`, 'error', 5000)
  } finally {
    reserving.value = false
  }
}

const doConfirm = async () => {
  if (!lastReservation.value) return
  confirming.value = true
  try {
    await fetchApi('/api/v1/budget/confirm', {
      method: 'POST',
      body: {
        reservationId: lastReservation.value.id,
        campaignId: lastReservation.value.campaignId,
        userId: lastReservation.value.userId,
        amount: lastReservation.value.amount
      }
    })
    showToast('预占已转正并确认扣款 (Confirmed)', 'success')
    lastReservation.value = null
    await loadCampaignBudget()
  } catch (err: any) {
    showToast(`确认扣款失败：${err.message || err}`, 'error', 5000)
  } finally {
    confirming.value = false
  }
}

const doRelease = async () => {
  if (!lastReservation.value) return
  releasing.value = true
  try {
    await fetchApi('/api/v1/budget/release', {
      method: 'POST',
      body: {
        reservationId: lastReservation.value.id,
        campaignId: lastReservation.value.campaignId,
        userId: lastReservation.value.userId,
        amount: lastReservation.value.amount
      }
    })
    showToast('预占资金已原路解冻释放 (Released)', 'success')
    lastReservation.value = null
    await loadCampaignBudget()
  } catch (err: any) {
    showToast(`释放资金失败：${err.message || err}`, 'error', 5000)
  } finally {
    releasing.value = false
  }
}

const doCredit = async () => {
  crediting.value = true
  try {
    await fetchApi(`/api/v1/budget/campaigns/${activeCampaignId.value}/credit`, {
      method: 'POST',
      body: { amount: creditAmount.value }
    })
    showToast(`成功向活动注资 $${creditAmount.value} USD`, 'success')
    await loadCampaignBudget()
  } catch (err: any) {
    showToast(`追加注资失败：${err.message || err}`, 'error', 5000)
  } finally {
    crediting.value = false
  }
}

const checkAlert = async () => {
  try {
    const res: any = await fetchApi(
      `/api/v1/budget/campaigns/${activeCampaignId.value}/alert?currentSpend=${alertTestSpend.value}&dailyBudget=${alertTestBudget.value}`
    )
    alertLevel.value = res?.alertLevel ?? 0
    showToast(`告警检测结果: Alert Level ${alertLevel.value}`, alertLevel.value > 0 ? 'warning' : 'success')
  } catch (err: any) {
    showToast(`告警检测失败：${err.message || err}`, 'error', 5000)
  }
}

const saveConfig = async () => {
  savingConfig.value = true
  try {
    await fetchApi(`/api/v1/budget/campaigns/${configForm.value.campaignId}`, {
      method: 'POST',
      body: {
        amount: configForm.value.amount,
        dailyBudget: configForm.value.dailyBudget
      }
    })
    activeCampaignId.value = configForm.value.campaignId
    showToast('预算配置更新成功', 'success')
    showConfigModal.value = false
    await loadCampaignBudget()
  } catch (err: any) {
    showToast(`配置保存失败：${err.message || err}`, 'error', 5000)
  } finally {
    savingConfig.value = false
  }
}

onMounted(() => {
  loadCampaignBudget()
})
</script>
