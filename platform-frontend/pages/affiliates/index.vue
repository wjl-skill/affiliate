<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">渠道客管理 (Affiliate Partners)</h3>
        <p class="text-xs text-slate-500">管理发布商渠道等级、S2S 下游回传 Postback 模板与账期结算门槛</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showCreateModal = true"
      >
        <span>➕</span>
        <span>添加渠道客</span>
      </button>
    </div>

    <!-- 数据表格 -->
    <CommonTable
      :columns="columns"
      :data="partners"
      :loading="loading"
      search-placeholder="搜索渠道客名称或 ID..."
    >
      <template #cell-name="{ row }">
        <div class="flex flex-col">
          <span class="font-bold text-slate-900">{{ row.name }}</span>
          <span class="text-[11px] text-slate-400 font-mono">ID: {{ row.id }}</span>
        </div>
      </template>

      <template #cell-tier="{ row }">
        <StatusTag :status="row.tier" />
      </template>

      <template #cell-postback="{ row }">
        <div class="max-w-xs truncate text-[11px] font-mono text-slate-600" :title="row.postbackUrlTemplate">
          {{ row.postbackUrlTemplate || '未配置' }}
        </div>
      </template>

      <template #cell-terms="{ row }">
        <div class="text-xs">
          <span class="font-semibold text-slate-800">{{ row.paymentTerm }}</span>
          <span class="text-slate-400 text-[10px] block">起提: ${{ row.minPayoutThreshold }}</span>
        </div>
      </template>

      <template #cell-status="{ row }">
        <StatusTag :status="row.status" />
      </template>

      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="px-2.5 py-1 text-xs font-medium rounded-md border border-slate-200 text-slate-700 hover:bg-slate-50"
            @click="openLinkGenerator(row)"
          >
            🔗 生成推广链接
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 弹窗 1: 添加渠道客 -->
    <ModalDialog
      :show="showCreateModal"
      title="添加合作渠道客 (New Affiliate Partner)"
      confirm-text="保存渠道客"
      @close="showCreateModal = false"
      @confirm="savePartner"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">渠道客 ID</label>
          <input
            v-model="createForm.id"
            type="text"
            required
            placeholder="例如: aff-apex-01"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">渠道客名称</label>
          <input
            v-model="createForm.name"
            type="text"
            required
            placeholder="例如: Apex Media Network"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">渠道等级评定</label>
            <select v-model="createForm.tier" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="STANDARD">STANDARD (标准)</option>
              <option value="SILVER">SILVER (白银)</option>
              <option value="GOLD">GOLD (黄金)</option>
              <option value="VIP">VIP (核心大户 - 阶梯加价)</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">约定付款账期</label>
            <select v-model="createForm.paymentTerm" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="NET_7">Net 7 (周结)</option>
              <option value="NET_15">Net 15 (半月结)</option>
              <option value="NET_30">Net 30 (月结)</option>
            </select>
          </div>
        </div>

        <!-- 使用 MacroInput 编辑 Postback 模板 -->
        <MacroInput
          v-model="createForm.postbackUrlTemplate"
          label="下游渠道回传 Postback URL 模板"
          placeholder="https://aff-postback.com/pb?click_id={click_id}&payout={payout}&txid={txid}"
        />
      </div>
    </ModalDialog>

    <!-- 弹窗 2: 专属推广链接生成器 -->
    <ModalDialog
      :show="showLinkModal"
      title="生成渠道专属推广跟踪链接"
      confirm-text="复制跟踪链接"
      hide-confirm
      @close="showLinkModal = false"
    >
      <div v-if="selectedPartner" class="space-y-4 text-xs">
        <div class="p-3 bg-slate-50 rounded-lg border border-slate-200">
          <span class="text-slate-500">当前渠道客:</span>
          <span class="font-bold text-slate-900 ml-1">{{ selectedPartner.name }} ({{ selectedPartner.id }})</span>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">选择推广目标 Offer ID</label>
          <input
            v-model="linkOfferId"
            type="text"
            placeholder="例如: off-101"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">子渠道 sub1 (广告系列)</label>
            <input
              v-model="linkSub1"
              type="text"
              placeholder="例如: facebook"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">子渠道 sub2 (素材编号)</label>
            <input
              v-model="linkSub2"
              type="text"
              placeholder="例如: cr01"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">生成的标准跟踪链接</label>
          <div class="flex items-center gap-2">
            <input
              :value="generatedTrackingUrl"
              readonly
              class="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-slate-800 font-mono text-[11px]"
            />
            <CopyButton :text="generatedTrackingUrl">复制链接</CopyButton>
          </div>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import CopyButton from '~/components/common/CopyButton.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import MacroInput from '~/components/common/MacroInput.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const showCreateModal = ref(false)
const showLinkModal = ref(false)
const partners = ref<any[]>([])

const selectedPartner = ref<any>(null)
const linkOfferId = ref('off-101')
const linkSub1 = ref('fb_ads')
const linkSub2 = ref('cr01')

const createForm = ref({
  id: 'aff-' + Math.floor(Math.random() * 900 + 100),
  tenantId: 'tenant-1',
  name: '',
  tier: 'VIP',
  postbackUrlTemplate: 'https://partner.com/postback?click_id={click_id}&payout={payout}&txid={txid}',
  paymentTerm: 'NET_15',
  minPayoutThreshold: 100.00,
  status: 'ACTIVE'
})

const columns = [
  { key: 'name', label: '渠道客 (Name / ID)' },
  { key: 'tier', label: '合作等级' },
  { key: 'postback', label: '下游 S2S Postback 模板' },
  { key: 'terms', label: '结算账期与起提额' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const generatedTrackingUrl = computed(() => {
  if (!selectedPartner.value) return ''
  return `http://localhost:8080/affiliate/click?offer_id=${linkOfferId.value}&aff_id=${selectedPartner.value.id}&sub1=${linkSub1.value}&sub2=${linkSub2.value}`
})

const loadPartners = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/affiliate/partners')
    partners.value = res
  } catch (err) {
    partners.value = [
      {
        id: 'aff-vip-888',
        name: 'Apex Growth Media',
        tier: 'VIP',
        postbackUrlTemplate: 'https://apex.com/pb?click_id={click_id}&payout={payout}&txid={txid}',
        paymentTerm: 'NET_15',
        minPayoutThreshold: 100.00,
        status: 'ACTIVE'
      },
      {
        id: 'aff-gold-777',
        name: 'ByteFlow Global',
        tier: 'GOLD',
        postbackUrlTemplate: 'https://byteflow.io/postback?cid={click_id}&amount={payout}',
        paymentTerm: 'NET_30',
        minPayoutThreshold: 200.00,
        status: 'ACTIVE'
      }
    ]
  } finally {
    loading.value = false
  }
}

const openLinkGenerator = (partner: any) => {
  selectedPartner.value = partner
  showLinkModal.value = true
}

const savePartner = async () => {
  try {
    await fetchApi('/api/v1/affiliate/partners', {
      method: 'POST',
      body: createForm.value
    })
  } catch (e) {}

  partners.value.unshift({ ...createForm.value })
  showCreateModal.value = false
  showToast('渠道客档案保存成功', 'success')
}

onMounted(() => {
  loadPartners()
})
</script>
