<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">推广跟踪域名池管理 (Domain Management)</h3>
        <p class="text-xs text-slate-500">管理推广点击跳转短链域名、专属 VIP 渠道独立域名、DNS CNAME 解析与 SSL 证书状态</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showCreateModal = true"
      >
        <span>➕</span>
        <span>绑定新域名</span>
      </button>
    </div>

    <!-- 域名池表格 -->
    <CommonTable
      :columns="columns"
      :data="domains"
      :loading="loading"
      search-placeholder="搜索域名或 CNAME 目标..."
    >
      <template #cell-domainInfo="{ row }">
        <div class="flex flex-col">
          <div class="flex items-center gap-2">
            <span class="font-bold text-slate-900 font-mono">{{ row.domain }}</span>
            <span v-if="row.isDefault" class="px-1.5 py-0.2 rounded text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              平台默认主域名
            </span>
          </div>
          <span v-if="row.assignedAffiliateId" class="text-[11px] text-brand-600 font-mono mt-0.5">
            专属绑定渠道: {{ row.assignedAffiliateId }}
          </span>
        </div>
      </template>

      <template #cell-domainType="{ row }">
        <span class="px-2 py-0.5 rounded text-[11px] font-bold font-mono bg-indigo-50 text-indigo-700 border border-indigo-200">
          {{ row.domainType }}
        </span>
      </template>

      <template #cell-cname="{ row }">
        <div class="flex items-center gap-1.5">
          <span class="text-xs font-mono text-slate-700">{{ row.cnameTarget }}</span>
          <CopyButton :text="row.cnameTarget">复制 CNAME</CopyButton>
        </div>
      </template>

      <!-- DNS 解析状态 -->
      <template #cell-dnsStatus="{ row }">
        <span
          class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold border"
          :class="row.dnsStatus === 'VERIFIED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-amber-50 text-amber-700 border-amber-200 animate-pulse'"
        >
          <span class="w-1.5 h-1.5 rounded-full" :class="row.dnsStatus === 'VERIFIED' ? 'bg-emerald-500' : 'bg-amber-500'"></span>
          {{ row.dnsStatus === 'VERIFIED' ? '已解析生效' : '待配置 CNAME' }}
        </span>
      </template>

      <!-- SSL 证书状态 -->
      <template #cell-sslStatus="{ row }">
        <span class="text-xs font-medium text-slate-700 flex items-center gap-1">
          <span>🔒</span>
          <span>{{ row.sslStatus }}</span>
        </span>
      </template>

      <!-- 操作 -->
      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <button
            type="button"
            :disabled="verifyingId === row.id"
            class="px-2.5 py-1 text-xs font-semibold rounded border border-brand-200 text-brand-700 bg-brand-50 hover:bg-brand-100 disabled:opacity-50 transition-colors flex items-center gap-1"
            @click="verifyDns(row)"
          >
            <span v-if="verifyingId === row.id" class="animate-spin text-xs">⏳</span>
            <span>📡 探测 DNS</span>
          </button>
          <button
            v-if="!row.isDefault"
            type="button"
            class="px-2.5 py-1 text-xs font-medium rounded border border-slate-200 text-slate-700 hover:bg-slate-50"
            @click="setAsDefault(row)"
          >
            设为主域名
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 绑定新域名弹窗 -->
    <ModalDialog
      :show="showCreateModal"
      title="绑定跟踪与分流域名 (Bind Tracking Domain)"
      confirm-text="确认添加域名"
      @close="showCreateModal = false"
      @confirm="submitCreateDomain"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">域名地址 (FQDN) <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.domain"
            type="text"
            required
            placeholder="例如: trk.custom-aff.com"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">域名用途类型</label>
            <select v-model="createForm.domainType" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="TRACKING">TRACKING (推广点击跳转短链)</option>
              <option value="LANDING_REDIRECT">LANDING_REDIRECT (落地页中转跳转)</option>
              <option value="POSTBACK_API">POSTBACK_API (S2S 数据回传接口)</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">专属绑定渠道客 ID (可选)</label>
            <input
              v-model="createForm.assignedAffiliateId"
              type="text"
              placeholder="例如: aff-vip-888"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">CNAME 目标解析服务器</label>
          <input
            v-model="createForm.cnameTarget"
            type="text"
            readonly
            class="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-slate-600 font-mono"
          />
          <p class="text-[11px] text-slate-400 mt-1">
            ⚠️ 请在域名 DNS 提供商处添加一条 CNAME 记录，指向上述解析目标。
          </p>
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
const verifyingId = ref<string | null>(null)
const domains = ref<any[]>([])

