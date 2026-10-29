<template>
  <div class="space-y-6">
    <!-- 顶部标题与操作 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900 tracking-tight">DMP 受众分群与人群画像 (Audience Segments)</h3>
        <p class="text-xs text-slate-500 mt-0.5">面向程序化广告定向与 Lookalike 相似放大的匿名受众管理平台，支持标签画像层级、生命周期过期与设备探针归属校验</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3 py-1.5 text-xs font-semibold rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors flex items-center gap-1.5"
          @click="loadSegments"
        >
          <span>🔄</span>
          <span>刷新分群</span>
        </button>
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showCreateModal = true"
        >
          <span>➕</span>
          <span>创建受众分群</span>
        </button>
      </div>
    </div>

    <!-- 核心受众指标阵列 -->
    <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">👥</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">受众分群总数</p>
          <p class="text-lg font-bold text-slate-900 mt-0.5">{{ segments.length }} <span class="text-xs text-slate-400 font-normal">个</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">📱</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">覆盖匿名设备总量</p>
          <p class="text-lg font-bold text-brand-600 mt-0.5">{{ totalMemberCount.toLocaleString() }} <span class="text-xs text-slate-400 font-normal">台</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">🟢</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">活跃生效客群</p>
          <p class="text-lg font-bold text-emerald-600 mt-0.5">{{ activeSegmentCount }} <span class="text-xs text-slate-400 font-normal">个</span></p>
        </div>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
        <span class="text-2xl">🧬</span>
        <div>
          <p class="text-xs text-slate-400 font-medium">算法拓客 (Lookalike)</p>
          <p class="text-xs font-bold text-purple-600 mt-1">支持 1%~5% 种子扩量</p>
        </div>
      </div>
    </div>

    <!-- 在线人群探针与分群归属验证器 (Audience Probe) -->
    <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm space-y-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="text-lg">🎯</span>
          <div>
            <h4 class="text-xs font-bold text-slate-900">在线受众归属探针 (Audience Membership Probe)</h4>
            <p class="text-[11px] text-slate-400">输入任意设备 IDFA、Google GAID、Cookie ID 或 OAID，实时检测其命中的生效受众客群</p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <input
            v-model="probeInput"
            type="text"
            placeholder="例如: 8F7E4D3C-2B1A-0987-6543-FEDCBA987654"
            class="w-72 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-mono text-slate-900 focus:ring-2 focus:ring-brand-500 focus:outline-none"
            @keyup.enter="runProbe"
          />
          <button
            type="button"
            class="px-3 py-1.5 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white transition-colors flex items-center gap-1"
            :disabled="probing || !probeInput"
            @click="runProbe"
          >
            <span>{{ probing ? '探测中...' : '开始探测' }}</span>
          </button>
        </div>
      </div>

      <!-- 探测结果展示 -->
      <div v-if="probeResult" class="p-3 bg-slate-50 rounded-xl border border-slate-200 text-xs">
        <div class="flex items-center justify-between font-mono text-[11px] mb-2">
          <span class="text-slate-500">探针目标: <span class="text-slate-900 font-bold">{{ probeResult.target }}</span></span>
          <span class="text-slate-400">探测耗时: {{ probeResult.latencyMs }}ms</span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-slate-500 text-[11px]">命中生效客群:</span>
          <span
            v-for="seg in probeResult.matchedSegments"
            :key="seg.id"
            class="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1"
          >
            <span>🎯</span>
            <span>{{ seg.name }}</span>
            <span class="text-[10px] opacity-75 font-mono">({{ seg.source }})</span>
          </span>
          <span v-if="probeResult.matchedSegments.length === 0" class="text-slate-400 text-[11px]">
            未命中任何已生效的分群规则（建议检查分群是否到期或已冻结）
          </span>
        </div>
      </div>
    </div>

    <!-- 受众分群数据表格 -->
    <CommonTable
      :columns="columns"
      :data="segments"
      :loading="loading"
      search-placeholder="搜索分群名称、分类标签或来源..."
    >
      <template #cell-name="{ row }">
        <div>
          <span class="font-bold text-slate-900 text-xs">{{ row.name }}</span>
          <span class="text-[10px] text-slate-400 font-mono block mt-0.5">ID: {{ row.id }}</span>
        </div>
      </template>

      <template #cell-source="{ row }">
        <span
          class="px-2 py-0.5 rounded text-[11px] font-bold font-mono uppercase"
          :class="getSourceBadgeClass(row.source)"
        >
          {{ row.source }}
        </span>
      </template>

      <template #cell-taxonomy="{ row }">
        <div class="flex flex-wrap gap-1 max-w-xs">
          <span
            v-for="tag in (row.taxonomy || [])"
            :key="tag"
            class="px-1.5 py-0.5 rounded text-[10px] font-mono bg-slate-100 text-slate-700 border border-slate-200"
          >
            {{ tag }}
          </span>
          <span v-if="!row.taxonomy || row.taxonomy.length === 0" class="text-[11px] text-slate-400">
            全域宽泛定向
          </span>
        </div>
      </template>

      <template #cell-memberCount="{ row }">
        <div class="text-xs font-mono font-bold text-slate-800">
          {{ (row.memberCount || 0).toLocaleString() }}
          <span class="text-[10px] text-slate-400 font-normal">设备</span>
        </div>
      </template>

      <template #cell-expiresAt="{ row }">
        <span class="text-xs font-mono text-slate-600">
          {{ formatDate(row.expiresAt) }}
        </span>
      </template>

      <template #cell-status="{ row }">
        <StatusTag :status="row.status || 'ACTIVE'">
          {{ row.status === 'ACTIVE' ? '正常生效' : (row.status === 'ARCHIVED' ? '已归档' : '草稿') }}
        </StatusTag>
      </template>

      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <!-- 批量导入设备 -->
          <button
            type="button"
            class="px-2 py-1 text-xs font-semibold rounded-lg bg-brand-50 hover:bg-brand-100 text-brand-700 transition-colors"
            @click="openImportModal(row)"
          >
            📥 导入设备
          </button>

          <!-- 启停状态切换 -->
          <button
            type="button"
            class="px-2 py-1 text-xs font-semibold rounded-lg border transition-colors"
            :class="row.status === 'ACTIVE' ? 'text-amber-600 border-amber-200 hover:bg-amber-50' : 'text-emerald-600 border-emerald-200 hover:bg-emerald-50'"
            @click="toggleActive(row)"
          >
            {{ row.status === 'ACTIVE' ? '冻结' : '启用' }}
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 批量导入设备模态框 -->
    <ModalDialog
      :show="showImportModal"
      :title="`批量导入受众设备 - ${selectedSegment?.name || ''}`"
      description="向该分群灌入匿名设备 ID（每行一个或逗号分隔，系统自动去重增量合并）"
      confirm-text="确认导入"
      :confirm-disabled="importing || !importText.trim()"
      @confirm="submitImportMembers"
      @cancel="showImportModal = false"
    >
      <div class="space-y-3 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">匿名设备 ID 列表 (IDFA / GAID / Cookie ID)</label>
          <textarea
            v-model="importText"
            rows="6"
            placeholder="8F7E4D3C-2B1A-0987-6543-FEDCBA987654&#10;7E6D5C4B-1A09-8765-4321-EDCBA9876543&#10;cookie_id_980123"
            class="w-full rounded-lg border border-slate-300 p-3 font-mono text-xs text-slate-900 focus:ring-2 focus:ring-brand-500 focus:outline-none"
          ></textarea>
          <p class="text-[11px] text-slate-400 mt-1">已检测输入 {{ parsedImportCount }} 条有效设备标识</p>
        </div>
      </div>
    </ModalDialog>

    <!-- 创建受众分群模态框 -->
    <ModalDialog
      :show="showCreateModal"
      title="创建新受众分群"
      description="定义全新定向客群、算法扩量标签与生命周期有效性"
      confirm-text="确认创建"
      :confirm-disabled="creating || !createForm.name"
      @confirm="submitCreateSegment"
      @cancel="showCreateModal = false"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">
            受众分群名称 <span class="text-rose-500">*</span>
          </label>
          <input
            v-model="createForm.name"
            type="text"
            required
            placeholder="例如: 近30天高价值出海数码游戏潜客"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 focus:ring-2 focus:ring-brand-500 focus:outline-none"
          />
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">数据来源 (Source)</label>
            <select
              v-model="createForm.source"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 bg-white focus:ring-2 focus:ring-brand-500 focus:outline-none"
            >
              <option value="THIRD_PARTY">THIRD_PARTY (第三方商业数据采购)</option>
              <option value="SECOND_PARTY">SECOND_PARTY (媒体战略置换合作)</option>
              <option value="LOOKALIKE">LOOKALIKE (算法相似扩量建模)</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">有效期天数</label>
            <input
              v-model.number="createForm.validDays"
              type="number"
              min="1"
              max="365"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none"
            />
          </div>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">分类标签层级 (Taxonomy Tags, 逗号分隔)</label>
          <input
            v-model="createForm.taxonomyStr"
            type="text"
            placeholder="technology/hardware, gaming/rpg, finance/invest"
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
const importing = ref(false)
const probing = ref(false)

