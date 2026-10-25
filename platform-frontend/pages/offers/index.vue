<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">Offer 推广计划管理与智能流量补量中心</h3>
        <p class="text-xs text-slate-500">管理 CPA/CPS/CPI 推广计划，支持从 S3 存储桶读取 Parquet 用户画像、补充替换宏参数并推送至 AppsFlyer / Adjust 归因平台</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3.5 py-2 text-xs font-semibold rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 shadow-sm transition-colors"
          @click="refreshAll"
        >
          🔄 刷新数据
        </button>
        <NuxtLink
          to="/offers/create"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        >
          <span>➕</span>
          <span>新建推广计划</span>
        </NuxtLink>
      </div>
    </div>

    <!-- Offer 数据表格 -->
    <CommonTable
      :columns="columns"
      :data="offers"
      :loading="loading"
      search-placeholder="搜索 Offer 名称、ID 或广告主..."
    >
      <!-- 自定义计划信息列 -->
      <template #cell-title="{ row }">
        <div class="flex flex-col">
          <span class="font-bold text-slate-900">{{ row.title }}</span>
          <span class="text-[11px] text-slate-400 font-mono">ID: {{ row.id }} · 广告主: {{ row.advertiserId }}</span>
        </div>
      </template>

      <!-- 自定义计费模式 -->
      <template #cell-payoutType="{ row }">
        <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">
          {{ row.payoutType }}
        </span>
      </template>

      <!-- 自定义佣金与营收 -->
      <template #cell-pricing="{ row }">
        <div class="text-xs">
          <span class="text-emerald-600 font-bold">${{ row.defaultPayout }}</span>
          <span class="text-slate-400 mx-1">/</span>
          <span class="text-slate-700 font-medium">${{ row.defaultRevenue }}</span>
        </div>
      </template>

      <!-- 自定义状态 -->
      <template #cell-status="{ row }">
        <StatusTag :status="row.status" />
      </template>

      <!-- 自定义转化 Cap -->
      <template #cell-dailyConversionCap="{ row }">
        <div class="text-xs">
          <span v-if="row.dailyConversionCap > 0" class="font-semibold text-slate-800">
            {{ row.dailyConversionCap }} 单/日
          </span>
          <span v-else class="text-slate-400 italic">不设上限</span>
          <div v-if="row.fallbackOfferId" class="text-[10px] text-brand-600 mt-0.5">
            兜底: {{ row.fallbackOfferId }}
          </div>
        </div>
      </template>

      <!-- 操作列 -->
      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="px-2.5 py-1 text-xs font-semibold rounded-lg bg-indigo-50 text-indigo-700 hover:bg-indigo-100 border border-indigo-200 transition-colors"
            @click="openSupplementModal(row)"
          >
            🚀 补量推送 (AF/Adjust)
          </button>
          <CopyButton :text="trackingUrl(row.id)">
            推广短链
          </CopyButton>
        </div>
      </template>
    </CommonTable>

    <!-- Offer 补量规则列表 -->
    <div v-if="supplementRules.length > 0" class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
        <div>
          <h4 class="text-sm font-bold text-slate-800">⚙️ 已配置的 Offer 流量补量规则 (Parquet 数据源 & 定时调度)</h4>
          <p class="text-[11px] text-slate-400">支持存储桶 Parquet 游标续传，自动记录每次点击会话与归因推送结果</p>
        </div>
        <span class="text-xs font-mono text-slate-500">共 {{ supplementRules.length }} 条规则</span>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="bg-slate-50 text-slate-500 border-b border-slate-200">
              <th class="py-2.5 px-4 font-semibold">Offer ID / 渠道</th>
              <th class="py-2.5 px-4 font-semibold">归因平台</th>
              <th class="py-2.5 px-4 font-semibold">存储桶 Parquet 文件路径</th>
              <th class="py-2.5 px-4 font-semibold">单批条数 / QPS</th>
              <th class="py-2.5 px-4 font-semibold">当前游标 / 累计点击</th>
              <th class="py-2.5 px-4 font-semibold">自动调度</th>
              <th class="py-2.5 px-4 font-semibold text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="rule in supplementRules" :key="rule.id" class="hover:bg-slate-50/80">
              <td class="py-2.5 px-4 font-mono">
                <span class="font-bold text-slate-800">{{ rule.offerId }}</span>
                <span class="block text-[10px] text-slate-400">Aff: {{ rule.affiliateId }}</span>
              </td>
              <td class="py-2.5 px-4">
                <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-700">{{ rule.mmpPlatform }}</span>
              </td>
              <td class="py-2.5 px-4 font-mono text-[11px] text-slate-600 max-w-xs truncate" :title="rule.objectKeyOrPath">
                {{ rule.objectKeyOrPath }}
              </td>
              <td class="py-2.5 px-4 font-mono text-slate-700">{{ rule.batchSize }} 条 / {{ rule.qpsLimit }} QPS</td>
              <td class="py-2.5 px-4 font-mono">
                <span class="text-indigo-600 font-bold">Offset #{{ rule.lastCursorOffset }}</span>
                <span class="text-slate-400 mx-1">·</span>
                <span class="text-emerald-600 font-bold">{{ rule.totalDispatchedClicks }} Clicks</span>
              </td>
              <td class="py-2.5 px-4">
                <span
                  class="px-2 py-0.5 rounded text-[10px] font-bold"
                  :class="rule.autoEnabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'"
                >
                  {{ rule.autoEnabled ? `每 ${rule.cronIntervalMinutes} 分钟` : '手动触发' }}
                </span>
              </td>
              <td class="py-2.5 px-4 text-right space-x-2">
                <button
                  type="button"
                  class="text-indigo-600 hover:text-indigo-800 font-semibold"
                  @click="runRuleNow(rule)"
                >
                  立即执行一批
                </button>
                <button
                  type="button"
                  class="text-rose-600 hover:text-rose-800 font-semibold"
                  @click="removeRule(rule.id)"
                >
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 补量点击记录与 MMP 推送日志表 -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
        <div>
          <h4 class="text-sm font-bold text-slate-800">📡 Offer 补量点击会话与 AF/Adjust 归因推送明细 (Supplement Click Logs)</h4>
          <p class="text-[11px] text-slate-400">每次补量均真实写入 ClickSession (click_id) 并携带设备 IP / User-Agent / IDFA / GADVID 推送至归因平台</p>
        </div>
        <span class="text-xs font-mono text-slate-500">最近 {{ supplementLogs.length }} 条点击记录</span>
      </div>
      <div v-if="supplementLogs.length > 0" class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="bg-slate-50 text-slate-500 border-b border-slate-200">
              <th class="py-2.5 px-4 font-semibold">Click ID / Offer</th>
              <th class="py-2.5 px-4 font-semibold">归因平台</th>
              <th class="py-2.5 px-4 font-semibold">设备标识 (IDFA / GADVID)</th>
              <th class="py-2.5 px-4 font-semibold">客户端 IP / OS</th>
              <th class="py-2.5 px-4 font-semibold">子媒体 (ds_publisher_id / adx / s)</th>
              <th class="py-2.5 px-4 font-semibold">参数替换后归因 URL</th>
              <th class="py-2.5 px-4 font-semibold">HTTP 状态</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in supplementLogs" :key="item.id" class="hover:bg-slate-50/70">
              <td class="py-2.5 px-4 font-mono">
                <div class="font-bold text-slate-900">{{ item.clickId }}</div>
                <div class="text-[10px] text-slate-400">Offer: {{ item.offerId }} · Aff: {{ item.affiliateId }}</div>
              </td>
              <td class="py-2.5 px-4">
                <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-700">{{ item.mmpPlatform }}</span>
              </td>
              <td class="py-2.5 px-4 font-mono text-[11px] text-slate-700">
                <div>{{ item.idfa || item.gadvid || item.profileId }}</div>
                <div class="text-[10px] text-slate-400">m={{ item.modelFlag ?? '-' }}</div>
              </td>
              <td class="py-2.5 px-4 font-mono text-[11px]">
                <div class="text-slate-800">{{ item.ip }}</div>
                <div class="text-[10px] text-slate-400 uppercase">{{ item.os }}</div>
              </td>
              <td class="py-2.5 px-4 font-mono text-[11px]">
                <div class="text-slate-800 truncate max-w-[160px]" :title="item.dsPublisherId">Pub: {{ item.dsPublisherId }}</div>
                <div class="text-[10px] text-slate-500">ADX: {{ item.dsAdx }} · s: {{ item.sourceTag }}</div>
              </td>
              <td class="py-2.5 px-4 font-mono text-[11px] text-slate-600 max-w-xs truncate" :title="item.targetUrl">
                {{ item.targetUrl }}
              </td>
              <td class="py-2.5 px-4 font-mono">
                <span
                  class="px-2 py-0.5 rounded text-[10px] font-bold"
                  :class="item.success ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'"
                >
                  HTTP {{ item.httpStatus }} ({{ item.latencyMs }}ms)
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="p-8 text-center text-xs text-slate-400">
        暂无补量点击记录，请点击任意 Offer 右侧的「🚀 补量推送 (AF/Adjust)」触发补量
      </div>
    </div>

    <!-- Offer 补量配置与立即执行弹窗 -->
    <div v-if="showSuppModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 overflow-y-auto">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-3xl w-full p-6 space-y-4 my-8">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <div>
            <h4 class="text-sm font-bold text-slate-900">🚀 Offer 流量补量与归因平台推送 ({{ suppForm.offerId }})</h4>
            <p class="text-[11px] text-slate-500">从存储桶或本地读取 Parquet 用户画像，自动补充替换 Offer 参数并推送到 AppsFlyer / Adjust</p>
          </div>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showSuppModal = false">✕</button>
        </div>

        <div class="space-y-3 text-xs">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">目标归因平台 (MMP)</label>
              <select v-model="suppForm.mmpPlatform" class="w-full border border-slate-300 rounded-lg p-2 bg-white font-semibold" @change="applyDefaultTemplateForMmp">
                <option value="APPSFLYER">AppsFlyer (AF)</option>
                <option value="ADJUST">Adjust</option>
                <option value="SINGULAR">Singular</option>
                <option value="BRANCH">Branch</option>
                <option value="CUSTOM">自定义宏模板</option>
              </select>
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">归属渠道客 ID (Affiliate ID)</label>
              <input v-model="suppForm.affiliateId" type="text" class="w-full font-mono border border-slate-300 rounded-lg p-2" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">设备系统过滤 (OS Filter)</label>
              <select v-model="suppForm.osFilter" class="w-full border border-slate-300 rounded-lg p-2 bg-white font-mono">
                <option value="ALL">ALL (全部系统)</option>
                <option value="ios">ios (仅 iOS / IDFA)</option>
                <option value="android">android (仅 Android / GADVID)</option>
              </select>
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">关联 S3 存储桶配置</label>
              <select v-model="suppForm.storageConfigId" class="w-full border border-slate-300 rounded-lg p-2 bg-white">
                <option value="">默认存储桶 / 本地直读</option>
                <option v-for="cfg in s3Configs" :key="cfg.id" :value="cfg.id">
                  {{ cfg.name }} ({{ cfg.bucketName }})
                </option>
              </select>
            </div>
            <div class="sm:col-span-2">
              <label class="block font-semibold text-slate-700 mb-1">存储桶 Object Key 或本地 Parquet 文件路径</label>
              <div class="flex gap-2">
                <input
                  v-model="suppForm.objectKeyOrPath"
                  type="text"
                  placeholder="c:\Users\Administrator\Downloads\part-00000-...snappy.parquet 或 s3://..."
                  class="flex-1 font-mono border border-slate-300 rounded-lg p-2"
                />
                <button
                  type="button"
                  :disabled="previewing"
                  class="px-3 py-2 rounded-lg border border-indigo-200 bg-indigo-50 text-indigo-700 font-semibold hover:bg-indigo-100 whitespace-nowrap"
                  @click="previewParquet"
                >
                  {{ previewing ? '解析中...' : '🔍 预览 Parquet 画像' }}
                </button>
              </div>
            </div>
          </div>

          <!-- Parquet 预览结果展示 -->
          <div v-if="previewBatch" class="p-3 rounded-lg bg-slate-900 text-slate-100 space-y-2">
            <div class="flex items-center justify-between text-[11px] font-mono text-indigo-300">
              <span>📊 Parquet 总行数: {{ previewBatch.totalRowsInFile.toLocaleString() }} 行 · 字段: {{ (previewBatch.columns || []).join(', ') }}</span>
              <button type="button" class="text-slate-400 hover:text-white" @click="previewBatch = null">收起预览</button>
            </div>
            <div class="overflow-x-auto max-h-40">
              <table class="w-full text-left font-mono text-[10px]">
                <thead>
                  <tr class="text-slate-400 border-b border-slate-700">
                    <th class="py-1 pr-2">idfa / gadvid</th>
                    <th class="py-1 pr-2">ip</th>
                    <th class="py-1 pr-2">os</th>
                    <th class="py-1 pr-2">ds_publisher_id</th>
                    <th class="py-1 pr-2">ds_adx</th>
                    <th class="py-1 pr-2">s / m</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-800">
                  <tr v-for="(p, idx) in previewBatch.profiles" :key="idx">
                    <td class="py-1 pr-2 text-emerald-300">{{ p.idfa || p.gadvid || p.id }}</td>
                    <td class="py-1 pr-2">{{ p.ip }}</td>
                    <td class="py-1 pr-2">{{ p.os }}</td>
                    <td class="py-1 pr-2 truncate max-w-[140px]">{{ p.dsPublisherId }}</td>
                    <td class="py-1 pr-2 text-amber-300">{{ p.dsAdx }}</td>
                    <td class="py-1 pr-2">{{ p.s }} ({{ p.m }})</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div>
            <label class="block font-semibold text-slate-700 mb-1">
              归因追踪链接模板 (支持宏占位符与缺失参数自动补齐)
            </label>
            <textarea
              v-model="suppForm.trackingUrlTemplate"
              rows="2"
              class="w-full font-mono border border-slate-300 rounded-lg p-2 text-[11px]"
            ></textarea>
            <p class="text-[10px] text-slate-400 mt-1 font-mono">
              可用画像变量: &#123;click_id&#125; &#123;idfa&#125; &#123;gadvid&#125; &#123;ip&#125; &#123;ua&#125; &#123;os&#125; &#123;ds_publisher_id&#125; &#123;ds_adx&#125; &#123;s&#125; &#123;m&#125; &#123;aff_id&#125; &#123;offer_id&#125;
            </p>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-4 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">单次补量点击数</label>
              <input v-model.number="suppForm.batchSize" type="number" min="1" max="1000" class="w-full font-mono border border-slate-300 rounded-lg p-2" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">平滑速率限制 (QPS)</label>
              <input v-model.number="suppForm.qpsLimit" type="number" min="1" max="500" class="w-full font-mono border border-slate-300 rounded-lg p-2" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">Parquet 起始行游标</label>
              <input v-model.number="suppForm.startOffset" type="number" min="0" class="w-full font-mono border border-slate-300 rounded-lg p-2" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">定时周期 (分钟)</label>
              <input v-model.number="suppForm.cronIntervalMinutes" type="number" min="1" max="1440" class="w-full font-mono border border-slate-300 rounded-lg p-2" />
            </div>
          </div>

          <label class="inline-flex items-center gap-2 pt-1 cursor-pointer">
            <input v-model="suppForm.autoEnabled" type="checkbox" class="rounded border-slate-300 text-indigo-600" />
            <span class="font-semibold text-slate-700">启用后台定时自动补量调度 (按游标顺序循环读取存储桶 Parquet 画像)</span>
          </label>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 pt-4 border-t border-slate-100">
          <button
            type="button"
            :disabled="savingRule"
            class="px-4 py-2 text-xs font-semibold rounded-lg border border-indigo-300 bg-indigo-50 hover:bg-indigo-100 text-indigo-700"
            @click="saveSupplementRule"
          >
            {{ savingRule ? '保存中...' : '💾 保存为补量规则' }}
          </button>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700"
              @click="showSuppModal = false"
            >
              取消
            </button>
            <button
              type="button"
              :disabled="executingSupp"
              class="px-5 py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm"
              @click="triggerSupplementNow"
            >
              {{ executingSupp ? '正在解析并推送...' : '⚡ 立即执行补量并推送归因平台' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import CopyButton from '~/components/common/CopyButton.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()
const loading = ref(false)
const offers = ref<any[]>([])
const s3Configs = ref<any[]>([])
const supplementRules = ref<any[]>([])
const supplementLogs = ref<any[]>([])

const showSuppModal = ref(false)
const previewing = ref(false)
const savingRule = ref(false)
const executingSupp = ref(false)
const previewBatch = ref<any>(null)

const DEFAULT_PARQUET_PATH = 'c:\\Users\\Administrator\\Downloads\\part-00000-8eb3e574-f749-4e15-820d-d957e5360834.c000.snappy.parquet'

const suppForm = ref({
  offerId: '',
  affiliateId: 'aff_supplement',
  mmpPlatform: 'APPSFLYER',
  storageConfigId: '',
  objectKeyOrPath: DEFAULT_PARQUET_PATH,
  osFilter: 'ios',
  trackingUrlTemplate: '',
  batchSize: 10,
  qpsLimit: 50,
  startOffset: 0,
  autoEnabled: true,
  cronIntervalMinutes: 30
})

const trackingUrl = (offerId: string) =>
  `${window.location.origin}/affiliate/click?offer_id=${offerId}&aff_id=AFF_ID`

const columns = [
  { key: 'title', label: '推广计划 (Title / ID)' },
  { key: 'payoutType', label: '计费类型' },
  { key: 'pricing', label: '渠道佣金 / 广告主收费' },
  { key: 'status', label: '状态' },
  { key: 'dailyConversionCap', label: '转化配额 (Cap)' },
  { key: 'actions', label: '操作' }
]

const refreshAll = async () => {
  await Promise.all([loadOffers(), loadS3Configs(), loadSupplementRules(), loadSupplementLogs()])
}

const loadOffers = async () => {
  loading.value = true
  try {
    offers.value = await fetchApi<any[]>('/api/v1/affiliate/offers') || []
  } catch (err: any) {
    offers.value = []
    showToast(`加载 Offer 计划失败：${err.message || err}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const loadS3Configs = async () => {
  try {
    s3Configs.value = await fetchApi<any[]>('/api/v1/system/config/s3') || []
  } catch (e) {
    s3Configs.value = []
  }
}

const loadSupplementRules = async () => {
  try {
    supplementRules.value = await fetchApi<any[]>('/api/v1/affiliate/offers/supplement/rules') || []
  } catch (e) {
    supplementRules.value = []
  }
}

const loadSupplementLogs = async () => {
  try {
    supplementLogs.value = await fetchApi<any[]>('/api/v1/affiliate/offers/supplement/logs?limit=50') || []
  } catch (e) {
    supplementLogs.value = []
  }
}

const openSupplementModal = (offer: any) => {
  previewBatch.value = null
  const baseLanding = offer.landingPageUrl || `https://app.appsflyer.com/${offer.id}`
  suppForm.value = {
    offerId: offer.id,
    affiliateId: 'aff_supplement',
    mmpPlatform: baseLanding.includes('adjust.com') ? 'ADJUST' : 'APPSFLYER',
    storageConfigId: s3Configs.value[0]?.id || '',
    objectKeyOrPath: DEFAULT_PARQUET_PATH,
    osFilter: 'ios',
    trackingUrlTemplate: baseLanding.includes('{idfa}')
      ? baseLanding
      : `https://app.appsflyer.com/${offer.id}?pid={aff_id}&clickid={click_id}&idfa={idfa}&advertising_id={gadvid}&ip={ip}&ua={ua}&af_siteid={ds_publisher_id}&af_sub_siteid={ds_adx}&af_sub1={s}&af_sub2={m}`,
    batchSize: 10,
    qpsLimit: 50,
    startOffset: 0,
    autoEnabled: true,
    cronIntervalMinutes: 30
  }
  showSuppModal.value = true
}

const applyDefaultTemplateForMmp = () => {
  const oid = suppForm.value.offerId || 'id123456'
  if (suppForm.value.mmpPlatform === 'ADJUST') {
    suppForm.value.trackingUrlTemplate = `https://app.adjust.com/${oid}?click_id={click_id}&idfa={idfa}&gps_adid={gadvid}&ip_address={ip}&user_agent={ua}&publisher_id={ds_publisher_id}&subpublisher_id={ds_adx}&campaign={s}&adgroup={m}&s2s=1`
  } else if (suppForm.value.mmpPlatform === 'APPSFLYER') {
    suppForm.value.trackingUrlTemplate = `https://app.appsflyer.com/${oid}?pid={aff_id}&clickid={click_id}&idfa={idfa}&advertising_id={gadvid}&ip={ip}&ua={ua}&af_siteid={ds_publisher_id}&af_sub_siteid={ds_adx}&af_sub1={s}&af_sub2={m}`
  }
}

const previewParquet = async () => {
  previewing.value = true
  try {
    previewBatch.value = await fetchApi<any>('/api/v1/affiliate/offers/supplement/preview', {
      method: 'POST',
      body: {
        storageConfigId: suppForm.value.storageConfigId,
        objectKeyOrPath: suppForm.value.objectKeyOrPath,
        osFilter: suppForm.value.osFilter,
        offset: suppForm.value.startOffset,
        limit: 5
      }
    })
    showToast(`成功读取 Parquet 文件，共 ${previewBatch.value?.totalRowsInFile || 0} 条设备画像`, 'success')
  } catch (err: any) {
    showToast(`预览 Parquet 失败：${err.message || err}`, 'error')
  } finally {
    previewing.value = false
  }
}

const saveSupplementRule = async () => {
  savingRule.value = true
  try {
    await fetchApi('/api/v1/affiliate/offers/supplement/rules', {
      method: 'POST',
      body: {
        offerId: suppForm.value.offerId,
        affiliateId: suppForm.value.affiliateId,
        mmpPlatform: suppForm.value.mmpPlatform,
        storageConfigId: suppForm.value.storageConfigId,
        objectKeyOrPath: suppForm.value.objectKeyOrPath,
        osFilter: suppForm.value.osFilter,
        trackingUrlTemplate: suppForm.value.trackingUrlTemplate,
        batchSize: suppForm.value.batchSize,
        qpsLimit: suppForm.value.qpsLimit,
        autoEnabled: suppForm.value.autoEnabled,
        cronIntervalMinutes: suppForm.value.cronIntervalMinutes,
        lastCursorOffset: suppForm.value.startOffset,
        status: 'ACTIVE'
      }
    })
    showToast('Offer 补量规则保存成功', 'success')
    await loadSupplementRules()
  } catch (err: any) {
    showToast(`保存补量规则失败：${err.message || err}`, 'error')
  } finally {
    savingRule.value = false
  }
}

const triggerSupplementNow = async () => {
  executingSupp.value = true
  try {
    const res = await fetchApi<any>('/api/v1/affiliate/offers/supplement/trigger', {
      method: 'POST',
      body: {
        offerId: suppForm.value.offerId,
        affiliateId: suppForm.value.affiliateId,
        mmpPlatform: suppForm.value.mmpPlatform,
        storageConfigId: suppForm.value.storageConfigId,
        objectKeyOrPath: suppForm.value.objectKeyOrPath,
        osFilter: suppForm.value.osFilter,
        trackingUrlTemplate: suppForm.value.trackingUrlTemplate,
        batchSize: suppForm.value.batchSize,
        qpsLimit: suppForm.value.qpsLimit,
        startOffset: suppForm.value.startOffset
      }
    })
    showToast(`补量完成！已生成 ${res?.dispatchedCount || 0} 笔点击并推送至 ${res?.mmpPlatform}`, 'success')
    showSuppModal.value = false
    await Promise.all([loadSupplementRules(), loadSupplementLogs()])
  } catch (err: any) {
    showToast(`执行补量失败：${err.message || err}`, 'error')
  } finally {
    executingSupp.value = false
  }
}

const runRuleNow = async (rule: any) => {
  try {
    const res = await fetchApi<any>('/api/v1/affiliate/offers/supplement/trigger', {
      method: 'POST',
      body: {
        ruleId: rule.id,
        offerId: rule.offerId,
        affiliateId: rule.affiliateId,
        mmpPlatform: rule.mmpPlatform,
        storageConfigId: rule.storageConfigId,
        objectKeyOrPath: rule.objectKeyOrPath,
        osFilter: rule.osFilter,
        trackingUrlTemplate: rule.trackingUrlTemplate,
        batchSize: rule.batchSize,
        qpsLimit: rule.qpsLimit,
        startOffset: rule.lastCursorOffset
      }
    })
    showToast(`规则执行完成：推送 ${res?.dispatchedCount || 0} 笔点击，游标推进至 #${res?.nextOffset || 0}`, 'success')
    await Promise.all([loadSupplementRules(), loadSupplementLogs()])
  } catch (err: any) {
    showToast(`触发规则失败：${err.message || err}`, 'error')
  }
}

const removeRule = async (ruleId: string) => {
  try {
    await fetchApi(`/api/v1/affiliate/offers/supplement/rules/${ruleId}`, { method: 'DELETE' })
    showToast('补量规则已删除', 'success')
    await loadSupplementRules()
  } catch (err: any) {
    showToast(`删除失败：${err.message || err}`, 'error')
  }
}

onMounted(() => {
  refreshAll()
})
</script>
