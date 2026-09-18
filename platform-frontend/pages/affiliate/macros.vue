<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">宏参数与平台映射管理 (Macro Registry)</h3>
        <p class="text-xs text-slate-500">维护本平台标准宏字典与各广告/流量平台宏写法对照，支持追踪链接双向渲染</p>
      </div>
      <div class="flex items-center gap-1 bg-slate-100 rounded-lg p-1">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="px-3 py-1.5 text-xs font-semibold rounded-md transition-colors"
          :class="activeTab === tab.key ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-500 hover:text-slate-700'"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- ================= Tab 1: 标准宏库 ================= -->
    <div v-show="activeTab === 'params'">
      <div class="flex justify-end mb-3">
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="openParamModal()"
        >
          <span>➕</span><span>新增标准宏</span>
        </button>
      </div>
      <CommonTable :columns="paramColumns" :data="params" :loading="paramLoading" search-placeholder="搜索宏键或名称...">
        <template #cell-macroKey="{ row }">
          <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-800 font-mono text-[11px]">{ {{ row.macroKey }} }</span>
        </template>
        <template #cell-category="{ row }">
          <span class="px-2 py-0.5 rounded text-[11px] font-semibold" :class="categoryClass(row.category)">{{ row.category }}</span>
        </template>
        <template #cell-status="{ row }">
          <StatusTag :status="row.status" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="px-2.5 py-1 text-xs font-medium rounded-md border border-slate-200 text-slate-700 hover:bg-slate-50"
              @click="openParamModal(row)"
            >
              编辑
            </button>
            <button
              type="button"
              class="px-2.5 py-1 text-xs font-medium rounded-md border border-red-200 text-red-600 hover:bg-red-50"
              @click="removeParam(row)"
            >
              删除
            </button>
          </div>
        </template>
      </CommonTable>
    </div>

    <!-- ================= Tab 2: 平台映射 ================= -->
    <div v-show="activeTab === 'mappings'" class="space-y-4">
      <div v-if="platforms.length" class="flex flex-wrap gap-2">
        <button
          v-for="p in platforms"
          :key="p.platformCode"
          type="button"
          class="px-3 py-1.5 rounded-lg border text-xs font-semibold transition-colors"
          :class="mappingFilter === p.platformCode ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'"
          @click="selectPlatform(p.platformCode)"
        >
          {{ p.platformName || p.platformCode }}
          <span class="ml-1 text-[10px] text-slate-400">({{ p.mappingCount }})</span>
        </button>
      </div>

      <div class="flex items-center justify-between">
        <span class="text-xs text-slate-500">
          {{ mappingFilter ? `当前平台: ${mappingFilter}` : '全部平台映射' }}
          <button v-if="mappingFilter" type="button" class="ml-2 text-brand-600 hover:underline" @click="selectPlatform('')">显示全部</button>
        </span>
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="openMappingModal()"
        >
          <span>➕</span><span>新增映射</span>
        </button>
      </div>

      <CommonTable :columns="mappingColumns" :data="mappings" :loading="mappingLoading" search-placeholder="搜索平台或宏...">
        <template #cell-platform="{ row }">
          <div class="flex flex-col">
            <span class="font-bold text-slate-900">{{ row.platformName || row.platformCode }}</span>
            <span class="text-[11px] text-slate-400 font-mono">{{ row.platformCode }}</span>
          </div>
        </template>
        <template #cell-standard="{ row }">
          <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-mono text-[11px]">{ {{ row.macroKey }} }</span>
        </template>
        <template #cell-token="{ row }">
          <span class="px-2 py-0.5 rounded bg-amber-50 text-amber-800 font-mono text-[11px] border border-amber-200">{{ row.platformMacroToken }}</span>
        </template>
        <template #cell-status="{ row }">
          <StatusTag :status="row.status" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="px-2.5 py-1 text-xs font-medium rounded-md border border-slate-200 text-slate-700 hover:bg-slate-50"
              @click="openMappingModal(row)"
            >
              编辑
            </button>
            <button
              type="button"
              class="px-2.5 py-1 text-xs font-medium rounded-md border border-red-200 text-red-600 hover:bg-red-50"
              @click="removeMapping(row)"
            >
              删除
            </button>
          </div>
        </template>
      </CommonTable>
    </div>

    <!-- ================= Tab 3: 链接转换工具 ================= -->
    <div v-show="activeTab === 'convert'" class="space-y-4">
      <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1">目标平台</label>
            <select v-model="convertForm.platformCode" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900">
              <option value="">选择平台...</option>
              <option v-for="p in platforms" :key="p.platformCode" :value="p.platformCode">{{ p.platformName || p.platformCode }}</option>
            </select>
          </div>
          <div class="md:col-span-2">
            <label class="block text-xs font-medium text-slate-700 mb-1">转换方向</label>
            <div class="flex gap-2">
              <button
                type="button"
                class="flex-1 px-3 py-2 rounded-lg border text-xs font-semibold transition-colors"
                :class="convertForm.direction === 'render' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'"
                @click="convertForm.direction = 'render'"
              >
                标准宏 → 平台宏（下发追踪/Postback 链接）
              </button>
              <button
                type="button"
                class="flex-1 px-3 py-2 rounded-lg border text-xs font-semibold transition-colors"
                :class="convertForm.direction === 'standardize' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'"
                @click="convertForm.direction = 'standardize'"
              >
                平台宏 → 标准宏（回收平台原生链接）
              </button>
            </div>
          </div>
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-700 mb-1">
            待转换链接 {{ convertForm.direction === 'render' ? '（标准写法，含 {click_id} 等占位符）' : '（平台原生写法，含其宏占位符）' }}
          </label>
          <textarea
            v-model="convertForm.url"
            rows="3"
            placeholder="https://advertiser.com/s2s?clickid={clickid}&amount={amount}&subid={sub1}"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs font-mono text-slate-900"
          ></textarea>
        </div>
        <div class="flex justify-end">
          <button
            type="button"
            :disabled="convertLoading"
            class="px-5 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 disabled:opacity-50 text-white shadow-sm transition-colors"
            @click="runConvert"
          >
            {{ convertLoading ? '转换中...' : '🔄 执行转换' }}
          </button>
        </div>
      </div>

      <div v-if="convertResult" class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
        <div>
          <div class="flex items-center justify-between mb-1">
            <label class="text-xs font-medium text-slate-700">转换结果</label>
            <CopyButton :text="convertResult.convertedUrl">复制链接</CopyButton>
          </div>
          <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-mono text-emerald-900 break-all">
            {{ convertResult.convertedUrl }}
          </div>
        </div>
        <div v-if="convertResult.replaced?.length">
          <label class="block text-xs font-medium text-slate-700 mb-1.5">已替换宏（{{ convertResult.replaced.length }} 处）</label>
          <div class="flex flex-wrap gap-2">
            <span v-for="(r, i) in convertResult.replaced" :key="i" class="px-2 py-1 rounded-md bg-slate-100 text-[11px] font-mono text-slate-700">
              {{ pairLabel(r) }}
            </span>
          </div>
        </div>
        <div v-if="convertResult.unmapped?.length">
          <label class="block text-xs font-medium text-amber-700 mb-1.5">
            ⚠️ 该平台未映射的标准宏（保留原样，请补充映射）
          </label>
          <div class="flex flex-wrap gap-2">
            <span v-for="u in convertResult.unmapped" :key="u" class="px-2 py-1 rounded-md bg-amber-50 border border-amber-200 text-[11px] font-mono text-amber-800">
              {{ braceLabel(u) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗: 标准宏 -->
    <ModalDialog
      :show="showParamModal"
      :title="paramForm.id ? '编辑标准宏' : '新增标准宏'"
      confirm-text="保存"
      @close="showParamModal = false"
      @confirm="saveParam"
    >
      <div class="space-y-4 text-xs">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">标准宏键 *（不带花括号）</label>
            <input v-model="paramForm.macroKey" type="text" placeholder="例如: click_id" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono" />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">中文名称</label>
            <input v-model="paramForm.displayName" type="text" placeholder="例如: 点击会话ID" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900" />
          </div>
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">用途说明</label>
          <input v-model="paramForm.description" type="text" placeholder="例如: 点击追踪会话唯一标识，归因与回传核心参数" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900" />
        </div>
        <div class="grid grid-cols-3 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">示例取值</label>
            <input v-model="paramForm.sampleValue" type="text" placeholder="c_8f3a2b1e" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono" />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">分类</label>
            <select v-model="paramForm.category" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="ATTRIBUTION">ATTRIBUTION (归因)</option>
              <option value="SUB_TRACKING">SUB_TRACKING (子渠道)</option>
              <option value="TRANSACTION">TRANSACTION (交易)</option>
              <option value="ENVIRONMENT">ENVIRONMENT (环境)</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">状态</label>
            <select v-model="paramForm.status" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="ACTIVE">ACTIVE (启用)</option>
              <option value="DISABLED">DISABLED (停用)</option>
            </select>
          </div>
        </div>
      </div>
    </ModalDialog>

    <!-- 弹窗: 平台映射 -->
    <ModalDialog
      :show="showMappingModal"
      :title="mappingForm.id ? '编辑平台宏映射' : '新增平台宏映射'"
      confirm-text="保存映射"
      @close="showMappingModal = false"
      @confirm="saveMapping"
    >
      <div class="space-y-4 text-xs">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">平台代码 *</label>
            <input v-model="mappingForm.platformCode" type="text" placeholder="例如: AWIN / SHAREASALE" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono uppercase" />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">平台名称</label>
            <input v-model="mappingForm.platformName" type="text" placeholder="例如: Awin 联盟" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">本平台标准宏 *</label>
            <select v-model="mappingForm.macroKey" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono">
              <option value="">选择标准宏...</option>
              <option v-for="p in params" :key="p.id" :value="p.macroKey">{{ p.macroKey }}{{ p.displayName ? ` (${p.displayName})` : '' }}</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">平台原生宏写法 *</label>
            <input v-model="mappingForm.platformMacroToken" type="text" placeholder="例如: {clickid} / [ssn] / __CLICKID__" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">备注</label>
            <input v-model="mappingForm.remark" type="text" placeholder="例如: Awin S2S 官方文档默认写法" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900" />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">状态</label>
            <select v-model="mappingForm.status" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="ACTIVE">ACTIVE (启用)</option>
              <option value="DISABLED">DISABLED (停用)</option>
            </select>
          </div>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import CopyButton from '~/components/common/CopyButton.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const tabs = [
  { key: 'params', label: '📚 标准宏库' },
  { key: 'mappings', label: '🔗 平台映射管理' },
  { key: 'convert', label: '🔄 链接转换工具' }
]
const activeTab = ref('params')

const params = ref<any[]>([])
const paramLoading = ref(false)
const showParamModal = ref(false)
const paramForm = ref<any>({ id: '', macroKey: '', displayName: '', description: '', sampleValue: '', category: 'ATTRIBUTION', status: 'ACTIVE' })

const mappings = ref<any[]>([])
const mappingLoading = ref(false)
const mappingFilter = ref('')
const platforms = ref<any[]>([])
const showMappingModal = ref(false)
const mappingForm = ref<any>({ id: '', platformCode: '', platformName: '', macroKey: '', platformMacroToken: '', remark: '', status: 'ACTIVE' })

const convertLoading = ref(false)
const convertForm = ref({ platformCode: '', direction: 'render', url: '' })
const convertResult = ref<any>(null)

const paramColumns = [
  { key: 'macroKey', label: '标准宏 {key}' },
  { key: 'displayName', label: '名称 / 说明' },
  { key: 'sampleValue', label: '示例取值' },
  { key: 'category', label: '分类' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const mappingColumns = [
  { key: 'platform', label: '平台' },
  { key: 'standard', label: '本平台标准宏' },
  { key: 'token', label: '平台原生宏写法' },
  { key: 'remark', label: '备注' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const categoryClass = (cat: string) =>
  ({
    ATTRIBUTION: 'bg-blue-50 text-blue-700',
    SUB_TRACKING: 'bg-purple-50 text-purple-700',
    TRANSACTION: 'bg-emerald-50 text-emerald-700',
    ENVIRONMENT: 'bg-slate-100 text-slate-600'
  }[cat] || 'bg-slate-100 text-slate-600')

const pairLabel = (r: { macroKey: string; token: string }) =>
  convertForm.value.direction === 'render'
    ? `{${r.macroKey}} → ${r.token}`
    : `${r.token} → {${r.macroKey}}`

const braceLabel = (u: string) => `{${u}}`

const loadParams = async () => {
  paramLoading.value = true
  try {
    params.value = await fetchApi<any[]>('/api/v1/affiliate/macros/params') || []
  } catch (err: any) {
    params.value = []
    showToast(`加载标准宏库失败：${err.message || err}`, 'error', 5000)
  } finally {
    paramLoading.value = false
  }
}

const loadPlatforms = async () => {
  try {
    platforms.value = await fetchApi<any[]>('/api/v1/affiliate/macros/platforms') || []
  } catch {
    platforms.value = []
  }
}

const loadMappings = async () => {
  mappingLoading.value = true
  try {
    const q = mappingFilter.value ? `?platformCode=${encodeURIComponent(mappingFilter.value)}` : ''
    mappings.value = await fetchApi<any[]>(`/api/v1/affiliate/macros/mappings${q}`) || []
  } catch (err: any) {
    mappings.value = []
    showToast(`加载平台映射失败：${err.message || err}`, 'error', 5000)
  } finally {
    mappingLoading.value = false
  }
}

const switchTab = (key: string) => {
  activeTab.value = key
  if (key === 'mappings') loadMappings()
}

const selectPlatform = (code: string) => {
  mappingFilter.value = code
  loadMappings()
}

const openParamModal = (row?: any) => {
  paramForm.value = row
    ? { ...row }
    : { id: '', macroKey: '', displayName: '', description: '', sampleValue: '', category: 'ATTRIBUTION', status: 'ACTIVE' }
  showParamModal.value = true
}

const saveParam = async () => {
  if (!paramForm.value.macroKey?.trim()) {
    showToast('请填写标准宏键', 'warning')
    return
  }
  try {
    await fetchApi('/api/v1/affiliate/macros/params', { method: 'POST', body: { ...paramForm.value } })
    showParamModal.value = false
    showToast('标准宏保存成功', 'success')
    await loadParams()
  } catch (err: any) {
    showToast(`标准宏保存失败：${err.message || err}`, 'error', 5000)
  }
}

const removeParam = async (row: any) => {
  if (!window.confirm(`确认删除标准宏 {${row.macroKey}} ？被映射引用的宏不可删除。`)) return
  try {
    await fetchApi(`/api/v1/affiliate/macros/params/${row.id}`, { method: 'DELETE' })
    showToast('标准宏已删除', 'success')
    await loadParams()
  } catch (err: any) {
    showToast(`删除失败：${err.message || err}`, 'error', 5000)
  }
}

const openMappingModal = (row?: any) => {
  mappingForm.value = row
    ? { ...row }
    : { id: '', platformCode: mappingFilter.value, platformName: '', macroKey: '', platformMacroToken: '', remark: '', status: 'ACTIVE' }
  showMappingModal.value = true
}

const saveMapping = async () => {
  const f = mappingForm.value
  if (!f.platformCode?.trim() || !f.macroKey?.trim() || !f.platformMacroToken?.trim()) {
    showToast('平台代码、标准宏与平台宏写法均为必填', 'warning')
    return
  }
  try {
    await fetchApi('/api/v1/affiliate/macros/mappings', { method: 'POST', body: { ...f } })
    showMappingModal.value = false
    showToast('平台宏映射保存成功', 'success')
    await Promise.all([loadMappings(), loadPlatforms()])
  } catch (err: any) {
    showToast(`映射保存失败：${err.message || err}`, 'error', 5000)
  }
}

const removeMapping = async (row: any) => {
  if (!window.confirm(`确认删除 ${row.platformCode} 的 {${row.macroKey}} → ${row.platformMacroToken} 映射？`)) return
  try {
    await fetchApi(`/api/v1/affiliate/macros/mappings/${row.id}`, { method: 'DELETE' })
    showToast('映射已删除', 'success')
    await Promise.all([loadMappings(), loadPlatforms()])
  } catch (err: any) {
    showToast(`删除失败：${err.message || err}`, 'error', 5000)
  }
}

const runConvert = async () => {
  if (!convertForm.value.platformCode) {
    showToast('请先选择目标平台', 'warning')
    return
  }
  if (!convertForm.value.url?.trim()) {
    showToast('请输入待转换链接', 'warning')
    return
  }
  convertLoading.value = true
  try {
    const endpoint = convertForm.value.direction === 'render' ? 'render' : 'standardize'
    convertResult.value = await fetchApi<any>(`/api/v1/affiliate/macros/${endpoint}`, {
      method: 'POST',
      body: { platformCode: convertForm.value.platformCode, url: convertForm.value.url }
    })
    showToast('链接转换完成', 'success')
  } catch (err: any) {
    convertResult.value = null
    showToast(`链接转换失败：${err.message || err}`, 'error', 5000)
  } finally {
    convertLoading.value = false
  }
}

onMounted(() => {
  loadParams()
  loadPlatforms()
  loadMappings()
})
</script>