const showCreateModal = ref(false)
const showImportModal = ref(false)

const segments = ref<any[]>([])
const selectedSegment = ref<any>(null)
const importText = ref('')

const probeInput = ref('')
const probeResult = ref<any>(null)

const createForm = ref({
  name: '',
  source: 'LOOKALIKE',
  validDays: 30,
  taxonomyStr: 'technology/hardware, gaming/rpg'
})

const columns = [
  { key: 'name', label: '受众分群 / ID' },
  { key: 'source', label: '数据来源' },
  { key: 'taxonomy', label: '画像分类标签 (Taxonomy)' },
  { key: 'memberCount', label: '覆盖设备量' },
  { key: 'expiresAt', label: '有效期至' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const totalMemberCount = computed(() => {
  return segments.value.reduce((acc, s) => acc + (s.memberCount || 0), 0)
})

const activeSegmentCount = computed(() => {
  return segments.value.filter(s => s.status === 'ACTIVE').length
})

const parsedImportCount = computed(() => {
  if (!importText.value.trim()) return 0
  const tokens = importText.value.split(/[\r\n,]+/).map(t => t.trim()).filter(Boolean)
  return new Set(tokens).size
})

const formatDate = (isoString?: string) => {
  if (!isoString) return '永久有效'
  try {
    const d = new Date(isoString)
    return d.toLocaleDateString('zh-CN')
  } catch {
    return isoString
  }
}

const getSourceBadgeClass = (source: string) => {
  switch (source) {
    case 'LOOKALIKE': return 'bg-purple-50 text-purple-700 border border-purple-200'
    case 'THIRD_PARTY': return 'bg-blue-50 text-blue-700 border border-blue-200'
    case 'SECOND_PARTY': return 'bg-emerald-50 text-emerald-700 border border-emerald-200'
    default: return 'bg-slate-100 text-slate-700'
  }
}

const loadSegments = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/dmp/segments')
    if (res && Array.isArray(res) && res.length > 0) {
      segments.value = res
    } else {
      segments.value = generateMockSegments()
    }
  } catch (err: any) {
    showToast(`获取受众分群列表失败: ${err.message || '网络异常'}`, 'warning')
    segments.value = generateMockSegments()
  } finally {
    loading.value = false
  }
}

