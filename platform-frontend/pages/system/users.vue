<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">系统用户管理 (User Management)</h3>
        <p class="text-xs text-slate-500">维护系统管理员、商务主管、财务及运营账号全生命周期与权限分配</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showCreateModal = true"
      >
        <span>➕</span>
        <span>新建系统用户</span>
      </button>
    </div>

    <!-- 用户数据表格 -->
    <CommonTable
      :columns="columns"
      :data="users"
      :loading="loading"
      search-placeholder="搜索账号名、姓名或邮箱..."
    >
      <!-- 用户头像与基本信息 -->
      <template #cell-userInfo="{ row }">
        <div class="flex items-center gap-3">
          <img :src="row.avatar || 'https://api.dicebear.com/7.x/bottts/svg?seed=' + row.username" class="w-8 h-8 rounded-full border border-slate-200 bg-slate-100" />
          <div class="flex flex-col">
            <span class="font-bold text-slate-900">{{ row.displayName || row.username }}</span>
            <span class="text-[11px] text-slate-400 font-mono">@{{ row.username }}</span>
          </div>
        </div>
      </template>

      <!-- 联系方式 -->
      <template #cell-contact="{ row }">
        <div class="text-xs">
          <span class="text-slate-800 font-medium">{{ row.email }}</span>
          <span v-if="row.phone" class="text-slate-400 block text-[11px] font-mono">{{ row.phone }}</span>
        </div>
      </template>

      <!-- 角色标签 -->
      <template #cell-roles="{ row }">
        <div class="flex flex-wrap gap-1">
          <span
            v-for="role in row.roles"
            :key="role"
            class="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider"
            :class="role === 'SUPER_ADMIN' ? 'bg-indigo-50 text-indigo-700 border border-indigo-200' : 'bg-slate-100 text-slate-700 border border-slate-200'"
          >
            {{ role }}
          </span>
        </div>
      </template>

      <!-- 状态 -->
      <template #cell-status="{ row }">
        <StatusTag :status="row.status" />
      </template>

      <!-- 最近登录 -->
      <template #cell-lastLogin="{ row }">
        <span class="text-xs text-slate-500 font-mono">
          {{ row.lastLoginAt ? formatDate(row.lastLoginAt) : '从未登录' }}
        </span>
      </template>

      <!-- 操作按钮 -->
      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="px-2 py-1 text-xs font-medium rounded border border-slate-200 text-slate-700 hover:bg-slate-50"
            @click="openRolesModal(row)"
          >
            分配角色
          </button>
          <button
            v-if="row.status === 'ACTIVE'"
            type="button"
            :disabled="row.username === 'admin'"
            class="px-2 py-1 text-xs font-medium rounded bg-rose-50 text-rose-700 hover:bg-rose-100 disabled:opacity-40"
            @click="toggleStatus(row, 'DISABLED')"
          >
            禁用
          </button>
          <button
            v-else
            type="button"
            class="px-2 py-1 text-xs font-medium rounded bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
            @click="toggleStatus(row, 'ACTIVE')"
          >
            启用
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 弹窗 1: 新建系统用户 -->
    <ModalDialog
      :show="showCreateModal"
      title="新建系统账号 (New User Account)"
      confirm-text="创建用户"
      @close="showCreateModal = false"
      @confirm="submitCreate"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">登录账号名 <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.username"
            type="text"
            required
            placeholder="例如: david_ops"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">显示姓名 <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.displayName"
            type="text"
            required
            placeholder="例如: David Liu"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">工作邮箱 <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.email"
            type="email"
            required
            placeholder="david@affiliate.io"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">联系电话</label>
          <input
            v-model="createForm.phone"
            type="text"
            placeholder="+1-800-555-0100"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">分配初始角色</label>
          <select v-model="createForm.role" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
            <option value="AFFILIATE_MANAGER">AFFILIATE_MANAGER (网盟商务主管)</option>
            <option value="FINANCE_OFFICER">FINANCE_OFFICER (财务结算专员)</option>
            <option value="TRAFFICKER">TRAFFICKER (广告投放优化师)</option>
            <option value="VIEWER">VIEWER (只读观察员)</option>
          </select>
        </div>
      </div>
    </ModalDialog>

    <!-- 弹窗 2: 重新分配角色 -->
    <ModalDialog
      :show="showRolesModal"
      title="配置用户所属角色"
      confirm-text="保存角色配置"
      @close="showRolesModal = false"
      @confirm="submitReassignRoles"
    >
      <div v-if="selectedUser" class="space-y-4 text-xs">
        <div class="p-3 bg-slate-50 rounded-lg border border-slate-200">
          <span class="text-slate-500">正在配置用户:</span>
          <span class="font-bold text-slate-900 ml-1">{{ selectedUser.displayName }} (@{{ selectedUser.username }})</span>
        </div>

        <div>
          <label class="block font-medium text-slate-700 mb-2">勾选角色权限清单：</label>
          <div class="space-y-2">
            <label
              v-for="r in availableRoles"
              :key="r.code"
              class="flex items-center gap-3 p-2.5 rounded-lg border hover:bg-slate-50 cursor-pointer"
              :class="userRolesSelection.includes(r.code) ? 'border-brand-500 bg-brand-50/30' : 'border-slate-200'"
            >
              <input
                type="checkbox"
                :value="r.code"
                v-model="userRolesSelection"
                class="rounded text-brand-600 focus:ring-brand-500"
              />
              <div>
                <p class="font-bold text-slate-800">{{ r.name }} ({{ r.code }})</p>
                <p class="text-[11px] text-slate-400 mt-0.5">{{ r.desc }}</p>
              </div>
            </label>
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