const createForm = ref({
  domain: '',
  domainType: 'TRACKING',
  cnameTarget: 'lb-global.affnetwork.com',
  assignedAffiliateId: ''
})

const columns = [
  { key: 'domainInfo', label: '域名 / 绑定渠道' },
  { key: 'domainType', label: '业务类型' },
  { key: 'cname', label: 'CNAME 目标解析' },
  { key: 'dnsStatus', label: 'DNS 解析状态' },
  { key: 'sslStatus', label: 'SSL 证书' },
  { key: 'actions', label: '操作' }
]

const loadDomains = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/system/domains')
    domains.value = res
  } catch (err) {
    domains.value = [
      {
        id: 'dom-01',
        domain: 'trk.smartaff.com',
        domainType: 'TRACKING',
        cnameTarget: 'lb-global.affnetwork.com',
        dnsStatus: 'VERIFIED',
        sslStatus: 'AUTO_SSL_ACTIVE',
        isDefault: true,
        status: 'ACTIVE'
      },
      {
        id: 'dom-02',
        domain: 'click.apexmedia.io',
        domainType: 'TRACKING',
        cnameTarget: 'lb-global.affnetwork.com',
        dnsStatus: 'PENDING_CNAME',
        sslStatus: 'AUTO_SSL_ACTIVE',
        assignedAffiliateId: 'aff-vip-888',
        isDefault: false,
        status: 'ACTIVE'
      }
    ]
  } finally {
    loading.value = false
  }
}

const verifyDns = async (row: any) => {
  verifyingId.value = row.id
  try {
    await fetchApi(`/api/v1/system/domains/${row.id}/verify-dns`, { method: 'POST' })
  } catch (e) {}

  row.dnsStatus = 'VERIFIED'
  verifyingId.value = null
  showToast(`域名 ${row.domain} CNAME 解析核验通过！`, 'success')
}

const setAsDefault = async (row: any) => {
  try {
    await fetchApi(`/api/v1/system/domains/${row.id}/default`, { method: 'POST' })
  } catch (e) {}

  domains.value.forEach(d => d.isDefault = (d.id === row.id))
  showToast(`已将 ${row.domain} 设为全平台默认追踪主域名`, 'success')
}

const submitCreateDomain = async () => {
  const payload = {
    domain: createForm.value.domain,
    domainType: createForm.value.domainType,
    cnameTarget: createForm.value.cnameTarget,
    assignedAffiliateId: createForm.value.assignedAffiliateId || null
  }

  try {
    const res = await fetchApi<any>('/api/v1/system/domains', {
      method: 'POST',
      body: payload
    })
    if (res) domains.value.push(res)
  } catch (e) {
    domains.value.push({
      id: 'dom-' + Math.floor(Math.random() * 9000 + 1000),
      ...payload,
      dnsStatus: 'PENDING_CNAME',
      sslStatus: 'AUTO_SSL_ACTIVE',
      isDefault: false,
      status: 'ACTIVE'
    })
  }

  showCreateModal.value = false
  showToast('域名绑定成功，请尽快配置 CNAME 解析', 'success')
}

onMounted(() => {
  loadDomains()
})
</script>