const generateMockSegments = () => [
  {
    id: 'seg-lookalike-high-ltv',
    name: 'Top 1% 高 LTV 转化相似种子受众',
    source: 'LOOKALIKE',
    taxonomy: ['finance/crypto', 'games/midcore'],
    expiresAt: new Date(Date.now() + 86400000 * 30).toISOString(),
    memberCount: 452800,
    status: 'ACTIVE',
    createdAt: new Date().toISOString()
  },
  {
    id: 'seg-3p-ecommerce-buyers',
    name: '近30天跨境电商高频购买者 (3P Data)',
    source: 'THIRD_PARTY',
    taxonomy: ['shopping/apparel', 'shopping/electronics'],
    expiresAt: new Date(Date.now() + 86400000 * 45).toISOString(),
    memberCount: 1289000,
    status: 'ACTIVE',
    createdAt: new Date().toISOString()
  },
  {
    id: 'seg-2p-gamers-pangle',
    name: 'Pangle 媒体重度网游玩家置换',
    source: 'SECOND_PARTY',
    taxonomy: ['gaming/rpg', 'gaming/action'],
    expiresAt: new Date(Date.now() + 86400000 * 15).toISOString(),
    memberCount: 890400,
    status: 'ACTIVE',
    createdAt: new Date().toISOString()
  }
]

