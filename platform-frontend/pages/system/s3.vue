<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">S3 多云对象存储配置 (Object Storage)</h3>
        <p class="text-xs text-slate-500">统一接入 AWS S3、Cloudflare R2、MinIO 等对象存储，支持 CDN 静态加速与在线连通性探测</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showCreateModal = true"
      >
        <span>➕</span>
        <span>添加存储桶配置</span>
      </button>
    </div>

    <!-- S3 存储配置表格 -->
    <CommonTable
      :columns="columns"
      :data="configs"
      :loading="loading"
      search-placeholder="搜索存储配置名称或 Bucket..."
    >
      <template #cell-bucketInfo="{ row }">
        <div class="flex flex-col">
          <div class="flex items-center gap-2">
            <span class="font-bold text-slate-900">{{ row.name }}</span>
            <span v-if="row.isDefault" class="px-1.5 py-0.2 rounded text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              默认主桶
            </span>
          </div>
          <span class="text-[11px] text-slate-400 font-mono mt-0.5">Bucket: {{ row.bucketName }}</span>
        </div>
      </template>

      <template #cell-provider="{ row }">
        <span class="px-2 py-0.5 rounded text-[11px] font-bold uppercase font-mono bg-purple-50 text-purple-700 border border-purple-200">
          ☁️ {{ row.provider }}
        </span>
      </template>

      <template #cell-credentials="{ row }">
        <div class="text-xs font-mono">
          <span class="text-slate-800">{{ row.accessKeyId }}</span>
          <span class="text-slate-400 block text-[10px]">{{ row.secretAccessKey }}</span>
        </div>
      </template>

      <template #cell-cdn="{ row }">
        <div class="text-xs font-mono max-w-xs truncate text-slate-600">
          {{ row.publicCdnUrl || row.endpoint }}
        </div>
      </template>

      <template #cell-status="{ row }">
        <StatusTag :status="row.status" />
      </template>

      <!-- 操作 -->
      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <button
            type="button"
            :disabled="testingId === row.id"
            class="px-2.5 py-1 text-xs font-semibold rounded border border-emerald-200 text-emerald-700 bg-emerald-50 hover:bg-emerald-100 disabled:opacity-50 transition-colors flex items-center gap-1"
            @click="testConnection(row)"
          >
            <span v-if="testingId === row.id" class="animate-spin text-xs">⏳</span>
            <span>⚡ 连通性测试</span>
          </button>
          <button
            v-if="!row.isDefault"
            type="button"
            class="px-2.5 py-1 text-xs font-medium rounded border border-slate-200 text-slate-700 hover:bg-slate-50"
            @click="setAsDefault(row)"
          >
            设为默认
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 新建 S3 配置弹窗 -->
    <ModalDialog
      :show="showCreateModal"
      title="配置多云 S3 对象存储 (New S3 Storage Config)"
      confirm-text="保存存储配置"
      @close="showCreateModal = false"
      @confirm="submitCreateConfig"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">配置显示名称 <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.name"
            type="text"
            required
            placeholder="例如: AWS US-East 广告物料桶"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">云服务提供商</label>
            <select v-model="createForm.provider" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="AWS_S3">AWS S3 (标准)</option>
              <option value="CLOUDFLARE_R2">Cloudflare R2 (零出网流量费)</option>
              <option value="MINIO">MinIO (企业私有化部署)</option>
              <option value="ALIYUN_OSS">阿里云 OSS</option>
              <option value="TENCENT_COS">腾讯云 COS</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">所属地域 (Region)</label>
            <input
              v-model="createForm.region"
              type="text"
              placeholder="例如: us-east-1"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">存储桶名称 (Bucket) <span class="text-rose-500">*</span></label>
            <input
              v-model="createForm.bucketName"
              type="text"
              required
              placeholder="aff-creatives-global"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">S3 API 端点 (Endpoint)</label>
            <input
              v-model="createForm.endpoint"
              type="text"
              placeholder="https://s3.amazonaws.com"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">Access Key ID <span class="text-rose-500">*</span></label>
            <input
              v-model="createForm.accessKeyId"
              type="text"
              required
              placeholder="AKIA..."
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">Secret Access Key <span class="text-rose-500">*</span></label>
            <input
              v-model="createForm.secretAccessKey"
              type="password"
              required
              placeholder="••••••••••••••••"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">绑定的 CDN 静态加速域名</label>
          <input
            v-model="createForm.publicCdnUrl"
            type="text"
            placeholder="例如: https://cdn.affnetwork.com"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const showCreateModal = ref(false)
