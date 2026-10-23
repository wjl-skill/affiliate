<template>
  <div class="space-y-6">
    <!-- 头部操作区 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">AI 创意与长视频混剪工作台 (AI Creative Studio & Autonomous Agent)</h3>
        <p class="text-xs text-slate-500">行业剧本智能规划、分镜生成、自演化 Agent 退化素材重构与剪映/CapCut 轨道草稿一键导出</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200 shadow-sm transition-colors flex items-center gap-1.5"
          @click="showAgentModal = true"
        >
          <span>🤖 AI 自主诊断与衍生重构</span>
        </button>
        <button
          type="button"
          class="px-4 py-2 text-xs font-semibold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
          @click="showCreateModal = true"
        >
          <span>🎬 新建智能混剪项目</span>
        </button>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">进行中混剪项目</span>
        <div class="text-2xl font-bold text-slate-800 font-mono mt-1">{{ projects.length }}</div>
        <span class="text-[10px] text-slate-500">多模态生成管线</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">已接入 AI 视频模型</span>
        <div class="text-2xl font-bold text-indigo-600 font-mono mt-1">{{ aiModels.length }}</div>
        <span class="text-[10px] text-slate-500 font-mono">Seedance · Wan 2.1 · Kling</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">当前选定项目分镜数</span>
        <div class="text-2xl font-bold text-sky-600 font-mono mt-1">{{ scenes.length }}</div>
        <span class="text-[10px] text-slate-500">60-90s 智能镜头流</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">AI Agent 自演化动作数</span>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-1">{{ agentActions.length }}</div>
        <span class="text-[10px] text-slate-500">闭环诊断 + A/B 流量赛马</span>
      </div>
    </div>

    <!-- 项目选择与分镜剧本主工作台 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 左侧：项目列表 -->
      <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
        <div class="p-4 border-b border-slate-200 flex items-center justify-between">
          <h4 class="text-sm font-bold text-slate-800">混剪项目列表</h4>
          <button type="button" class="text-xs text-indigo-600 hover:text-indigo-800 font-semibold" @click="loadProjects">
            🔄 刷新
          </button>
        </div>
        <div class="flex-1 divide-y divide-slate-100 max-h-[600px] overflow-y-auto">
          <div
            v-for="p in projects"
            :key="p.id"
            class="p-4 cursor-pointer transition-colors"
            :class="selectedProject?.id === p.id ? 'bg-indigo-50/60 border-l-4 border-indigo-600' : 'hover:bg-slate-50'"
            @click="selectProject(p)"
          >
            <div class="flex items-center justify-between mb-1">
              <span class="text-xs font-bold text-slate-800">{{ p.title || p.name }}</span>
              <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700">{{ p.status || 'DRAFT' }}</span>
            </div>
            <p class="text-[11px] text-slate-500 line-clamp-2">{{ p.brief || p.description || '无详细描述' }}</p>
            <div class="flex items-center justify-between mt-2 text-[10px] text-slate-400 font-mono">
              <span>时长: {{ p.durationSeconds || p.targetDurationSeconds || 60 }}s</span>
              <span>管线: {{ p.adType || p.pipelineType || 'COMMERCE_UNBOXING' }}</span>
            </div>
          </div>
          <div v-if="projects.length === 0" class="p-8 text-center text-xs text-slate-400">
            暂无项目，点击右上角新建
          </div>
        </div>
      </div>

      <!-- 右侧：分镜剧本与剪映导出详情 -->
      <div class="lg:col-span-2 space-y-4">
        <div v-if="selectedProject" class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
          <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-200">
            <div>
              <div class="flex items-center gap-2">
                <h4 class="text-base font-bold text-slate-900">{{ selectedProject.title || selectedProject.name }}</h4>
                <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-100 text-indigo-800">
                  {{ selectedProject.adType || selectedProject.pipelineType || 'COMMERCE_UNBOXING' }}
                </span>
                <span class="px-2 py-0.5 rounded text-[10px] font-mono bg-slate-100 text-slate-600">
                  {{ selectedProject.modelCode || 'ark_seedance_2_0' }}
                </span>
              </div>
              <p class="text-xs text-slate-500 mt-0.5">
                目标时长: {{ selectedProject.durationSeconds || selectedProject.targetDurationSeconds || 60 }}秒 · 比例: {{ selectedProject.aspectRatio || '9:16' }} · 平台: {{ selectedProject.targetPlatform || 'TIKTOK' }}
              </p>
            </div>
            <div class="flex items-center gap-2">
              <button
                type="button"
                :disabled="generatingStoryboards"
                class="px-3.5 py-1.5 text-xs font-bold rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 transition-colors disabled:opacity-50"
                @click="generateStoryboards"
              >
                {{ generatingStoryboards ? '生成分镜中...' : '🤖 智能规划分镜' }}
              </button>
              <button
                type="button"
                :disabled="exportingCapcut"
                class="px-3.5 py-1.5 text-xs font-bold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm disabled:opacity-50"
                @click="exportCapCutDraft"
              >
                {{ exportingCapcut ? '导出中...' : '📥 导出剪映/CapCut草稿' }}
              </button>
            </div>
          </div>

          <!-- 分镜列表 -->
          <div>
            <h5 class="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3">分镜剧本与镜头画面规划 (Storyboards)</h5>
            <div v-if="scenes.length > 0" class="space-y-3">
              <div
                v-for="(scene, idx) in scenes"
                :key="scene.id || idx"
                class="p-3.5 rounded-lg border border-slate-200 bg-slate-50/60 flex items-start gap-3"
              >
                <div class="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 font-bold text-xs flex items-center justify-center flex-shrink-0 font-mono">
                  #{{ scene.sceneIndex || idx + 1 }}
                </div>
                <div class="flex-1 space-y-1 text-xs">
                  <div class="flex items-center justify-between">
                    <span class="font-bold text-slate-800">{{ scene.cameraMovement || scene.sceneTitle || `镜头 #${idx + 1}` }}</span>
                    <span class="font-mono text-[10px] text-slate-400">{{ scene.durationSeconds || 5 }}s · {{ scene.status || 'READY' }}</span>
                  </div>
                  <p class="text-slate-600 text-[11px]"><strong class="text-slate-700">旁白/字幕:</strong> {{ scene.subtitleText || scene.voiceoverText || '无旁白' }}</p>
                  <p class="text-slate-500 text-[11px]"><strong class="text-slate-700">画面提示词:</strong> {{ scene.shotPrompt || scene.visualPrompt || scene.cameraPrompt || '智能匹配背景' }}</p>
                </div>
              </div>
            </div>
            <div v-else class="p-8 text-center text-xs text-slate-400 bg-slate-50 rounded-lg border border-dashed border-slate-300">
              当前项目尚未生成分镜剧本，请点击上方「智能规划分镜」一键生成
            </div>
          </div>
        </div>

        <div v-else class="bg-white rounded-xl border border-slate-200 p-12 text-center text-xs text-slate-400">
          👈 请在左侧选择一个项目查看详情与分镜剧本
        </div>

        <!-- AI Agent 历史自演化记录 -->
        <div v-if="agentActions.length > 0" class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-3">
          <div class="flex items-center justify-between">
            <h5 class="text-xs font-bold text-slate-800 uppercase tracking-wider">AI Creative Agent 自演化与 A/B 赛马日志</h5>
            <span class="text-[11px] text-slate-400">自动接入 AdLearningRuleEngine 20% 流量实验</span>
          </div>
          <div class="divide-y divide-slate-100 max-h-60 overflow-y-auto text-xs">
            <div v-for="act in agentActions" :key="act.actionId" class="py-2.5 flex items-start justify-between gap-4">
              <div class="space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-mono font-bold text-indigo-700">{{ act.materialId || act.sourceMaterialId }}</span>
                  <span class="text-slate-400">→</span>
                  <span class="font-mono font-semibold text-emerald-700">{{ act.newProjectId || act.spawnedStudioProjectId }}</span>
                  <span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-800">{{ act.status }}</span>
                  <span v-if="act.capCutDraftPath" class="text-[10px] font-mono text-slate-400">Draft: {{ act.capCutDraftPath }}</span>
                </div>
                <p class="text-[11px] text-slate-600 line-clamp-2">{{ act.revisedPrompt || act.refinedPrompt }}</p>
                <p v-if="act.refinedOpeningHook" class="text-[10px] text-amber-700 font-medium">🎯 黄金前3秒 Hook: {{ act.refinedOpeningHook }}</p>
              </div>
              <span class="text-[10px] font-mono text-slate-400 whitespace-nowrap">{{ act.createdAt ? new Date(act.createdAt).toLocaleString() : act.triggeredAt }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建项目模态框 -->
    <div v-if="showCreateModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h4 class="text-sm font-bold text-slate-900">新建智能长视频混剪项目</h4>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showCreateModal = false">✕</button>
        </div>

        <div class="space-y-3 text-xs">
          <div>
            <label class="block font-semibold text-slate-700 mb-1">项目名称 (Title)</label>
            <input v-model="createForm.title" type="text" placeholder="如 2026 夏季爆款跨境数码开箱" class="w-full border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">行业策划管线 (Ad Type)</label>
              <select v-model="createForm.adType" class="w-full border border-slate-300 rounded-lg p-2.5 bg-white focus:ring-1 focus:ring-indigo-500 font-semibold">
                <option value="COMMERCE_UNBOXING">跨境电商开箱带货</option>
                <option value="SKINCARE_HERO">美妆护肤功效种草</option>
                <option value="APP_PROMO">应用/SaaS 实操演示</option>
                <option value="MINI_DRAMA">短剧反转高光集锦</option>
              </select>
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">AI 生成底座模型</label>
              <select v-model="createForm.modelCode" class="w-full border border-slate-300 rounded-lg p-2.5 bg-white focus:ring-1 focus:ring-indigo-500 font-mono">
                <option v-for="m in aiModels" :key="m.modelCode" :value="m.modelCode">{{ m.displayName || m.name || m.modelCode }}</option>
              </select>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-3">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">目标时长 (秒)</label>
              <input v-model.number="createForm.durationSeconds" type="number" step="15" min="15" max="180" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">画面比例</label>
              <select v-model="createForm.aspectRatio" class="w-full border border-slate-300 rounded-lg p-2.5 bg-white font-mono">
                <option value="9:16">9:16 (竖屏)</option>
                <option value="16:9">16:9 (横屏)</option>
                <option value="1:1">1:1 (方屏)</option>
              </select>
            </div>
            <div>
              <label class="block font-semibold text-slate-700 mb-1">投放平台</label>
              <select v-model="createForm.targetPlatform" class="w-full border border-slate-300 rounded-lg p-2.5 bg-white font-semibold">
                <option value="TIKTOK">TikTok</option>
                <option value="META_REELS">Meta Reels</option>
                <option value="YOUTUBE_SHORTS">YouTube Shorts</option>
              </select>
            </div>
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">产品核心卖点与脚本创意要求 (Brief)</label>
            <textarea v-model="createForm.brief" rows="3" placeholder="填写核心卖点、受众画像、优惠力度等，大模型将自动拆解分镜" class="w-full border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500"></textarea>
          </div>
        </div>

        <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
          <button type="button" class="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700" @click="showCreateModal = false">取消</button>
          <button type="button" :disabled="submittingProject" class="px-4 py-2 text-xs font-bold rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white disabled:bg-slate-300" @click="submitProject">
            {{ submittingProject ? '创建中...' : '立即创建' }}
          </button>
        </div>
      </div>
    </div>

    <!-- AI Agent 自主诊断与重构模态框 -->
    <div v-if="showAgentModal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div class="bg-white rounded-2xl border border-slate-200 shadow-2xl max-w-lg w-full p-6 space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h4 class="text-sm font-bold text-slate-900">🤖 触发 AI Creative Agent 自演化重构</h4>
          <button type="button" class="text-slate-400 hover:text-slate-600" @click="showAgentModal = false">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block font-semibold text-slate-700 mb-1">待诊断衰退素材 ID</label>
            <input v-model="agentForm.materialId" type="text" placeholder="如 mat_hook_drop_01" class="w-full font-mono border border-slate-300 rounded-lg p-2.5" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">目标受众画像</label>
            <input v-model="agentForm.targetAudience" type="text" placeholder="如 US Tier-1 Gamers 18-30" class="w-full border border-slate-300 rounded-lg p-2.5" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">衰退诱因诊断 (Diagnosis Reason)</label>
            <input v-model="agentForm.diagnosisReason" type="text" placeholder="如 前3秒跳出率 > 78%，CTR 低于大盘均值" class="w-full border border-slate-300 rounded-lg p-2.5" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">原始脚本/提示词</label>
            <textarea v-model="agentForm.basePrompt" rows="3" class="w-full border border-slate-300 rounded-lg p-2.5"></textarea>
          </div>
        </div>
        <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
          <button type="button" class="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700" @click="showAgentModal = false">取消</button>
          <button type="button" :disabled="runningAgent" class="px-4 py-2 text-xs font-bold rounded-lg bg-amber-600 hover:bg-amber-700 text-white disabled:bg-slate-300" @click="triggerAutonomousAgent">
            {{ runningAgent ? 'Agent 演化中...' : '启动诊断并自动建项' }}
          </button>
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

const projects = ref<any[]>([])
const selectedProject = ref<any>(null)
const scenes = ref<any[]>([])
const aiModels = ref<any[]>([])
const agentActions = ref<any[]>([])

const showCreateModal = ref(false)
const showAgentModal = ref(false)
const submittingProject = ref(false)
const generatingStoryboards = ref(false)
const exportingCapcut = ref(false)
const runningAgent = ref(false)

const createForm = ref({
  title: '',
  adType: 'COMMERCE_UNBOXING',
  durationSeconds: 60,
  aspectRatio: '9:16',
  targetPlatform: 'TIKTOK',
  creativeStyle: 'CYBERPUNK_ENERGETIC',
  modelCode: 'ark_seedance_2_0',
  brief: ''
})

const agentForm = ref({
  materialId: 'mat_degraded_001',
  targetAudience: 'US Gen-Z Mobile Users',
  diagnosisReason: 'HIGH_HOOK_DROP_OFF (3s Drop > 78%)',
  basePrompt: 'Standard product intro without emotional hook'
})

const loadProjects = async () => {
  try {
    const list = await fetchApi<any[]>('/api/v1/creative/studio/projects?limit=50') || []
    projects.value = list
    if (list.length > 0 && !selectedProject.value) {
      selectProject(list[0])
    }
  } catch (err: any) {
    projects.value = []
  }
}

const loadAgentActions = async () => {
  try {
    const list = await fetchApi<any[]>('/api/v1/creative/agent/actions') || []
    agentActions.value = list
  } catch (err: any) {
    agentActions.value = []
  }
}

const loadModels = async () => {
  try {
    const models = await fetchApi<any[]>('/api/v1/creative/ai/models') || []
    aiModels.value = models
  } catch (err: any) {
    aiModels.value = [
      { modelCode: 'ark_seedance_2_0', displayName: 'Seedance 2.0' },
      { modelCode: 'dashscope_wan_2_1', displayName: 'Wan 2.1' },
      { modelCode: 'kling_v1', displayName: 'Kling v1' },
      { modelCode: 'local_mock_video', displayName: 'Local Mock' }
    ]
  }
}

const selectProject = async (p: any) => {
  selectedProject.value = p
  try {
    const sceneList = await fetchApi<any[]>(`/api/v1/creative/studio/projects/${p.id}/scenes`) || []
    scenes.value = sceneList
  } catch (err: any) {
    scenes.value = []
  }
}

const submitProject = async () => {
  if (!createForm.value.title || !createForm.value.brief) {
    showToast('请填写项目名称和创意卖点描述 (Brief)', 'warning')
    return
  }
  submittingProject.value = true
  try {
    const p = await fetchApi<any>('/api/v1/creative/studio/projects', {
      method: 'POST',
      body: createForm.value
    })
    showToast(`项目「${p.title || createForm.value.title}」创建成功`, 'success')
    showCreateModal.value = false
    createForm.value = {
      title: '',
      adType: 'COMMERCE_UNBOXING',
      durationSeconds: 60,
      aspectRatio: '9:16',
      targetPlatform: 'TIKTOK',
      creativeStyle: 'CYBERPUNK_ENERGETIC',
      modelCode: 'ark_seedance_2_0',
      brief: ''
    }
    await loadProjects()
    if (p) {
      selectProject(p)
    }
  } catch (err: any) {
    showToast(`创建失败：${err.message || err}`, 'error', 5000)
  } finally {
    submittingProject.value = false
  }
}

const triggerAutonomousAgent = async () => {
  runningAgent.value = true
  try {
    const action = await fetchApi<any>('/api/v1/creative/agent/trigger', {
      method: 'POST',
      body: {
        materialId: agentForm.value.materialId,
        level: 'RISK',
        dropOff3s: 0.78,
        ctr: 0.0082,
        cvr: 0.0105,
        roi: 0.65,
        prompt: `${agentForm.value.basePrompt} | Audience: ${agentForm.value.targetAudience} | Reason: ${agentForm.value.diagnosisReason}`,
        targetAudience: agentForm.value.targetAudience,
        diagnosisReason: agentForm.value.diagnosisReason,
        basePrompt: agentForm.value.basePrompt
      }
    })
    showToast(`AI Agent 已生成衍生混剪工程：${action.newProjectId || action.spawnedStudioProjectId}`, 'success')
    showAgentModal.value = false
    await Promise.all([loadProjects(), loadAgentActions()])
  } catch (err: any) {
    showToast(`Agent 执行失败：${err.message || err}`, 'error', 5000)
  } finally {
    runningAgent.value = false
  }
}

const generateStoryboards = async () => {
  if (!selectedProject.value) return
  generatingStoryboards.value = true
  try {
    const list = await fetchApi<any[]>(`/api/v1/creative/studio/projects/${selectedProject.value.id}/storyboards/generate`, {
      method: 'POST'
    })
    scenes.value = list || []
    showToast(`成功规划生成 ${scenes.value.length} 个镜头分镜`, 'success')
  } catch (err: any) {
    showToast(`生成分镜失败：${err.message || err}`, 'error', 5000)
  } finally {
    generatingStoryboards.value = false
  }
}

const exportCapCutDraft = async () => {
  if (!selectedProject.value) return
  exportingCapcut.value = true
  try {
    const draft = await fetchApi<any>(`/api/v1/creative/studio/projects/${selectedProject.value.id}/export-capcut`, {
      method: 'POST'
    })
    const jsonStr = typeof draft === 'string' ? draft : JSON.stringify(draft, null, 2)
    const blob = new Blob([jsonStr], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${selectedProject.value.title || selectedProject.value.name || 'studio'}_capcut_draft.json`
    a.click()
    URL.revokeObjectURL(url)
    showToast('剪映/CapCut 轨道级草稿已成功导出', 'success')
  } catch (err: any) {
    showToast(`导出草稿失败：${err.message || err}`, 'error', 5000)
  } finally {
    exportingCapcut.value = false
  }
}

onMounted(() => {
  loadProjects()
  loadModels()
  loadAgentActions()
})
</script>