const { fetchApi } = useApi()
const { showToast } = useToasts()

const loading = ref(false)
const showCreateModal = ref(false)
const showRolesModal = ref(false)
const users = ref<any[]>([])
const selectedUser = ref<any>(null)
const userRolesSelection = ref<string[]>([])

const availableRoles = ref<Array<{ code: string; name: string; desc: string }>>([])

const loadRoles = async () => {
  try {
    const res = await fetchApi<any[]>('/api/v1/system/roles')
    availableRoles.value = (res || []).map(r => ({
      code: r.roleCode,
      name: r.roleName,
      desc: r.description || ''
    }))
  } catch (err: any) {
    showToast(`加载角色清单失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

const createForm = ref({
  username: '',
  displayName: '',
  email: '',
  phone: '',
  role: 'VIEWER'
})

const columns = [
  { key: 'userInfo', label: '用户名称 (Name / ID)' },
  { key: 'contact', label: '联系方式 (Email / 电话)' },
  { key: 'roles', label: '所属角色' },
  { key: 'status', label: '账号状态' },
  { key: 'lastLogin', label: '最近活跃' },
  { key: 'actions', label: '操作' }
]

const formatDate = (isoStr: string) => {
  return new Date(isoStr).toLocaleString()
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/system/users')
    users.value = res || []
  } catch (err: any) {
    users.value = []
    showToast(`加载用户列表失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  } finally {
    loading.value = false
  }
}

const toggleStatus = async (row: any, newStatus: string) => {
  try {
    const updated = await fetchApi<any>(`/api/v1/system/users/${row.id}/status?status=${newStatus}`, { method: 'POST' })
    row.status = updated?.status ?? newStatus
    showToast(`用户 ${row.username} 状态已更新为 ${row.status}`, 'success')
  } catch (err: any) {
    showToast(`状态更新失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

const openRolesModal = (user: any) => {
  selectedUser.value = user
  userRolesSelection.value = [...(user.roles || [])]
  showRolesModal.value = true
}

const submitReassignRoles = async () => {
  if (!selectedUser.value) return
  try {
    const updated = await fetchApi<any>(`/api/v1/system/users/${selectedUser.value.id}/roles`, {
      method: 'POST',
      body: userRolesSelection.value
    })
    selectedUser.value.roles = Array.from(updated?.roles ?? userRolesSelection.value)
    showRolesModal.value = false
    showToast('角色权限已重新生效！', 'success')
  } catch (err: any) {
    showToast(`角色分配失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

const submitCreate = async () => {
  if (!createForm.value.username || !createForm.value.email) {
    showToast('请填写必填的账号名与邮箱', 'warning')
    return
  }
  const payload = {
    username: createForm.value.username,
    displayName: createForm.value.displayName,
    email: createForm.value.email,
    phone: createForm.value.phone,
    roles: [createForm.value.role]
  }

  try {
    const res = await fetchApi<any>('/api/v1/system/users', {
      method: 'POST',
      body: payload
    })
    if (res) users.value.unshift(res)
    showCreateModal.value = false
    showToast('系统用户创建成功！', 'success')
  } catch (err: any) {
    showToast(`用户创建失败：${err?.message || '服务请求失败'}`, 'error', 5000)
  }
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>
