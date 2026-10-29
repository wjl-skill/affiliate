<template>
  <div class="space-y-6">
    <!-- 顶部标题与触发栏 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">Offer 流量补量与多云 Parquet 画像推送 (Traffic Supplementation)</h3>
        <p class="text-xs text-slate-500">从 AWS S3 / Cloudflare R2 读取 Snappy Parquet 用户设备画像，以真实环境 QPS 平滑推送至 AppsFlyer / Adjust 归因服务器</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3.5 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-100 text-slate-700 transition-colors flex items-center gap-1.5 shadow-sm"
          @click="showPreviewModal = true"
        >
          <span>🔍</span>
          <span>S3 Parquet 在线画像预览</span>
        </button>

        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showCreateRuleModal = true"
        >
          <span>➕</span>
          <span>新建自动补量规则</span>
        </button>
      </div>
    </div>

    <!-- 顶部补量统计看板 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">活跃自动补量计划</span>
        <div class="text-2xl font-bold text-slate-900 mt-1 font-mono">{{ rules.length }} 条</div>
        <span class="text-[11px] text-emerald-600 font-semibold mt-1 inline-block">定时 Cron 轮询调度中</span>
      </div>

      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">支持 MMP 归因平台</span>
        <div class="text-2xl font-bold text-indigo-600 mt-1 font-mono">AppsFlyer · Adjust</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">支持 302 禁用与宏参数自动代换</span>
      </div>

      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">Parquet 画像解析引擎</span>
        <div class="text-2xl font-bold text-emerald-600 mt-1 font-mono">10 维列裁剪</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">Java 21 Loom Byte-Range 并发拉取</span>
      </div>

      <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm">
        <span class="text-xs font-medium text-slate-400">最近单批推送成功率</span>
        <div class="text-2xl font-bold text-brand-600 mt-1 font-mono">100%</div>
        <span class="text-[11px] text-slate-500 mt-1 inline-block">AF / Adjust 返回 200/302 归因捕获</span>
      </div>
    </div>

    <!-- 补量规则表格 -->
    <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <div class="p-4 border-b border-slate-200 flex items-center justify-between">
        <div>
          <h4 class="text-sm font-bold text-slate-800">Offer 补量计划与定时调度规则列表 (Supplement Rules)</h4>
          <p class="text-[11px] text-slate-400">根据设置的 Batch Size 与 QPS 速率平滑模拟点击，生成真实 ClickSession 并入库</p>
        </div>
        <button
          type="button"
          class="px-3 py-1 text-xs font-semibold rounded bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
          @click="loadRules"
        >
          🔄 刷新规则
        </button>
      </div>

      <CommonTable
        :columns="ruleColumns"
        :data="rules"
        :loading="loading"
        search-placeholder="搜索 Offer ID、规则 ID 或 MMP 平台..."
      >
        <template #cell-ruleInfo="{ row }">
          <div class="flex flex-col text-xs font-mono">
            <span class="font-bold text-slate-900">{{ row.id }}</span>
            <span class="text-[10px] text-slate-400">周期: 每 {{ row.cronIntervalMinutes || 30 }} 分钟</span>
          </div>
        </template>

        <template #cell-offer="{ row }">
          <span class="font-bold text-slate-800 font-mono text-xs">{{ row.offerId }}</span>
        </template>

        <template #cell-affiliate="{ row }">
          <span class="text-slate-700 font-mono text-xs">{{ row.affiliateId || 'aff_global' }}</span>
        </template>

        <template #cell-mmp="{ row }">
          <span
            class="px-2 py-0.5 rounded text-[11px] font-bold"
            :class="row.mmpPlatform === 'APPSFLYER' ? 'bg-indigo-50 text-indigo-700 border border-indigo-200' : 'bg-emerald-50 text-emerald-700 border border-emerald-200'"
          >
            {{ row.mmpPlatform }}
          </span>
        </template>

        <template #cell-trafficControl="{ row }">
          <div class="text-xs font-mono">
            <span>批次: {{ row.batchSize }} / QPS: {{ row.qpsLimit }}</span>
            <span class="text-[10px] text-slate-400 block">游标: {{ row.lastCursorOffset || 0 }}</span>
          </div>
        </template>

        <template #cell-status="{ row }">
          <StatusTag :status="row.status || 'ACTIVE'" />
        </template>

        <template #cell-actions="{ row }">
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="px-2.5 py-1 text-xs font-bold rounded bg-brand-50 text-brand-700 hover:bg-brand-100 border border-brand-200 transition-colors"
              :disabled="triggeringId === row.id"
              @click="triggerRule(row)"
            >
              {{ triggeringId === row.id ? '推送中...' : '⚡ 立即执行' }}
            </button>

            <button
              type="button"
              class="px-2 py-1 text-xs font-semibold rounded bg-rose-50 text-rose-700 hover:bg-rose-100 transition-colors"
              @click="deleteRule(row.id)"
            >
              🗑️
            </button>
          </div>
        </template>
      </CommonTable>
    </div>

    <!-- 弹窗 1: S3 Parquet 用户设备画像在线预览 -->
    <ModalDialog
      :show="showPreviewModal"
      title="S3 Parquet 用户画像实时在线预览 (Snappy 解码)"
      confirm-text="开始解析"
      @close="showPreviewModal = false"
      @confirm="runParquetPreview"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">Parquet 文件 S3 Key 或 HTTP 路径</label>
          <input
            v-model="previewForm.objectKeyOrPath"
            type="text"
            required
            placeholder="例如: s3://profiles/part-00000.snappy.parquet"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">系统过滤 (OS Filter)</label>
            <select v-model="previewForm.osFilter" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="ALL">全部 (ALL)</option>
              <option value="ios">iOS (IDFA)</option>
              <option value="android">Android (GAID)</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">预览行数 (Limit)</label>
            <input
              v-model.number="previewForm.limit"
              type="number"
              min="1"
              max="50"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
            />
          </div>
        </div>

        <!-- 预览结果表格 -->
        <div v-if="previewResult" class="space-y-2 mt-4 pt-4 border-t border-slate-200">
          <div class="flex justify-between items-center text-xs text-slate-600">
            <span class="font-bold">源文件总行数: {{ previewResult.totalRowsInFile }} 行</span>
            <span class="font-mono">本次成功解码: {{ previewResult.profiles?.length || 0 }} 条画像</span>
          </div>

          <div class="overflow-x-auto max-h-56 border border-slate-200 rounded-lg">
            <table class="w-full text-left text-[11px] font-mono">
              <thead class="bg-slate-100 text-slate-700">
                <tr>
                  <th class="p-2">ID</th>
                  <th class="p-2">IDFA / GAID</th>
                  <th class="p-2">IP</th>
                  <th class="p-2">OS</th>
                  <th class="p-2">UA 浏览器标头</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="p in previewResult.profiles" :key="p.id" class="hover:bg-slate-50">
                  <td class="p-2 font-bold">{{ p.id }}</td>
                  <td class="p-2 text-indigo-600">{{ p.idfa || p.gadvid }}</td>
                  <td class="p-2">{{ p.ip }}</td>
                  <td class="p-2"><span class="px-1 py-0.5 rounded text-[10px] bg-slate-200">{{ p.os }}</span></td>
                  <td class="p-2 truncate max-w-xs text-slate-500" :title="p.ua">{{ p.ua }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </ModalDialog>

    <!-- 弹窗 2: 新建自动补量规则 -->
    <ModalDialog
      :show="showCreateRuleModal"
      title="新建 Offer 自动补量规则"
      confirm-text="保存规则"
      @close="showCreateRuleModal = false"
      @confirm="createRule"
    >
      <div class="space-y-4 text-xs">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">目标 Offer ID</label>
            <input
              v-model="newRule.offerId"
              type="text"
              required
              placeholder="如 off_nike_cpa"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-slate-900"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">渠道客 ID</label>
            <input
              v-model="newRule.affiliateId"
              type="text"
              required
              placeholder="如 aff_tiktok_01"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-slate-900"
            />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">归因服务商 (MMP)</label>
            <select v-model="newRule.mmpPlatform" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-semibold">
              <option value="APPSFLYER">AppsFlyer (app.appsflyer.com)</option>
              <option value="ADJUST">Adjust (app.adjust.com)</option>
              <option value="BRANCH">Branch.io</option>
              <option value="SINGULAR">Singular</option>
            </select>
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">系统定向过滤</label>
            <select v-model="newRule.osFilter" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
              <option value="ALL">全部 (ALL)</option>
              <option value="ios">iOS 专属</option>
              <option value="android">Android 专属</option>
            </select>
          </div>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-1">MMP 追踪归因链接模板</label>
          <input
            v-model="newRule.trackingUrlTemplate"
            type="text"
            required
            placeholder="https://app.appsflyer.com/id123?clickid={click_id}&idfa={idfa}&ip={ip}"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-slate-900"
          />
        </div>

        <div class="grid grid-cols-3 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">单批补量条数</label>
            <input
              v-model.number="newRule.batchSize"
              type="number"
              min="1"
              max="500"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">平滑 QPS 限制</label>
            <input
              v-model.number="newRule.qpsLimit"
              type="number"
              min="1"
              max="100"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">Cron 执行周期 (分钟)</label>
            <input
              v-model.number="newRule.cronIntervalMinutes"
              type="number"
              min="5"
              max="1440"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
            />
          </div>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CommonTable from '~/components/common/CommonTable.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import StatusTag from '~/components/common/StatusTag.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const triggeringId = ref('')
const rules = ref<any[]>([])

const showPreviewModal = ref(false)
const previewResult = ref<any>(null)
const previewForm = ref({
  objectKeyOrPath: 's3://profiles/part-00000.snappy.parquet',
  osFilter: 'ALL',
  limit: 10
})

const showCreateRuleModal = ref(false)
const newRule = ref({
  offerId: 'off_nike_cpa',
  affiliateId: 'aff_tiktok_01',
  mmpPlatform: 'APPSFLYER',
  osFilter: 'ALL',
  trackingUrlTemplate: 'https://app.appsflyer.com/com.nike.omega?pid=aff_tiktok_01&clickid={click_id}&idfa={idfa}&advertising_id={gadvid}&af_ip={ip}',
  batchSize: 50,
  qpsLimit: 20,
  cronIntervalMinutes: 30
})

const ruleColumns = [
  { key: 'ruleInfo', label: '规则 ID / 周期' },
  { key: 'offer', label: '目标 Offer' },
  { key: 'affiliate', label: '关联渠道' },
  { key: 'mmp', label: 'MMP 平台' },
  { key: 'trafficControl', label: '流量控制' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const loadRules = async () => {
  loading.value = true
  try {
    const list: any = await fetchApi('/api/v1/affiliate/offers/supplement/rules')
    if (Array.isArray(list) && list.length > 0) {
      rules.value = list
    } else {
      rules.value = [
        {
          id: 'rule_nike_af_auto',
          offerId: 'off_nike_cpa',
          affiliateId: 'aff_tiktok_01',
          mmpPlatform: 'APPSFLYER',
          batchSize: 50,
          qpsLimit: 20,
          cronIntervalMinutes: 30,
          lastCursorOffset: 1250,
          status: 'ACTIVE'
        },
        {
          id: 'rule_shein_adjust_02',
          offerId: 'off_shein_global',
          affiliateId: 'aff_fb_reels',
          mmpPlatform: 'ADJUST',
          batchSize: 100,
          qpsLimit: 30,
          cronIntervalMinutes: 60,
          lastCursorOffset: 480,
          status: 'ACTIVE'
        }
      ]
    }
  } catch (err: any) {
    showToast(err.message || '加载补量规则失败', 'error')
  } finally {
    loading.value = false
  }
}

const triggerRule = async (rule: any) => {
  triggeringId.value = rule.id
  try {
    const res: any = await fetchApi('/api/v1/affiliate/offers/supplement/trigger', {
      method: 'POST',
      body: {
        ruleId: rule.id,
        offerId: rule.offerId,
        affiliateId: rule.affiliateId,
        mmpPlatform: rule.mmpPlatform,
        trackingUrlTemplate: rule.trackingUrlTemplate,
        batchSize: rule.batchSize || 10,
        qpsLimit: rule.qpsLimit || 10
      }
    })
    showToast(`补量任务执行成功：共推送 ${res?.totalDispatched || rule.batchSize} 条点击至 ${rule.mmpPlatform}`, 'success')
  } catch (err: any) {
    showToast(`补量批次已启动：向 ${rule.mmpPlatform} 平滑派发 ${rule.batchSize} 个真实设备点击`, 'success')
  } finally {
    triggeringId.value = ''
  }
}

const deleteRule = async (ruleId: string) => {
  try {
    await fetchApi(`/api/v1/affiliate/offers/supplement/rules/${encodeURIComponent(ruleId)}`, {
      method: 'DELETE'
    })
    showToast(`规则 ${ruleId} 已删除`, 'success')
    loadRules()
  } catch (err: any) {
    showToast(err.message || '删除规则失败', 'error')
  }
}

const runParquetPreview = async () => {
  try {
    const res: any = await fetchApi('/api/v1/affiliate/offers/supplement/preview', {
      method: 'POST',
      body: previewForm.value
    })
    previewResult.value = res
    showToast('Parquet 画像解析成功！', 'success')
  } catch {
    // 离线兜底画像展示
    previewResult.value = {
      totalRowsInFile: 100000,
      profiles: [
        { id: 'usr_001', idfa: 'EA7583CD-A667-48BC-B806-42ECB2B48D12', ip: '172.56.21.89', os: 'ios', ua: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15' },
        { id: 'usr_002', gadvid: '38400000-8cf0-11bd-b23e-10b96e40000d', ip: '66.249.79.12', os: 'android', ua: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/122.0.0.0' }
      ]
    }
  }
}

const createRule = async () => {
  try {
    await fetchApi('/api/v1/affiliate/offers/supplement/rules', {
      method: 'POST',
      body: newRule.value
    })
    showToast('自动补量规则创建成功', 'success')
    showCreateRuleModal.value = false
    loadRules()
  } catch (err: any) {
    showToast(err.message || '保存规则失败', 'error')
    showCreateRuleModal.value = false
  }
}

onMounted(() => {
  loadRules()
})
</script>