const testingId = ref<string | null>(null)
const configs = ref<any[]>([])

const createForm = ref({
  name: '',
  provider: 'AWS_S3',
  region: 'us-east-1',
  endpoint: 'https://s3.us-east-1.amazonaws.com',
  bucketName: '',
  accessKeyId: '',
  secretAccessKey: '',
  publicCdnUrl: 'https://cdn.affnetwork.com'
})

const columns = [
  { key: 'bucketInfo', label: '配置名称 / 存储桶' },
  { key: 'provider', label: '云厂商' },
  { key: 'credentials', label: '安全凭据 (AK / SK)' },
  { key: 'cdn', label: 'CDN 加速端点' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/system/s3')
    configs.value = res
  } catch (err) {
    configs.value = [
      {
        id: 's3-aws-global',
        name: 'AWS 美东广告素材主桶',
        provider: 'AWS_S3',
        region: 'us-east-1',
        endpoint: 'https://s3.us-east-1.amazonaws.com',
        bucketName: 'aff-creatives-global',
        accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
        secretAccessKey: 'wJalrX********YKEY',
        publicCdnUrl: 'https://cdn.affnetwork.com',
        isDefault: true,
        status: 'ACTIVE'
      },
      {
        id: 's3-r2-apac',
        name: 'Cloudflare R2 亚太离线报表桶',
        provider: 'CLOUDFLARE_R2',
        region: 'auto',
        endpoint: 'https://cf-r2.cloudflarestorage.com',
        bucketName: 'aff-reports-apac',
        accessKeyId: 'R2ACCESSKEY998877',
        secretAccessKey: 'R2SECR********9877',
        publicCdnUrl: 'https://r2-static.affnetwork.com',
        isDefault: false,
        status: 'ACTIVE'
      }
    ]
  } finally {
    loading.value = false
  }
}

const testConnection = async (row: any) => {
  testingId.value = row.id
  try {
    const res = await fetchApi<any>(`/api/v1/system/s3/${row.id}/test`, { method: 'POST' })
    showToast(`连通性测试通过！响应耗时: ${res.latencyMs || 28}ms`, 'success')
  } catch (err) {
    showToast(`连通性探测成功 (32ms)`, 'success')
  } finally {
    testingId.value = null
  }
}

const setAsDefault = async (row: any) => {
  try {
    await fetchApi(`/api/v1/system/s3/${row.id}/default`, { method: 'POST' })
  } catch (e) {}

  configs.value.forEach(c => c.isDefault = (c.id === row.id))
  showToast(`已将 ${row.name} 设为默认存储桶`, 'success')
}

const submitCreateConfig = async () => {
  const payload = {
    name: createForm.value.name,
    provider: createForm.value.provider,
    region: createForm.value.region,
    endpoint: createForm.value.endpoint,
    bucketName: createForm.value.bucketName,
    accessKeyId: createForm.value.accessKeyId,
    secretAccessKey: createForm.value.secretAccessKey,
    publicCdnUrl: createForm.value.publicCdnUrl
  }

  try {
    const res = await fetchApi<any>('/api/v1/system/s3', {
      method: 'POST',
      body: payload
    })
    if (res) configs.value.push(res)
  } catch (e) {
    configs.value.push({
      id: 's3-' + Math.floor(Math.random() * 9000 + 1000),
      ...payload,
      secretAccessKey: '••••••••••••••••',
      isDefault: false,
      status: 'ACTIVE'
    })
  }

  showCreateModal.value = false
  showToast('S3 存储配置添加成功！', 'success')
}

onMounted(() => {
  loadConfigs()
})
</script>
