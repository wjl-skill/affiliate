<template>
  <div class="space-y-6">
    <!-- 顶部标题与操作栏 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">AI 模型配置与智能中枢 (AI Model Hub & Prompt Registry)</h3>
        <p class="text-xs text-slate-500">多租户大模型凭证管理 (OpenAI / DeepSeek / Claude / 通义千问)、结构化 Prompt 模板编排、Token 配额监控与在线推理演练</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-3.5 py-2 text-xs font-semibold rounded-lg border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 transition-colors"
          @click="refreshAll"
        >
          🔄 刷新状态
        </button>
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-colors"
          @click="openNewConfigModal"
        >
          + 新增 AI 模型配置
        </button>
      </div>
    </div>

    <!-- Token 消耗与配额统计卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">当前租户累计 AI 调用</span>
        <div class="text-2xl font-bold text-slate-800 font-mono mt-1">{{ stats.totalRequests || 0 }}</div>
        <span class="text-[10px] text-slate-500">含自动容灾回退请求</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">累计消耗总 Token</span>
        <div class="text-2xl font-bold text-indigo-600 font-mono mt-1">{{ stats.totalTokens || 0 }}</div>
        <span class="text-[10px] text-slate-500 font-mono">Prompt: {{ stats.promptTokens || 0 }} · Completion: {{ stats.completionTokens || 0 }}</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">平均推理响应时延</span>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-1">{{ Number(stats.avgLatencyMs || 0).toFixed(1) }} ms</div>
        <span class="text-[10px] text-slate-500">含 Semantic Heuristic 降级保护</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">已激活模型 / 预设模板</span>
        <div class="text-2xl font-bold text-sky-600 font-mono mt-1">{{ configs.length }} / {{ templates.length }}</div>
        <span class="text-[10px] text-slate-500">支持租户级覆盖与优先级路由</span>
      </div>
    </div>

    <!-- 主工作区：模型列表 + 在线推理沙盒 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 左侧 2 列：AI 模型配置列表 & Prompt 模板管理 -->
      <div class="lg:col-span-2 space-y-6">
        <!-- 模型配置表格 -->
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div class="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
            <h4 class="text-sm font-bold text-slate-800">已注册大模型端点配置 (Model Endpoints)</h4>
            <span class="text-[11px] text-slate-400">API Key 全程脱敏展示</span>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-left border-collapse text-xs">
              <thead>
                <tr class="bg-slate-50 text-slate-500 border-b border-slate-200">
                  <th class="py-3 px-4 font-semibold">名称 / 租户</th>
                  <th class="py-3 px-4 font-semibold">供应商 & 模型</th>
                  <th class="py-3 px-4 font-semibold">脱敏密钥</th>
                  <th class="py-3 px-4 font-semibold">优先级</th>
                  <th class="py-3 px-4 font-semibold text-right">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="cfg in configs" :key="cfg.id" class="hover:bg-slate-50/80">
                  <td class="py-3 px-4">
                    <div class="flex items-center gap-2">
                      <span class="font-bold text-slate-800">{{ cfg.name }}</span>
                      <span v-if="cfg.isDefault" class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-700">默认</span>
                    </div>
                    <div class="text-[10px] text-slate-400 font-mono">租户: {{ cfg.tenantId }} · ID: {{ cfg.id }}</div>
                  </td>
                  <td class="py-3 px-4">
                    <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-700 mr-1.5">{{ cfg.provider }}</span>
                    <span class="font-mono text-slate-700">{{ cfg.modelName }}</span>
                  </td>
                  <td class="py-3 px-4 font-mono text-slate-500">{{ cfg.apiKey || '未配置 (走语义回退)' }}</td>
                  <td class="py-3 px-4 font-mono font-bold text-slate-700">P{{ cfg.priority }}</td>
                  <td class="py-3 px-4 text-right space-x-2">
                    <button
                      type="button"
                      class="text-slate-600 hover:text-slate-900 font-semibold"
                      @click="openEditConfigModal(cfg)"
                    >
                      编辑
                    </button>
                    <button
                      v-if="!cfg.isDefault"
                      type="button"
                      class="text-indigo-600 hover:text-indigo-800 font-semibold"
                      @click="setDefaultConfig(cfg.id)"
                    >
                      设为默认
                    </button>
                    <button
                      type="button"
                      class="text-sky-600 hover:text-sky-800 font-semibold"
                      @click="testModelConfig(cfg.id)"
                    >
                      连通测试
                    </button>
                    <button
                      v-if="cfg.tenantId !== 'default'"
                      type="button"
                      class="text-rose-600 hover:text-rose-800 font-semibold"
                      @click="deleteConfig(cfg.id)"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 结构化 Prompt 模板列表 -->
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          <div class="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
            <div>
              <h4 class="text-sm font-bold text-slate-800">业务场景结构化 Prompt 模板库 (Prompt Templates)</h4>
              <span class="text-[11px] text-slate-400">支持租户级热更新覆写与 Mustache 变量渲染预览</span>
            </div>
            <button
              type="button"
              class="px-3 py-1.5 text-xs font-semibold rounded-lg border border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 transition-colors"
              @click="openNewTemplateModal"
            >
              + 新增自定义模板
            </button>
          </div>
          <div class="divide-y divide-slate-100">
            <div v-for="tpl in templates" :key="tpl.templateKey" class="p-4 hover:bg-slate-50/60 space-y-1.5">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold text-slate-800">{{ tpl.title }}</span>
                  <span class="px-2 py-0.5 rounded text-[10px] font-mono bg-slate-100 text-slate-600">{{ tpl.templateKey }}</span>
                </div>
                <div class="flex items-center gap-3">
                  <span class="text-[10px] font-mono text-slate-400">租户: {{ tpl.tenantId }}</span>
                  <button
                    type="button"
                    class="text-[11px] font-semibold text-indigo-600 hover:text-indigo-800"
                    @click="openEditTemplateModal(tpl)"
                  >
                    定制/编辑
                  </button>
                  <button
                    type="button"
                    class="text-[11px] font-semibold text-emerald-600 hover:text-emerald-800"
                    @click="openRenderModal(tpl)"
                  >
                    渲染演练
                  </button>
                </div>
              </div>
              <p class="text-[11px] text-slate-500"><strong class="text-slate-700">System:</strong> {{ tpl.systemPrompt }}</p>
              <pre class="text-[11px] font-mono text-slate-600 bg-slate-50 p-2.5 rounded-lg border border-slate-200/80 whitespace-pre-wrap">{{ tpl.userPromptTemplate }}</pre>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧 1 列：在线 AI 推理沙盒与调用审计日志 -->
      <div class="space-y-6">
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
          <h4 class="text-sm font-bold text-slate-800">⚡ AI 网关在线推理演练 (Live Playground)</h4>
          <div class="space-y-3 text-xs">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">业务调用模块标识 (Caller Module)</label>
              <select v-model="chatForm.callerModule" class="w-full border border-slate-300 rounded-lg p-2 bg-white font-semibold">
                <option value="CREATIVE">CREATIVE (素材诊断与裂变)</option>
                <option value="DSP">DSP (智能竞价与 Bid Shading)</option>
                <option value="AFFILIATE_FRAUD">AFFILIATE_FRAUD (反作弊归因诊断)</option>
                <option value="REPORTING">REPORTING (经营报表商业洞察)</option>
              </select>
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">提示词内容 (User Prompt)</label>
              <textarea
                v-model="chatForm.prompt"
                rows="3"
                placeholder="输入广告业务问题测试 AI 中枢响应..."
                class="w-full border border-slate-300 rounded-lg p-2.5"
              ></textarea>
            </div>
            <button
              type="button"
              :disabled="chatLoading"
              class="w-full py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white transition-colors disabled:opacity-50"
              @click="sendTestChat"
            >
              {{ chatLoading ? '推理生成中...' : '🚀 发送推理请求' }}
            </button>
            <div v-if="chatReply" class="p-3 rounded-lg bg-slate-900 text-slate-100 text-[11px] space-y-1.5">
              <div class="flex items-center justify-between text-[10px] text-slate-400 font-mono">
                <span>模型: {{ chatReply.modelName }}</span>
                <span>耗时: {{ chatReply.latencyMs }}ms · {{ chatReply.fallbackUsed ? '语义回退保护' : '实时远端' }}</span>
              </div>
              <div class="whitespace-pre-wrap leading-relaxed">{{ chatReply.content }}</div>
            </div>
          </div>
        </div>

        <!-- 最近调用审计日志 -->
        <div class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-3">
          <h4 class="text-sm font-bold text-slate-800">最近 AI 调用审计流水</h4>
          <div v-if="logs.length > 0" class="divide-y divide-slate-100 max-h-72 overflow-y-auto text-xs">
            <div v-for="item in logs" :key="item.id" class="py-2 flex items-center justify-between">
              <div>
                <div class="font-semibold text-slate-800">{{ item.callerModule }} · <span class="font-mono text-[11px]">{{ item.modelName }}</span></div>
                <div class="text-[10px] text-slate-400 font-mono">Tokens: {{ item.totalTokens }} · {{ item.latencyMs }}ms</div>
              </div>
              <span
                class="px-2 py-0.5 rounded text-[10px] font-bold"
                :class="item.status === 'SUCCESS' ? 'bg-emerald-100 text-emerald-700' : item.status === 'FALLBACK' ? 'bg-amber-100 text-amber-700' : 'bg-rose-100 text-rose-700'"
              >
                {{ item.status }}
              </span>
            </div>
          </div>
          <div v-else class="text-center py-6 text-xs text-slate-400">暂无调用记录</div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑模型配置弹窗 -->
    <div v-if="showConfigModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h4 class="text-sm font-bold text-slate-900">{{ configForm.id ? '编辑租户级 AI 模型配置' : '新增租户级 AI 模型配置' }}</h4>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showConfigModal = false">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block font-semibold text-slate-700 mb-1">配置显示名称</label>
            <input v-model="configForm.name" type="text" placeholder="如 DeepSeek 生产主模型" class="w-full border border-slate-300 rounded-lg p-2.5" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">AI 供应商 (Provider)</label>
              <select v-model="configForm.provider" class="w-full border border-slate-300 rounded-lg p-2.5 bg-white font-semibold">
                <option value="DEEPSEEK">DEEPSEEK</option>
                <option value="OPENAI">OPENAI</option>
                <option value="CLAUDE">CLAUDE</option>
                <option value="QWEN">QWEN (通义千问)</option>
                <option value="CUSTOM">CUSTOM (OpenAI 兼容)</option>
              </select>
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">模型标识 (Model Name)</label>
              <input v-model="configForm.modelName" type="text" placeholder="deepseek-chat / gpt-4o" class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
            </div>
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">API Base URL</label>
            <input v-model="configForm.baseUrl" type="text" placeholder="https://api.deepseek.com/v1" class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">API Key 密钥 (留空则保留原密钥或走语义回退)</label>
            <input v-model="configForm.apiKey" type="password" placeholder="sk-..." class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
          </div>
          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">Temperature</label>
              <input v-model.number="configForm.temperature" type="number" step="0.1" min="0" max="2" class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">Max Tokens</label>
              <input v-model.number="configForm.maxTokens" type="number" step="256" min="256" max="16384" class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">优先级 (越小越高)</label>
              <input v-model.number="configForm.priority" type="number" min="1" max="100" class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
            </div>
          </div>
          <label class="inline-flex items-center gap-2 pt-1 cursor-pointer">
            <input v-model="configForm.isDefault" type="checkbox" class="rounded border-slate-300 text-indigo-600" />
            <span class="font-semibold text-slate-700">设为当前租户默认首选模型</span>
          </label>
        </div>
        <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
          <button type="button" class="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700" @click="showConfigModal = false">取消</button>
          <button type="button" :disabled="savingConfig" class="px-4 py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white" @click="saveConfig">
            {{ savingConfig ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 定制/编辑 Prompt 模板弹窗 -->
    <div v-if="showTemplateModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h4 class="text-sm font-bold text-slate-900">定制 / 覆写业务结构化 Prompt 模板</h4>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showTemplateModal = false">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">模板标识 (Template Key)</label>
              <input v-model="templateForm.templateKey" type="text" placeholder="CREATIVE_DIAGNOSIS" class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">场景标题</label>
              <input v-model="templateForm.title" type="text" placeholder="短视频素材衰退智能诊断" class="w-full border border-slate-300 rounded-lg p-2.5" />
            </div>
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">System Prompt (角色指令)</label>
            <textarea v-model="templateForm.systemPrompt" rows="2" class="w-full border border-slate-300 rounded-lg p-2.5"></textarea>
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">User Prompt 模板 (支持 &#123;&#123;variable&#125;&#125; 占位符)</label>
            <textarea v-model="templateForm.userPromptTemplate" rows="4" class="w-full font-mono border border-slate-300 rounded-lg p-2.5"></textarea>
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
          <button type="button" class="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700" @click="showTemplateModal = false">取消</button>
          <button type="button" :disabled="savingTemplate" class="px-4 py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white" @click="saveTemplate">
            {{ savingTemplate ? '保存中...' : '保存模板覆写' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Prompt 模板变量渲染预览弹窗 -->
    <div v-if="showRenderModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h4 class="text-sm font-bold text-slate-900">🧪 Prompt 模板变量渲染演练 ({{ renderForm.templateKey }})</h4>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showRenderModal = false">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block font-semibold text-slate-700 mb-1">输入测试变量 (JSON 格式)</label>
            <textarea v-model="renderForm.variablesJson" rows="4" class="w-full font-mono border border-slate-300 rounded-lg p-2.5"></textarea>
          </div>
          <button
            type="button"
            class="w-full py-2 text-xs font-bold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors"
            @click="executeRenderPreview"
          >
            执行变量注入渲染
          </button>
          <div v-if="renderedPreview" class="p-3 bg-slate-900 text-slate-100 rounded-lg font-mono text-[11px] whitespace-pre-wrap">{{ renderedPreview }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const configs = ref<any[]>([])
const templates = ref<any[]>([])
const stats = ref<any>({})
const logs = ref<any[]>([])

const showConfigModal = ref(false)
const savingConfig = ref(false)
const showTemplateModal = ref(false)
const savingTemplate = ref(false)
const showRenderModal = ref(false)
const renderedPreview = ref('')
const chatLoading = ref(false)
const chatReply = ref<any>(null)

const configForm = ref<any>({
  id: '',
  name: '',
  provider: 'DEEPSEEK',
  modelName: 'deepseek-chat',
  baseUrl: 'https://api.deepseek.com/v1',
  apiKey: '',
  temperature: 0.7,
  maxTokens: 2048,
  priority: 1,
  isDefault: true,
  active: true
})

const templateForm = ref({
  templateKey: '',
  title: '',
  systemPrompt: '',
  userPromptTemplate: ''
})

const renderForm = ref({
  templateKey: '',
  variablesJson: '{\n  "materialId": "mat_901",\n  "ctr": "0.82%",\n  "dropOff3s": "78%"\n}'
})

const chatForm = ref({
  callerModule: 'CREATIVE',
  prompt: '请分析前3秒跳出率过高的短视频素材并提供黄金Hook重构方案'
})

const openNewConfigModal = () => {
  configForm.value = {
    id: '',
    name: '',
    provider: 'DEEPSEEK',
    modelName: 'deepseek-chat',
    baseUrl: 'https://api.deepseek.com/v1',
    apiKey: '',
    temperature: 0.7,
    maxTokens: 2048,
    priority: 1,
    isDefault: true,
    active: true
  }
  showConfigModal.value = true
}

const openEditConfigModal = (cfg: any) => {
  configForm.value = {
    id: cfg.id,
    name: cfg.name,
    provider: cfg.provider || 'DEEPSEEK',
    modelName: cfg.modelName,
    baseUrl: cfg.baseUrl,
    apiKey: '',
    temperature: cfg.temperature ?? 0.7,
    maxTokens: cfg.maxTokens ?? 2048,
    priority: cfg.priority ?? 1,
    isDefault: !!cfg.isDefault,
    active: cfg.active !== false
  }
  showConfigModal.value = true
}

const openNewTemplateModal = () => {
  templateForm.value = {
    templateKey: 'CUSTOM_AD_DIAGNOSIS',
    title: '自定义广告经营策略模板',
    systemPrompt: '你是资深数字广告算法架构师与增长顾问。',
    userPromptTemplate: '请针对目标市场 {{market}} 与产品 {{product}} 制定投放优化建议。'
  }
  showTemplateModal.value = true
}

const openEditTemplateModal = (tpl: any) => {
  templateForm.value = {
    templateKey: tpl.templateKey,
    title: tpl.title,
    systemPrompt: tpl.systemPrompt,
    userPromptTemplate: tpl.userPromptTemplate
  }
  showTemplateModal.value = true
}

const openRenderModal = (tpl: any) => {
  renderForm.value = {
    templateKey: tpl.templateKey,
    variablesJson: '{\n  "materialId": "mat_hook_01",\n  "ctr": "0.85%",\n  "cvr": "1.12%",\n  "dropOff3s": "76%",\n  "roi": "0.68"\n}'
  }
  renderedPreview.value = ''
  showRenderModal.value = true
}

const refreshAll = async () => {
  await Promise.all([loadConfigs(), loadTemplates(), loadStatsAndLogs()])
}

const loadConfigs = async () => {
  try {
    configs.value = await fetchApi<any[]>('/api/v1/ai/configs') || []
  } catch (e) {
    configs.value = []
  }
}

const loadTemplates = async () => {
  try {
    templates.value = await fetchApi<any[]>('/api/v1/ai/templates') || []
  } catch (e) {
    templates.value = []
  }
}

const loadStatsAndLogs = async () => {
  try {
    stats.value = await fetchApi<any>('/api/v1/ai/stats') || {}
    logs.value = await fetchApi<any[]>('/api/v1/ai/logs?limit=20') || []
  } catch (e) {
    stats.value = {}
    logs.value = []
  }
}

const saveConfig = async () => {
  if (!configForm.value.name) {
    showToast('请填写模型配置名称', 'warning')
    return
  }
  savingConfig.value = true
  try {
    await fetchApi('/api/v1/ai/configs', {
      method: 'POST',
      body: configForm.value
    })
    showToast('AI 模型配置已保存', 'success')
    showConfigModal.value = false
    await loadConfigs()
  } catch (err: any) {
    showToast(`保存失败：${err.message || err}`, 'error')
  } finally {
    savingConfig.value = false
  }
}

const saveTemplate = async () => {
  if (!templateForm.value.templateKey || !templateForm.value.userPromptTemplate) {
    showToast('请填写模板 Key 与 Prompt 内容', 'warning')
    return
  }
  savingTemplate.value = true
  try {
    await fetchApi('/api/v1/ai/templates', {
      method: 'POST',
      body: templateForm.value
    })
    showToast(`模板 ${templateForm.value.templateKey} 已保存覆写`, 'success')
    showTemplateModal.value = false
    await loadTemplates()
  } catch (err: any) {
    showToast(`保存模板失败：${err.message || err}`, 'error')
  } finally {
    savingTemplate.value = false
  }
}

const executeRenderPreview = async () => {
  try {
    const vars = JSON.parse(renderForm.value.variablesJson || '{}')
    const res = await fetchApi<any>('/api/v1/ai/templates/render', {
      method: 'POST',
      body: {
        templateKey: renderForm.value.templateKey,
        variables: vars
      }
    })
    renderedPreview.value = res?.renderedPrompt || ''
  } catch (err: any) {
    showToast(`渲染失败，请检查 JSON 格式：${err.message || err}`, 'error')
  }
}

const setDefaultConfig = async (id: string) => {
  try {
    await fetchApi(`/api/v1/ai/configs/${id}/set-default`, { method: 'PUT' })
    showToast('已更新默认首选模型', 'success')
    await loadConfigs()
  } catch (err: any) {
    showToast(`设置默认失败：${err.message || err}`, 'error')
  }
}

const testModelConfig = async (id: string) => {
  try {
    const res = await fetchApi<any>(`/api/v1/ai/configs/${id}/test`, { method: 'POST' })
    if (res?.success) {
      showToast('模型端点连通性测试通过', 'success')
    } else {
      showToast('未配置外部有效 API Key，系统将自动启用高可用领域语义推理回退引擎', 'warning', 4000)
    }
  } catch (err: any) {
    showToast(`测试失败：${err.message || err}`, 'error')
  }
}

const deleteConfig = async (id: string) => {
  try {
    await fetchApi(`/api/v1/ai/configs/${id}`, { method: 'DELETE' })
    showToast('配置已删除', 'success')
    await loadConfigs()
  } catch (err: any) {
    showToast(`删除失败：${err.message || err}`, 'error')
  }
}

const sendTestChat = async () => {
  if (!chatForm.value.prompt) return
  chatLoading.value = true
  try {
    chatReply.value = await fetchApi<any>('/api/v1/ai/chat', {
      method: 'POST',
      body: {
        callerModule: chatForm.value.callerModule,
        messages: [
          { role: 'system', content: 'You are an expert ad-tech AI assistant.' },
          { role: 'user', content: chatForm.value.prompt }
        ]
      }
    })
    await loadStatsAndLogs()
  } catch (err: any) {
    showToast(`推理请求失败：${err.message || err}`, 'error')
  } finally {
    chatLoading.value = false
  }
}

onMounted(() => {
  refreshAll()
})
</script>
