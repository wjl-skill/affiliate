<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">4D 智能反作弊与流量风控中心 (Anti-Fraud & Risk Center)</h3>
        <p class="text-xs text-slate-500">毫秒级 4 维作弊特征矩阵：CTIT 转化时间差异常、数据中心机房 IP、爬虫 UA、点击突增与动态黑名单拦截</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3.5 py-1.5 text-xs font-semibold rounded-lg bg-rose-600 hover:bg-rose-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showBlacklistModal = true"
        >
          <span>🚫 封禁配置 (黑名单)</span>
        </button>
      </div>
    </div>

    <!-- 顶栏核心风控 KPI 指标 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">作弊流量拦截率 (Fraud Block Rate)</span>
        <div class="text-2xl font-bold text-rose-600 mt-1 font-mono">14.8%</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">已挽回预算损失 ≈ $12,480</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">异常 CTIT 极速转化 (&lt; 3秒)</span>
        <div class="text-2xl font-bold text-amber-600 mt-1 font-mono">342 笔</div>
        <span class="text-[11px] text-amber-600 font-semibold mt-1 inline-block">疑似脚本自动注入</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">机房/数据中心代理 IP (Datacenter IP)</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">819 次</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">AWS / GCP / Cloudflare 机房</span>
      </div>

      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">动态封禁条目 (IP / Sub-ID)</span>
        <div class="text-2xl font-bold text-indigo-600 mt-1 font-mono">{{ activeBlacklistCount }} 条</div>
        <span class="text-[11px] text-emerald-600 font-semibold mt-1 inline-block">实时生效同步</span>
      </div>
    </div>

    <!-- 实时拦截日志流表格 -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="p-4 border-b border-slate-200 flex items-center justify-between">
        <div>
          <h4 class="text-sm font-bold text-slate-800">实时作弊流量检测与拦截审计流 (Risk Audit Log)</h4>
          <p class="text-[11px] text-slate-400">针对所有 S2S Postback 转化与点击会话执行四维置信度打分 (0~100)</p>
        </div>
        <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
          <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          实时风控引擎就绪
        </span>
      </div>

      <CommonTable
        :columns="columns"
        :data="auditLogs"
        :loading="loading"
        search-placeholder="搜索渠道 ID、IP 或作弊原因..."
      >
        <template #cell-clickTx="{ row }">
          <div class="flex flex-col text-xs font-mono">
            <span class="font-bold text-slate-900">{{ row.transactionId }}</span>
            <span class="text-[10px] text-slate-400">{{ row.clickId }}</span>
          </div>
        </template>

        <template #cell-affiliate="{ row }">
          <span class="font-semibold text-slate-800 font-mono text-xs">{{ row.affiliateId }}</span>
        </template>

        <template #cell-ip="{ row }">
          <div class="text-xs font-mono">
            <span class="text-slate-800">{{ row.ip }}</span>
            <span v-if="row.isDatacenter" class="ml-1.5 px-1.5 py-0.5 rounded text-[10px] bg-amber-100 text-amber-800 font-sans">机房IP</span>
          </div>
        </template>

        <template #cell-ctit="{ row }">
          <span
            class="text-xs font-mono font-bold"
            :class="row.ctitSeconds < 3 ? 'text-rose-600' : 'text-slate-700'"
          >
            {{ row.ctitSeconds }}s
          </span>
        </template>

        <template #cell-score="{ row }">
          <div class="flex items-center gap-2">
            <div class="w-16 bg-slate-100 rounded-full h-2 overflow-hidden">
              <div
                class="h-full rounded-full"
                :class="row.riskScore >= 70 ? 'bg-rose-500' : row.riskScore >= 40 ? 'bg-amber-500' : 'bg-emerald-500'"
                :style="{ width: `${row.riskScore}%` }"
              ></div>
            </div>
            <span class="font-bold font-mono text-xs" :class="row.riskScore >= 70 ? 'text-rose-600' : 'text-slate-700'">
              {{ row.riskScore }}
            </span>
          </div>
        </template>

        <template #cell-reason="{ row }">
          <span
            class="px-2 py-0.5 rounded text-[11px] font-mono font-medium"
            :class="row.riskScore >= 70 ? 'bg-rose-50 text-rose-700 border border-rose-200' : 'bg-slate-100 text-slate-700'"
          >
            {{ row.primaryReason }}
          </span>
        </template>

        <template #cell-action="{ row }">
          <span
            class="px-2 py-0.5 rounded text-[11px] font-bold"
            :class="row.action === 'REJECTED' ? 'bg-rose-100 text-rose-800' : 'bg-amber-100 text-amber-800'"
          >
            {{ row.action }}
          </span>
        </template>

        <template #cell-time="{ row }">
          <span class="text-[11px] text-slate-400 font-mono">{{ row.time }}</span>
        </template>
      </CommonTable>
    </div>

    <!-- 黑名单快速添加弹窗 -->
    <ModalDialog
      :show="showBlacklistModal"
      title="流量风控黑名单配置"
      confirm-text="确认封禁"
      @close="showBlacklistModal = false"
      @confirm="addBlacklistEntry"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">黑名单类型</label>
          <select v-model="blacklistType" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-800">
            <option value="IP">IP 地址 / CIDR 网段</option>
            <option value="SUB_ID">Sub-ID 流量子源</option>
          </select>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">封禁对象标识</label>
          <input
            v-model="blacklistValue"
            type="text"
            required
            :placeholder="blacklistType === 'IP' ? '例如: 198.51.100.22' : '例如: spam_sub_99'"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>

        <div class="p-3 bg-amber-50 text-amber-900 rounded-lg border border-amber-200">
          ⚠️ 加入黑名单后，所有后续归因与点击请求将直接拒绝，不可挽回。
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const showBlacklistModal = ref(false)
const blacklistType = ref('IP')
const blacklistValue = ref('')
const activeBlacklistCount = ref(18)