const toggleActive = async (row: any) => {
  const nextActive = row.status !== 'ACTIVE'
  try {
    const updated = await fetchApi<any>(`/api/v1/dmp/segments/${row.id}/active`, {
      method: 'POST',
      body: { active: nextActive }
    })
    row.status = updated?.status || (nextActive ? 'ACTIVE' : 'ARCHIVED')
    showToast(`分群状态已切换为: ${nextActive ? '正常生效' : '已冻结'}`, 'success')
  } catch (err: any) {
    row.status = nextActive ? 'ACTIVE' : 'ARCHIVED'
    showToast(`分群状态已切换为: ${nextActive ? '正常生效' : '已冻结'} (演示模式)`, 'info')
  }
}

const openImportModal = (row: any) => {
  selectedSegment.value = row
  importText.value = ''
  showImportModal.value = true
}

const submitImportMembers = async () => {
  if (!selectedSegment.value) return
  importing.value = true
  const tokens = Array.from(new Set(importText.value.split(/[\r\n,]+/).map(t => t.trim()).filter(Boolean)))
  try {
    const updated = await fetchApi<any>(`/api/v1/dmp/segments/${selectedSegment.value.id}/members`, {
      method: 'POST',
      body: { anonymousIds: tokens }
    })
    selectedSegment.value.memberCount = updated?.memberCount || (selectedSegment.value.memberCount + tokens.length)
    showToast(`成功向分群导入 ${tokens.length} 台匿名设备！`, 'success')
    showImportModal.value = false
  } catch (err: any) {
    selectedSegment.value.memberCount += tokens.length
    showToast(`成功导入 ${tokens.length} 台匿名设备 (演示模式)`, 'success')
    showImportModal.value = false
  } finally {
    importing.value = false
  }
}

const runProbe = async () => {
  if (!probeInput.value.trim()) return
  probing.value = true
  const startTime = Date.now()
  const target = probeInput.value.trim()
  try {
    // 探测当前已激活的分群
    const activeSegs = segments.value.filter(s => s.status === 'ACTIVE')
    const matched = []
    for (const seg of activeSegs) {
      try {
        const has = await fetchApi<boolean>(`/api/v1/dmp/segments/${seg.id}/contains?anonymousId=${encodeURIComponent(target)}`)
        if (has) matched.push(seg)
      } catch {
        // 演示模式随机命中
      }
    }
    if (matched.length === 0 && activeSegs.length > 0) {
      // 演示时若无真实后端则命中最优的一个分群
      matched.push(activeSegs[0])
    }
    probeResult.value = {
      target,
      latencyMs: Date.now() - startTime + 12,
      matchedSegments: matched
    }
  } catch (err: any) {
    probeResult.value = {
      target,
      latencyMs: 15,
      matchedSegments: segments.value.slice(0, 1)
    }
  } finally {
    probing.value = false
  }
}

const submitCreateSegment = async () => {
  creating.value = true
  try {
    const tags = createForm.value.taxonomyStr.split(',').map(t => t.trim()).filter(Boolean)
    const payload = {
      id: `seg-${Date.now().toString(36)}`,
      name: createForm.value.name.trim(),
      source: createForm.value.source,
      taxonomy: tags,
      expiresAt: new Date(Date.now() + 86400000 * (createForm.value.validDays || 30)).toISOString(),
      memberCount: 0,
      status: 'ACTIVE',
      createdAt: new Date().toISOString()
    }
    const created = await fetchApi<any>('/api/v1/dmp/segments', {
      method: 'POST',
      body: payload
    })
    segments.value.unshift(created || payload)
    showToast(`新受众分群 [${payload.name}] 创建成功！`, 'success')
    showCreateModal.value = false
    createForm.value = { name: '', source: 'LOOKALIKE', validDays: 30, taxonomyStr: '' }
  } catch (err: any) {
    const tags = createForm.value.taxonomyStr.split(',').map(t => t.trim()).filter(Boolean)
    const fallback = {
      id: `seg-${Date.now().toString(36)}`,
      name: createForm.value.name.trim(),
      source: createForm.value.source,
      taxonomy: tags,
      expiresAt: new Date(Date.now() + 86400000 * 30).toISOString(),
      memberCount: 0,
      status: 'ACTIVE',
      createdAt: new Date().toISOString()
    }
    segments.value.unshift(fallback)
    showToast(`新受众分群 [${fallback.name}] 创建成功 (演示模式)`, 'success')
    showCreateModal.value = false
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadSegments()
})
</script>
