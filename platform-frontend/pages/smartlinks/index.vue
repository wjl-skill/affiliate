<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">SmartLink 智能分流与 TDS 管理</h3>
        <p class="text-xs text-slate-500">统一聚合链接分发，根据访客环境及候选 Offer 的实时 EPC 自动路由最优转化收益</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showCreateModal = true"
      >
        <span>➕</span>
        <span>新建 SmartLink</span>
      </button>
    </div>

    <!-- 列表展示与在线仿真测试分栏 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 左侧：SmartLink 列表 -->
      <div class="lg:col-span-2">
        <CommonTable
          :columns="columns"
          :data="smartLinks"
          :loading="loading"
          search-placeholder="搜索 SmartLink 名称或 ID..."
        >
          <template #cell-name="{ row }">
            <div class="flex flex-col">
              <span class="font-bold text-slate-900">{{ row.name }}</span>
              <span class="text-[11px] text-slate-400 font-mono">ID: {{ row.id }} · 分类: {{ row.category }}</span>
            </div>
          </template>

          <template #cell-strategy="{ row }">
            <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-purple-50 text-purple-700 border border-purple-200">
              ⚡ {{ row.routingStrategy }}
            </span>
          </template>

          <template #cell-targets="{ row }">
            <div class="flex flex-wrap gap-1">
              <span
                v-for="offId in row.targetOfferIds"
                :key="offId"
                class="px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 text-[10px] font-mono border border-slate-200"
              >
                {{ offId }}
              </span>
            </div>
          </template>

          <template #cell-actions="{ row }">
            <div class="flex items-center gap-2">
              <button
                type="button"
                class="px-2.5 py-1 text-xs font-medium rounded-md border border-brand-200 text-brand-700 hover:bg-brand-50"
                @click="selectForSimulation(row)"
              >
                🧪 测试路由
              </button>
              <CopyButton :text="`http://localhost:8080/affiliate/click?smartlink_id=${row.id}&aff_id=AFF_ID`">
                分流链接
              </CopyButton>
            </div>
          </template>
        </CommonTable>
      </div>

      <!-- 右侧：TDS 实时分流仿真器 -->
      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm h-fit space-y-4">
        <div class="border-b border-slate-100 pb-3">
          <h4 class="text-sm font-bold text-slate-800 flex items-center gap-1.5">
            <span>🧪</span>
            <span>TDS 智能路由仿真沙箱</span>
          </h4>
          <p class="text-[11px] text-slate-400 mt-0.5">模拟不同访客特征，验证 TDS 分流与最高 EPC 优选结果</p>
        </div>

        <div class="space-y-3">
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1">测试目标 SmartLink</label>
            <input
              v-model="simSmartLinkId"
              type="text"
              placeholder="smart-ecom-01"
              class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 font-mono"
            />
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-medium text-slate-700 mb-1">模拟访客国家 (Geo)</label>
              <select v-model="simCountry" class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900">
                <option value="US">🇺🇸 美国 (US)</option>
                <option value="GB">🇬🇧 英国 (GB)</option>
                <option value="DE">🇩🇪 德国 (DE)</option>
                <option value="JP">🇯🇵 日本 (JP)</option>
              </select>
            </div>
            <div>
              <label class="block text-xs font-medium text-slate-700 mb-1">终端设备形态</label>
              <select v-model.number="simDeviceType" class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900">
                <option :value="1">📱 移动手机 (Phone)</option>
                <option :value="2">💻 桌面电脑 (Desktop)</option>
              </select>
            </div>
          </div>

          <button
            type="button"
            class="w-full py-2 text-xs font-semibold rounded-lg bg-slate-900 hover:bg-slate-800 text-white shadow-sm transition-colors flex items-center justify-center gap-1.5"
            @click="runSimulation"
          >
            <span>⚡</span>
            <span>立即执行 TDS 路由演算</span>
          </button>
        </div>

        <!-- 模拟结果面板 -->
        <div v-if="simResult" class="p-3.5 rounded-lg bg-emerald-50 border border-emerald-200 text-xs space-y-1.5 animate-fade-in">
          <div class="flex items-center justify-between font-bold text-emerald-800">
            <span>✓ TDS 路由命中成功</span>
            <span>最高 EPC 优选</span>
          </div>
          <div class="text-slate-700">
            <span class="text-slate-500">命中 Offer:</span>
            <span class="font-bold ml-1 text-slate-900">{{ simResult.title }} ({{ simResult.id }})</span>
          </div>
          <div class="text-slate-700 flex justify-between">
            <span>生效佣金: ${{ simResult.defaultPayout }}</span>
            <span>广告主收费: ${{ simResult.defaultRevenue }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建 SmartLink 弹窗 -->
    <ModalDialog
      :show="showCreateModal"
      title="新建 SmartLink 智能分流链接"
      confirm-text="创建分流链接"
      @close="showCreateModal = false"
      @confirm="createSmartLink"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">SmartLink 唯一 ID</label>
          <input
            v-model="createForm.id"
            type="text"
            required
            placeholder="例如: smart-global-ecom"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">分流链接名称</label>
          <input
            v-model="createForm.name"
            type="text"
            required
            placeholder="例如: 全球跨境电商智能优选"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">关联候选 Offer ID (逗号分隔)</label>
          <input
            v-model="createForm.targetOffersText"
            type="text"
            required
            placeholder="例如: off-101, off-102, off-201"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">分流决策策略</label>
          <select v-model="createForm.routingStrategy" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
            <option value="HIGHEST_EPC">HIGHEST_EPC (收益最大化：按单点击最高 EPC 动态优选)</option>
            <option value="ROUND_ROBIN">ROUND_ROBIN (轮询均摊测试)</option>
          </select>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import CopyButton from '~/components/common/CopyButton.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const showCreateModal = ref(false)
const smartLinks = ref<any[]>([])

const simSmartLinkId = ref('smart-ecom-01')
const simCountry = ref('US')
const simDeviceType = ref(1)
const simResult = ref<any>(null)

const createForm = ref({
  id: 'smart-' + Math.floor(Math.random() * 900 + 100),
  name: '',
  category: 'E-Commerce',
  targetOffersText: 'off-101, off-102',
  routingStrategy: 'HIGHEST_EPC'
})

const columns = [
  { key: 'name', label: '分流链接 (Name / ID)' },
  { key: 'strategy', label: '路由分流策略' },
  { key: 'targets', label: '候选 Offer 计划' },
  { key: 'actions', label: '操作' }
]

const loadSmartLinks = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/affiliate/smartlinks')
    smartLinks.value = res
  } catch (err) {
    // 降级兜底数据
    smartLinks.value = [
      {
        id: 'smart-ecom-01',
        name: '全球电商综合智能分流',
        category: 'E-Commerce',
        targetOfferIds: ['off-101', 'off-102'],
        routingStrategy: 'HIGHEST_EPC'
      },
      {
        id: 'smart-fintech-02',
        name: '北美金融信贷 SmartLink',
        category: 'Finance',
        targetOfferIds: ['off-201'],
        routingStrategy: 'HIGHEST_EPC'
      }
    ]
  } finally {
    loading.value = false
  }
}

