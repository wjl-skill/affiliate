<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">动态菜单管理 (Menu Management)</h3>
        <p class="text-xs text-slate-500">配置平台侧边栏层级结构、路由路径、图标与对应的权限控制标识</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showCreateModal = true"
      >
        <span>➕</span>
        <span>添加菜单节点</span>
      </button>
    </div>

    <!-- 菜单表格 -->
    <CommonTable
      :columns="columns"
      :data="menus"
      :loading="loading"
      search-placeholder="搜索菜单名称或路径..."
    >
      <template #cell-menuTitle="{ row }">
        <div class="flex items-center gap-2">
          <span class="text-base">{{ row.icon || '📄' }}</span>
          <span class="font-bold text-slate-900">{{ row.title }}</span>
          <span class="text-[10px] text-slate-400 font-mono">({{ row.id }})</span>
        </div>
      </template>

      <template #cell-path="{ row }">
        <span class="text-xs font-mono text-slate-700">{{ row.path }}</span>
      </template>

      <template #cell-perm="{ row }">
        <span v-if="row.permissionCode" class="px-2 py-0.5 rounded text-[10px] font-mono font-semibold bg-slate-100 text-slate-700 border border-slate-200">
          {{ row.permissionCode }}
        </span>
        <span v-else class="text-slate-400 italic text-[11px]">公开可见</span>
      </template>

      <template #cell-sort="{ row }">
        <span class="text-xs font-mono font-bold text-slate-700">{{ row.sortOrder }}</span>
      </template>

      <template #cell-status="{ row }">
        <StatusTag :status="row.status" />
      </template>

      <template #cell-actions="{ row }">
        <button
          type="button"
          class="px-2 py-1 text-xs font-medium rounded text-rose-700 bg-rose-50 hover:bg-rose-100"
          @click="deleteMenu(row.id)"
        >
          删除
        </button>
      </template>
    </CommonTable>

    <!-- 新建菜单弹窗 -->
    <ModalDialog
      :show="showCreateModal"
      title="新建菜单节点 (New Menu Node)"
      confirm-text="保存菜单"
      @close="showCreateModal = false"
      @confirm="submitCreateMenu"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">菜单 ID</label>
          <input
            v-model="createForm.id"
            type="text"
            placeholder="留空则由服务端自动生成"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">菜单标题 <span class="text-rose-500">*</span></label>
            <input
              v-model="createForm.title"
              type="text"
              required
              placeholder="例如: 智能风控大盘"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">菜单图标 (Emoji 或 图标名)</label>
            <input
              v-model="createForm.icon"
              type="text"
              placeholder="例如: 🛡️"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
            />
          </div>
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">路由跳转路径 (Route Path) <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.path"
            type="text"
            required
            placeholder="例如: /risk/overview"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">前端组件相对路径</label>
          <input
            v-model="createForm.component"
            type="text"
            placeholder="例如: pages/risk/overview.vue"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-700 mb-1">挂接权限码 (可选)</label>
            <input
              v-model="createForm.permissionCode"
              type="text"
              placeholder="例如: risk:view"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
            />
          </div>
          <div>
            <label class="block font-medium text-slate-700 mb-1">排序优先级 (数字越小越靠前)</label>
            <input
              v-model.number="createForm.sortOrder"
              type="number"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
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
import StatusTag from '~/components/common/StatusTag.vue'
import ModalDialog from '~/components/common/ModalDialog.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'
import { flattenMenuTree } from '~/utils/menu'

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const showCreateModal = ref(false)
const menus = ref<any[]>([])

const createForm = ref({
  id: '',
  parentId: '0',
  title: '',
  icon: '⚡',
  path: '',
  component: '',
  permissionCode: '',
  sortOrder: 20
})

const columns = [
  { key: 'menuTitle', label: '菜单节点 (Title / Icon)' },
  { key: 'path', label: '路由路径 (Path)' },
  { key: 'perm', label: '访问权限码' },
  { key: 'sort', label: '排序' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const loadMenus = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/system/menus')
    menus.value = flattenMenuTree(res || [])
  } catch (err: any) {
    menus.value = []
    showToast(`加载菜单树失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const deleteMenu = async (id: string) => {
  try {
    await fetchApi(`/api/v1/system/menus/${id}`, { method: 'DELETE' })
    await loadMenus()
    showToast('菜单已删除', 'success')
  } catch (err: any) {
    showToast(`菜单删除失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

const submitCreateMenu = async () => {
  if (!createForm.value.title || !createForm.value.path) {
    showToast('请填写菜单标题与路由路径', 'warning')
    return
  }
  const payload = {
    ...createForm.value,
    visible: true,
    status: 'ACTIVE'
  }

  try {
    await fetchApi<any>('/api/v1/system/menus', {
      method: 'POST',
      body: payload
    })
    showCreateModal.value = false
    showToast('菜单节点添加成功！', 'success')
    await loadMenus()
  } catch (err: any) {
    showToast(`菜单保存失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

onMounted(() => {
  loadMenus()
})
</script>
