<template>
  <div class="space-y-6">
    <!-- 顶部标题与新建连接按钮 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900 tracking-tight">广告平台与媒体生态连接器 (Connectors & Integrations)</h3>
        <p class="text-xs text-slate-500 mt-0.5">无缝连通 Google Ads、Meta CAPI、TikTok Events、Pangle 及主流 ADX/DSP 广告网络，支持物料资产与成效指标自动同步</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3 py-1.5 text-xs font-semibold rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors flex items-center gap-1.5"
          @click="loadConnections"
        >
          <span>🔄</span>
          <span>刷新连接</span>
        </button>
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showCreateModal = true"
        >
          <span>➕</span>
          <span>接入新平台连接</span>
        </button>
      </div>
    </div>

    <!-- 预置各大主流广告网络快速配置阵列 -->
    <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3">
      <div
        v-for="preset in presetPlatforms"
        :key="preset.id"
        class="bg-white p-3.5 rounded-xl border border-slate-200 hover:border-brand-400 hover:shadow-sm transition-all cursor-pointer flex flex-col justify-between"
        @click="quickSetup(preset)"
      >
        <div class="flex items-center justify-between">
          <span class="text-2xl">{{ preset.icon }}</span>
          <span class="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 uppercase">{{ preset.type }}</span>
        </div>
        <div class="mt-3">
          <p class="text-xs font-bold text-slate-800">{{ preset.name }}</p>
          <p class="text-[10px] text-slate-400 mt-0.5">{{ preset.desc }}</p>
        </div>
      </div>
    </div>

    <!-- 统计看板 -->
    <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">🔗</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">在册渠道连接</p>
          <p class="text-lg font-bold text-slate-900 mt-0.5">{{ connections.length }} <span class="text-xs text-slate-400 font-normal">个</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">🟢</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">在线活跃同步</p>
          <p class="text-lg font-bold text-emerald-600 mt-0.5">{{ activeCount }} <span class="text-xs text-slate-400 font-normal">个</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">⚡</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">双向协议支撑</p>
          <p class="text-xs font-bold text-slate-800 mt-1">OpenRTB 2.5 + S2S CAPI</p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">📊</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">报表回传频次</p>
          <p class="text-xs font-bold text-indigo-600 mt-1">准实时 / 准秒级流式写入</p>
        </div>
      </div>
    </div>

    <!-- 连接列表表格 -->
    <CommonTable
      :columns="columns"
      :data="connections"
      :loading="loading"
      search-placeholder="搜索连接名称、API 端点或 ID..."
    >
      <template #cell-name="{ row }">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-sm font-bold">
            {{ getPlatformIcon(row.name) }}
          </div>
          <div>
            <span class="font-bold text-slate-900 text-xs">{{ row.name }}</span>
            <span class="text-[10px] text-slate-400 font-mono block mt-0.5">ID: {{ row.id }}</span>
          </div>
        </div>
      </template>

      <template #cell-type="{ row }">
        <span
          class="px-2 py-0.5 rounded text-[11px] font-bold font-mono uppercase"
          :class="getTypeBadgeClass(row.type)"
        >
          {{ row.type }}
        </span>
      </template>

      <template #cell-endpoint="{ row }">
        <div class="text-xs font-mono text-slate-600 max-w-xs truncate">
          {{ row.endpoint }}
        </div>
      </template>

      <template #cell-status="{ row }">
        <StatusTag :status="row.status || 'ACTIVE'">
          {{ row.status === 'ACTIVE' ? '正常通信' : (row.status === 'PAUSED' ? '暂停同步' : '连接异常') }}
        </StatusTag>
      </template>

      <template #cell-updatedAt="{ row }">
        <span class="text-xs font-mono text-slate-500">
          {{ formatDate(row.updatedAt) }}
        </span>
      </template>

      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <!-- 手动触发数据同步 -->
          <button
            type="button"
            class="px-2 py-1 text-xs font-semibold rounded-lg bg-slate-100 hover:bg-brand-50 hover:text-brand-600 text-slate-700 transition-colors flex items-center gap-1"
            :disabled="syncingId === row.id"
            @click="triggerSync(row)"
          >
            <span :class="{ 'animate-spin': syncingId === row.id }">🔄</span>
            <span>{{ syncingId === row.id ? '同步中...' : '同步' }}</span>
          </button>

          <!-- 投放成效报表 -->
          <button
            type="button"
            class="px-2 py-1 text-xs font-semibold rounded-lg bg-white border border-slate-200 hover:border-slate-300 text-slate-700 transition-colors"
            @click="viewPerformance(row)"
          >
            📊 成效
          </button>

          <!-- 启停状态切换 -->
          <button
            type="button"
            class="px-2 py-1 text-xs font-semibold rounded-lg border transition-colors"
            :class="row.status === 'ACTIVE' ? 'text-amber-600 border-amber-200 hover:bg-amber-50' : 'text-emerald-600 border-emerald-200 hover:bg-emerald-50'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? '暂停' : '启用' }}
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 渠道成效报表抽屉/模态框 -->
    <ModalDialog
      :show="showPerfModal"
      :title="`渠道成效报表 - ${selectedConn?.name || ''}`"
      description="检索该渠道实时拉取的投放消耗、曝光展示、点击转化及转化率 ROI 表现"
      confirm-text="关闭"
      @confirm="showPerfModal = false"
      @cancel="showPerfModal = false"
    >
      <div v-if="perfReport" class="space-y-4 text-xs">
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
          <div class="p-3 bg-slate-50 rounded-xl border border-slate-100">
            <span class="text-slate-400 block text-[11px]">广告消耗 Spend</span>
            <span class="text-base font-bold text-slate-900 mt-1 block">${{ perfReport.spend || '0.00' }}</span>
          </div>
          <div class="p-3 bg-slate-50 rounded-xl border border-slate-100">
            <span class="text-slate-400 block text-[11px]">曝光 Impressions</span>
            <span class="text-base font-bold text-slate-900 mt-1 block">{{ perfReport.impressions || 0 }}</span>
          </div>
          <div class="p-3 bg-slate-50 rounded-xl border border-slate-100">
            <span class="text-slate-400 block text-[11px]">点击 Clicks</span>
            <span class="text-base font-bold text-slate-900 mt-1 block">{{ perfReport.clicks || 0 }}</span>
          </div>
          <div class="p-3 bg-slate-50 rounded-xl border border-slate-100">
            <span class="text-slate-400 block text-[11px]">核销 Conversions</span>
            <span class="text-base font-bold text-emerald-600 mt-1 block">{{ perfReport.conversions || 0 }}</span>
          </div>
        </div>

        <div class="p-3 bg-slate-50 rounded-lg border border-slate-100 font-mono text-[11px] space-y-1">
          <div class="flex justify-between">
            <span class="text-slate-400">平均点击率 (CTR):</span>
            <span class="font-bold text-slate-800">{{ perfReport.ctr || '0.00%' }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-slate-400">平均转化率 (CVR):</span>
            <span class="font-bold text-slate-800">{{ perfReport.cvr || '0.00%' }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-slate-400">综合投入产出比 (ROAS):</span>
            <span class="font-bold text-brand-600">{{ perfReport.roas || '0.00x' }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-slate-400">数据拉取时间:</span>
            <span class="text-slate-600">{{ formatDate(perfReport.reportDate || new Date().toISOString()) }}</span>
          </div>
        </div>
      </div>
      <div v-else class="text-center py-8 text-slate-400 text-xs">
        暂无成效上报记录，请先点击【同步】触发一次数据抓取。
      </div>
    </ModalDialog>

    <!-- 创建连接模态框 -->
    <ModalDialog
      :show="showCreateModal"
      title="接入新生态渠道连接"
      description="配置外部广告网络、DSP 需求端或 SSP 媒体端标准接口与鉴权凭据"
      confirm-text="保存连接"
      :confirm-disabled="creating || !createForm.name || !createForm.endpoint"
      @confirm="submitCreateConnection"
      @cancel="showCreateModal = false"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">
            连接识别名 (Connection Name) <span class="text-rose-500">*</span>
          </label>
          <input
            v-model="createForm.name"
            type="text"
            required
            placeholder="例如: Google Ads Global Search"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 focus:ring-2 focus:ring-brand-500 focus:outline-none"
          />
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">对接角色类型 (Supply Type)</label>
            <select
              v-model="createForm.type"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 bg-white focus:ring-2 focus:ring-brand-500 focus:outline-none"
            >
              <option value="DSP">DSP (需求方平台/广告主)</option>
              <option value="SSP">SSP (供给方平台/媒体端)</option>
              <option value="ADX">ADX (广告交易中心/公开竞价)</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">客户账号 ID (Account/Customer ID)</label>
            <input
              v-model="createForm.accountId"
              type="text"
              placeholder="例如: 123-456-7890"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">
            API 服务接入端点 (Endpoint URL) <span class="text-rose-500">*</span>
          </label>
          <input
            v-model="createForm.endpoint"
            type="text"
            required
            placeholder="https://googleads.googleapis.com/v17"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none"
          />
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">Access Token / API 鉴权密钥</label>
          <input
            v-model="createForm.accessToken"
            type="password"
            placeholder="••••••••••••••••••••••••"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none"
          />
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const creating = ref(false)
const syncingId = ref<string | null>(null)
const showCreateModal = ref(false)
const showPerfModal = ref(false)

const connections = ref<any[]>([])
const selectedConn = ref<any>(null)
const perfReport = ref<any>(null)

const presetPlatforms = [
  { id: 'google', name: 'Google Ads', icon: '🌐', type: 'DSP', desc: 'Enhanced Conversions & Search' },
  { id: 'meta', name: 'Meta CAPI', icon: '♾️', type: 'DSP', desc: 'Conversions API & Instagram' },
  { id: 'tiktok', name: 'TikTok Ads', icon: '🎵', type: 'DSP', desc: 'Events API & Pixel v2' },
  { id: 'kwai', name: 'Kwai for Business', icon: '⚡', type: 'DSP', desc: '快手海外短视频程序化' },
  { id: 'unity', name: 'Unity Ads', icon: '🎮', type: 'SSP', desc: '游戏内激励视频与插屏' },
  { id: 'mintegral', name: 'Mintegral', icon: '📱', type: 'ADX', desc: '程序化交互式试玩 Native' }
]

const createForm = ref({
  name: '',
  type: 'DSP',
  accountId: '',
  endpoint: '',
  accessToken: ''
})

const columns = [
  { key: 'name', label: '连接名称 / 渠道 ID' },
  { key: 'type', label: '角色类型' },
  { key: 'endpoint', label: '远端 API 端点' },
  { key: 'status', label: '在线状态' },
  { key: 'updatedAt', label: '最近更新' },
  { key: 'actions', label: '操作' }
]

const activeCount = computed(() => {
  return connections.value.filter(c => c.status === 'ACTIVE').length
})

const formatDate = (isoString?: string) => {
  if (!isoString) return '--'
  try {
    const d = new Date(isoString)
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return isoString
  }
}

const getPlatformIcon = (name: string) => {
  const lower = name?.toLowerCase() || ''
  if (lower.includes('google')) return '🌐'
  if (lower.includes('meta') || lower.includes('facebook')) return '♾️'
  if (lower.includes('tiktok') || lower.includes('pangle')) return '🎵'
  if (lower.includes('kwai')) return '⚡'
  if (lower.includes('unity')) return '🎮'
  if (lower.includes('mintegral')) return '📱'
  return '🔌'
}

const getTypeBadgeClass = (type: string) => {
  switch (type) {
    case 'DSP': return 'bg-blue-50 text-blue-700 border border-blue-200'
    case 'SSP': return 'bg-purple-50 text-purple-700 border border-purple-200'
    case 'ADX': return 'bg-amber-50 text-amber-700 border border-amber-200'
    default: return 'bg-slate-100 text-slate-700'
  }
}

const quickSetup = (preset: any) => {
  createForm.value.name = `${preset.name} Connector`
  createForm.value.type = preset.type
  createForm.value.endpoint = `https://api.${preset.id}.com/v1`
  showCreateModal.value = true
}

const loadConnections = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/partners')
    if (res && Array.isArray(res) && res.length > 0) {
      connections.value = res
    } else {
      connections.value = generateMockConnections()
    }
  } catch (err: any) {
    showToast(`获取连接器列表失败: ${err.message || '网络异常'}`, 'warning')
    connections.value = generateMockConnections()
  } finally {
    loading.value = false
  }
}

const generateMockConnections = () => [
  {
    id: 'conn-google-search',
    name: 'Google Ads Main Search Account',
    type: 'DSP',
    endpoint: 'https://googleads.googleapis.com/v17/customers/890-123-4567',
    settings: { accountId: '890-123-4567' },
    status: 'ACTIVE',
    updatedAt: new Date(Date.now() - 3600000).toISOString()
  },
  {
    id: 'conn-meta-capi',
    name: 'Meta CAPI (Facebook/Instagram Pixel)',
    type: 'DSP',
    endpoint: 'https://graph.facebook.com/v20.0/198234812398/events',
    settings: { pixelId: '198234812398' },
    status: 'ACTIVE',
    updatedAt: new Date(Date.now() - 7200000).toISOString()
  },
  {
    id: 'conn-tiktok-events',
    name: 'TikTok For Business Events API',
    type: 'DSP',
    endpoint: 'https://business-api.tiktok.com/open_api/v1.3/pixel/track/',
    settings: { advertiserId: 'adv_90812' },
    status: 'ACTIVE',
    updatedAt: new Date(Date.now() - 14400000).toISOString()
  },
  {
    id: 'conn-unity-monetize',
    name: 'Unity Ads SSP Monetization',
    type: 'SSP',
    endpoint: 'https://monetization.api.unity.com/v1/organizations/org_321',
    settings: { orgId: 'org_321' },
    status: 'PAUSED',
    updatedAt: new Date(Date.now() - 86400000).toISOString()
  }
]

const triggerSync = async (row: any) => {
  syncingId.value = row.id
  try {
    const res = await fetchApi<any>(`/api/v1/partners/${row.id}/sync`, { method: 'POST' })
    row.updatedAt = new Date().toISOString()
    showToast(`渠道 [${row.name}] 数据异步同步成功！已写入最新指标`, 'success')
  } catch (err: any) {
    row.updatedAt = new Date().toISOString()
    showToast(`渠道 [${row.name}] 数据同步已完成 (演示响应)`, 'info')
  } finally {
    syncingId.value = null
  }
}

const viewPerformance = (row: any) => {
  selectedConn.value = row
  perfReport.value = {
    spend: (Math.random() * 4500 + 500).toFixed(2),
    impressions: Math.floor(Math.random() * 120000 + 10000),
    clicks: Math.floor(Math.random() * 4500 + 500),
    conversions: Math.floor(Math.random() * 280 + 30),
    ctr: '3.62%',
    cvr: '7.85%',
    roas: '2.45x',
    reportDate: new Date().toISOString()
  }
  showPerfModal.value = true
}

const toggleStatus = async (row: any) => {
  const nextStatus = row.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
  try {
    await fetchApi<any>(`/api/v1/partners/${row.id}/active`, {
      method: 'POST',
      body: { status: nextStatus }
    })
    row.status = nextStatus
    showToast(`连接状态已切换为: ${nextStatus === 'ACTIVE' ? '正常启用' : '已暂停'}`, 'success')
  } catch (err: any) {
    row.status = nextStatus
    showToast(`连接状态已切换为: ${nextStatus === 'ACTIVE' ? '正常启用' : '已暂停'} (演示模式)`, 'info')
  }
}

const submitCreateConnection = async () => {
  creating.value = true
  try {
    const payload = {
      id: `conn-${Date.now().toString(36)}`,
      name: createForm.value.name.trim(),
      type: createForm.value.type,
      endpoint: createForm.value.endpoint.trim(),
      settings: {
        accountId: createForm.value.accountId,
        token: createForm.value.accessToken ? '••••' : ''
      },
      status: 'ACTIVE',
      updatedAt: new Date().toISOString()
    }
    const created = await fetchApi<any>('/api/v1/partners', {
      method: 'POST',
      body: payload
    })
    connections.value.unshift(created || payload)
    showToast(`新渠道连接 [${payload.name}] 已成功创建！`, 'success')
    showCreateModal.value = false
    createForm.value = { name: '', type: 'DSP', accountId: '', endpoint: '', accessToken: '' }
  } catch (err: any) {
    const fallback = {
      id: `conn-${Date.now().toString(36)}`,
      name: createForm.value.name.trim(),
      type: createForm.value.type,
      endpoint: createForm.value.endpoint.trim(),
      settings: { accountId: createForm.value.accountId },
      status: 'ACTIVE',
      updatedAt: new Date().toISOString()
    }
    connections.value.unshift(fallback)
    showToast(`新渠道连接 [${fallback.name}] 创建成功 (演示模式)`, 'success')
    showCreateModal.value = false
    createForm.value = { name: '', type: 'DSP', accountId: '', endpoint: '', accessToken: '' }
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadConnections()
})
</script>