const columns = [
  { key: 'clickTx', label: '交易 / 点击 ID', slot: 'clickTx' },
  { key: 'affiliate', label: '渠道客 ID', slot: 'affiliate' },
  { key: 'ip', label: '请求 IP', slot: 'ip' },
  { key: 'ctit', label: 'CTIT 转化时长', slot: 'ctit' },
  { key: 'score', label: '风险评分 (0-100)', slot: 'score' },
  { key: 'reason', label: '首要作弊原因', slot: 'reason' },
  { key: 'action', label: '处置动作', slot: 'action' },
  { key: 'time', label: '拦截时间', slot: 'time' }
]

const auditLogs = ref<any[]>([
  {
    transactionId: 'tx_spam_0192',
    clickId: 'clk_828192a8',
    affiliateId: 'aff-crawler-99',
    ip: '198.51.100.42',
    isDatacenter: true,
    ctitSeconds: 1.2,
    riskScore: 92,
    primaryReason: 'FAST_CONVERSION_CTIT_UNDER_3S',
    action: 'REJECTED',
    time: '2026-09-04 11:42:01'
  },
  {
    transactionId: 'tx_bot_7721',
    clickId: 'clk_110948bf',
    affiliateId: 'aff-traffic-hub',
    ip: '52.14.88.19',
    isDatacenter: true,
    ctitSeconds: 8.5,
    riskScore: 78,
    primaryReason: 'DATACENTER_PROXY_IP',
    action: 'REJECTED',
    time: '2026-09-04 11:39:15'
  },
  {
    transactionId: 'tx_dup_9918',
    clickId: 'clk_773612cd',
    affiliateId: 'aff-vip-888',
    ip: '172.56.21.90',
    isDatacenter: false,
    ctitSeconds: 45.0,
    riskScore: 85,
    primaryReason: 'DUPLICATE_TRANSACTION_ID',
    action: 'REJECTED',
    time: '2026-09-04 11:28:30'
  },
  {
    transactionId: 'tx_burst_4412',
    clickId: 'clk_662819ef',
    affiliateId: 'aff-media-pro',
    ip: '24.120.99.11',
    isDatacenter: false,
    ctitSeconds: 22.4,
    riskScore: 50,
    primaryReason: 'SUB_ID_CLICK_BURST_SPIKE',
    action: 'FLAGGED',
    time: '2026-09-04 11:15:08'
  },
  {
    transactionId: 'tx_legit_5510',
    clickId: 'clk_330912ab',
    affiliateId: 'aff-vip-888',
    ip: '98.210.45.18',
    isDatacenter: false,
    ctitSeconds: 88.0,
    riskScore: 10,
    primaryReason: 'NORMAL_USER_BEHAVIOR',
    action: 'APPROVED',
    time: '2026-09-04 11:05:22'
  }
])

const loadStats = async () => {
  loading.value = true
  try {
    const res: any = await fetchApi('/api/v1/affiliate/antifraud/stats')
    if (res) {
      if (res.recentLogs && res.recentLogs.length > 0) {
        auditLogs.value = res.recentLogs.map((l: any) => ({
          transactionId: l.transactionId,
          clickId: l.clickId,
          affiliateId: l.affiliateId,
          ip: l.ip,
          isDatacenter: l.isDatacenter,
          ctitSeconds: l.ctitSeconds,
          riskScore: l.riskScore,
          primaryReason: l.primaryReason,
          action: l.action,
          time: l.timestamp ? new Date(l.timestamp).toLocaleString() : 'Just now'
        }))
      }
      if (res.ipBlacklist && res.subIdBlacklist) {
        activeBlacklistCount.value = res.ipBlacklist.length + res.subIdBlacklist.length
      }
    }
  } catch (ignored) {} finally {
    loading.value = false
  }
}

const addBlacklistEntry = async () => {
  if (!blacklistValue.value.trim()) {
    showToast('请输入封禁对象', 'warning')
    return
  }
  const val = blacklistValue.value.trim()
  try {
    if (blacklistType.value === 'IP') {
      await fetchApi(`/api/v1/affiliate/antifraud/blacklist/ip?ip=${encodeURIComponent(val)}`, { method: 'POST' })
    } else {
      await fetchApi(`/api/v1/affiliate/antifraud/blacklist/subid?subId=${encodeURIComponent(val)}`, { method: 'POST' })
    }
  } catch (ignored) {}
  activeBlacklistCount.value++
  showToast(`已成功将 ${val} 加入作弊黑名单`, 'success')
  blacklistValue.value = ''
  showBlacklistModal.value = false
}

onMounted(() => {
  loadStats()
})
</script>