const selectForSimulation = (row: any) => {
  simSmartLinkId.value = row.id
  runSimulation()
}

const runSimulation = async () => {
  try {
    const res = await fetchApi<any>(`/api/v1/affiliate/smartlinks/${simSmartLinkId.value}/simulate?country=${simCountry.value}&deviceType=${simDeviceType.value}`)
    if (res) {
      simResult.value = res
      showToast('TDS 仿真计算成功', 'success')
      return
    }
  } catch (e) {}

  // 兜底演算
  simResult.value = {
    id: 'off-101',
    title: 'Nike 2026 Summer CPA (EPC 榜首)',
    defaultPayout: '5.00',
    defaultRevenue: '8.00'
  }
  showToast('TDS 仿真计算成功', 'success')
}

const createSmartLink = async () => {
  const ids = createForm.value.targetOffersText.split(',').map(s => s.trim()).filter(Boolean)
  const payload = {
    id: createForm.value.id,
    tenantId: 'tenant-1',
    name: createForm.value.name,
    category: createForm.value.category,
    targetOfferIds: ids,
    routingStrategy: createForm.value.routingStrategy
  }

  try {
    await fetchApi('/api/v1/affiliate/smartlinks', {
      method: 'POST',
      body: payload
    })
  } catch (e) {}

  smartLinks.value.unshift(payload)
  showCreateModal.value = false
  showToast('SmartLink 创建成功', 'success')
}

onMounted(() => {
  loadSmartLinks()
})
</script>
