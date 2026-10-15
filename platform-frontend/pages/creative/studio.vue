<template>
  <div class="space-y-6">
    <!-- 头部操作区 -->
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-lg font-bold text-slate-900">AI 创意与长视频混剪工作台 (AI Creative Studio & CapCut Pipeline)</h3>
        <p class="text-xs text-slate-500">行业剧本智能规划、分镜生成、多模型生成 (Seedance 2.0 / Wan 2.1) 与剪映/CapCut 轨道草稿一键导出</p>
      </div>
      <div class="flex items-center gap-3">
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
        <span class="text-[10px] text-slate-500 font-mono">Seedance · Wan 2.1 · HappyHorse</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">当前选定项目分镜数</span>
        <div class="text-2xl font-bold text-sky-600 font-mono mt-1">{{ scenes.length }}</div>
        <span class="text-[10px] text-slate-500">60-90s 智能镜头流</span>
      </div>
      <div class="bg-white rounded-xl p-4 border border-slate-200 shadow-sm">
        <span class="text-xs font-semibold text-slate-400">CapCut 草稿导出协议</span>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-1">Ready</div>
        <span class="text-[10px] text-slate-500">原生轨道级 JSON</span>
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
              <span class="text-xs font-bold text-slate-800">{{ p.name }}</span>
              <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-700">{{ p.status || 'DRAFT' }}</span>
            </div>
            <p class="text-[11px] text-slate-500 line-clamp-2">{{ p.description || '无详细描述' }}</p>
            <div class="flex items-center justify-between mt-2 text-[10px] text-slate-400 font-mono">
              <span>时长: {{ p.targetDurationSeconds }}s</span>
              <span>管线: {{ p.pipelineType || 'COMMERCE' }}</span>
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
                <h4 class="text-base font-bold text-slate-900">{{ selectedProject.name }}</h4>
                <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-100 text-indigo-800">
                  {{ selectedProject.pipelineType }}
                </span>
              </div>
              <p class="text-xs text-slate-500 mt-0.5">目标时长: {{ selectedProject.targetDurationSeconds }}秒 · 行业智能分镜流</p>
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
                  #{{ idx + 1 }}
                </div>
                <div class="flex-1 space-y-1 text-xs">
                  <div class="flex items-center justify-between">
                    <span class="font-bold text-slate-800">{{ scene.sceneTitle || `镜头 #${idx + 1}` }}</span>
                    <span class="font-mono text-[10px] text-slate-400">{{ scene.durationSeconds || 5 }}s</span>
                  </div>
                  <p class="text-slate-600 text-[11px]"><strong class="text-slate-700">旁白/台词:</strong> {{ scene.voiceoverText || '无旁白' }}</p>
                  <p class="text-slate-500 text-[11px]"><strong class="text-slate-700">镜头提示词:</strong> {{ scene.visualPrompt || scene.cameraPrompt || '智能匹配背景' }}</p>
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
            <label class="block font-semibold text-slate-700 mb-1">项目名称</label>
            <input v-model="createForm.name" type="text" placeholder="如 2026 夏季爆款跨境数码开箱" class="w-full border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">行业策划管线 (Pipeline)</label>
            <select v-model="createForm.pipelineType" class="w-full border border-slate-300 rounded-lg p-2.5 bg-white focus:ring-1 focus:ring-indigo-500 font-semibold">
              <option value="COMMERCE_UNBOXING">跨境电商开箱带货 (COMMERCE_UNBOXING)</option>
              <option value="BEAUTY_MAKEUP">美妆护肤功效种草 (BEAUTY_MAKEUP)</option>
              <option value="SAAS_SOFTWARE">SaaS 软件功能实操演示 (SAAS_SOFTWARE)</option>
              <option value="GAME_PLAY">手游热血高光集锦 (GAME_PLAY)</option>
            </select>
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">目标成品视频时长 (秒)</label>
            <input v-model="createForm.targetDurationSeconds" type="number" step="15" min="15" max="180" class="w-full font-mono border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500" />
          </div>
          <div>
            <label class="block font-semibold text-slate-700 mb-1">产品核心卖点与脚本创意要求</label>
            <textarea v-model="createForm.description" rows="3" placeholder="填写核心卖点、受众画像、优惠力度等，大模型将自动拆解分镜" class="w-full border border-slate-300 rounded-lg p-2.5 focus:ring-1 focus:ring-indigo-500"></textarea>
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

const showCreateModal = ref(false)
const submittingProject = ref(false)
const generatingStoryboards = ref(false)
const exportingCapcut = ref(false)

const createForm = ref({
  name: '',
  pipelineType: 'COMMERCE_UNBOXING',
  targetDurationSeconds: 60,
  description: ''
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

const loadModels = async () => {
  try {
    const models = await fetchApi<any[]>('/api/v1/creative/ai/models') || []
    aiModels.value = models
  } catch (err: any) {
    aiModels.value = [
      { modelCode: 'ark_seedance_2_0', name: 'Seedance 2.0' },
      { modelCode: 'dashscope_wan_2_1', name: 'Wan 2.1' },
      { modelCode: 'happyhorse_1_1', name: 'HappyHorse 1.1' },
      { modelCode: 'local_mock_video', name: 'Local Mock' }
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
  if (!createForm.value.name) {
    showToast('请填写项目名称', 'warning')
    return
  }
  submittingProject.value = true
  try {
    const p = await fetchApi<any>('/api/v1/creative/studio/projects', {
      method: 'POST',
      body: createForm.value
    })
    showToast(`项目「${p.name}」创建成功`, 'success')
    showCreateModal.value = false
    createForm.value = { name: '', pipelineType: 'COMMERCE_UNBOXING', targetDurationSeconds: 60, description: '' }
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
    a.download = `${selectedProject.value.name}_capcut_draft.json`
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
})
</script>
