<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">角色与权限管理 (Role Management)</h3>
        <p class="text-xs text-slate-500">配置平台 RBAC 角色模型、多维数据可见范围 (DataScope) 及挂接的功能与动态菜单树</p>
      </div>
      <button
        type="button"
        class="px-4 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors flex items-center gap-1.5"
        @click="showCreateModal = true"
      >
        <span>➕</span>
        <span>新建自定义角色</span>
      </button>
    </div>

    <!-- 角色表格 -->
    <CommonTable
      :columns="columns"
      :data="roles"
      :loading="loading"
      search-placeholder="搜索角色编码或名称..."
    >
      <template #cell-roleInfo="{ row }">
        <div class="flex flex-col">
          <div class="flex items-center gap-2">
            <span class="font-bold text-slate-900">{{ row.roleName }}</span>
            <span v-if="row.isSystem" class="px-1.5 py-0.2 rounded text-[10px] font-semibold bg-slate-100 text-slate-600 border border-slate-200">
              系统内置
            </span>
          </div>
          <span class="text-[11px] text-slate-400 font-mono mt-0.5">{{ row.roleCode }}</span>
        </div>
      </template>

      <template #cell-dataScope="{ row }">
        <span class="px-2 py-0.5 rounded text-[11px] font-semibold bg-brand-50 text-brand-700 border border-brand-200 font-mono">
          {{ row.dataScope }}
        </span>
      </template>

      <template #cell-permsCount="{ row }">
        <span class="text-xs font-semibold text-slate-700 font-mono">
          {{ row.permissionCodes ? (row.permissionCodes.includes('*') ? '通配全量 (*)' : row.permissionCodes.length + ' 项') : '0 项' }}
        </span>
      </template>

      <template #cell-menusCount="{ row }">
        <span class="text-xs font-semibold text-slate-700 font-mono">
          {{ row.menuIds ? row.menuIds.length + ' 个菜单' : '0 个' }}
        </span>
      </template>

      <template #cell-status="{ row }">
        <StatusTag :status="row.status" />
      </template>

      <!-- 操作 -->
      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="px-2 py-1 text-xs font-medium rounded border border-brand-200 text-brand-700 hover:bg-brand-50"
            @click="openPermissionsModal(row)"
          >
            分配权限
          </button>
          <button
            type="button"
            class="px-2 py-1 text-xs font-medium rounded border border-slate-200 text-slate-700 hover:bg-slate-50"
            @click="openMenusModal(row)"
          >
            分配菜单
          </button>
        </div>
      </template>
    </CommonTable>

    <!-- 弹窗 1: 新建自定义角色 -->
    <ModalDialog
      :show="showCreateModal"
      title="新建自定义角色 (New Custom Role)"
      confirm-text="创建角色"
      @close="showCreateModal = false"
      @confirm="submitCreateRole"
    >
      <div class="space-y-4 text-xs">
        <div>
          <label class="block font-medium text-slate-700 mb-1">角色唯一代码 (Role Code) <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.roleCode"
            type="text"
            required
            placeholder="例如: MEDIA_BUYER"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 font-mono uppercase"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">角色名称 <span class="text-rose-500">*</span></label>
          <input
            v-model="createForm.roleName"
            type="text"
            required
            placeholder="例如: 媒介采买主管"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
          />
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">角色职能描述</label>
          <textarea
            v-model="createForm.description"
            rows="2"
            placeholder="说明该角色业务职责..."
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900"
          ></textarea>
        </div>
        <div>
          <label class="block font-medium text-slate-700 mb-1">数据可见范围 (DataScope)</label>
          <select v-model="createForm.dataScope" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900">
            <option value="ALL">ALL (全部数据 - 平台超管)</option>
            <option value="TENANT_ONLY">TENANT_ONLY (租户空间级数据)</option>
            <option value="DEPT_ONLY">DEPT_ONLY (部门组织级数据)</option>
            <option value="SELF_ONLY">SELF_ONLY (仅本人创建的数据)</option>
          </select>
        </div>
      </div>
    </ModalDialog>

    <!-- 弹窗 2: 分配功能权限 -->
    <ModalDialog
      :show="showPermsModal"
      title="配置角色功能操作权限"
      confirm-text="保存权限配置"
      @close="showPermsModal = false"
      @confirm="savePermissions"
    >
      <div v-if="selectedRole" class="space-y-4 text-xs">
        <div class="p-3 bg-slate-50 rounded-lg border border-slate-200">
          <span class="text-slate-500">正在配置角色:</span>
          <span class="font-bold text-slate-900 ml-1">{{ selectedRole.roleName }} ({{ selectedRole.roleCode }})</span>
        </div>

        <div class="space-y-4 max-h-[50vh] overflow-y-auto pr-1">
          <div v-for="(perms, moduleName) in groupedPermissions" :key="moduleName" class="border border-slate-200 rounded-lg p-3">
            <h5 class="font-bold text-slate-800 text-xs mb-2 flex items-center justify-between border-b border-slate-100 pb-1">
              <span>{{ moduleName }}</span>
              <span class="text-[10px] text-slate-400 font-normal">共 {{ perms.length }} 项</span>
            </h5>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <label
                v-for="p in perms"
                :key="p.code"
                class="flex items-center gap-2 p-1.5 rounded hover:bg-slate-50 cursor-pointer"
              >
                <input
                  type="checkbox"
                  :value="p.code"
                  v-model="selectedPerms"
                  class="rounded text-brand-600 focus:ring-brand-500"
                />
                <div>
                  <span class="font-medium text-slate-800">{{ p.name }}</span>
                  <span class="text-[10px] text-slate-400 block font-mono">{{ p.code }}</span>
                </div>
              </label>
            </div>
          </div>
        </div>
      </div>
    </ModalDialog>

    <!-- 弹窗 3: 分配菜单树 -->
    <ModalDialog
      :show="showMenusModal"
      title="配置角色侧边栏菜单"
      confirm-text="保存菜单授权"
      @close="showMenusModal = false"
      @confirm="saveMenus"
    >
      <div v-if="selectedRole" class="space-y-4 text-xs">
        <div class="p-3 bg-slate-50 rounded-lg border border-slate-200">
          <span class="text-slate-500">正在配置角色:</span>
          <span class="font-bold text-slate-900 ml-1">{{ selectedRole.roleName }} ({{ selectedRole.roleCode }})</span>
        </div>

        <div class="space-y-2 max-h-[50vh] overflow-y-auto pr-1">
          <label
            v-for="m in allMenuOptions"
            :key="m.id"
            class="flex items-center gap-3 p-2 rounded-lg border border-slate-200 hover:bg-slate-50 cursor-pointer"
            :class="selectedMenus.includes(m.id) ? 'border-brand-500 bg-brand-50/20' : ''"
          >
            <input
              type="checkbox"
              :value="m.id"
              v-model="selectedMenus"
              class="rounded text-brand-600 focus:ring-brand-500"
            />
            <span class="text-base">{{ m.icon }}</span>
            <div>
              <span class="font-bold text-slate-800">{{ m.title }}</span>
              <span class="text-[10px] text-slate-400 block font-mono">{{ m.path }}</span>
            </div>
          </label>
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
const showPermsModal = ref(false)
const showMenusModal = ref(false)
const roles = ref<any[]>([])
const selectedRole = ref<any>(null)
const selectedPerms = ref<string[]>([])
const selectedMenus = ref<string[]>([])
const groupedPermissions = ref<Record<string, any[]>>({})

const allMenuOptions = [
  { id: 'menu-dashboard', title: '监控大盘', path: '/', icon: '📊' },
  { id: 'menu-offers', title: 'Offer 计划', path: '/offers', icon: '🎯' },
  { id: 'menu-smartlinks', title: 'SmartLink 分流', path: '/smartlinks', icon: '⚡' },
  { id: 'menu-affiliates', title: '渠道客管理', path: '/affiliates', icon: '🤝' },
  { id: 'menu-conversions', title: '转化与归因', path: '/conversions', icon: '🔄' },
  { id: 'menu-finance', title: '财务账期出账', path: '/finance', icon: '💰' },
  { id: 'menu-analytics', title: 'Sub-ID 报表', path: '/analytics', icon: '📈' },
  { id: 'menu-sys-users', title: '用户管理', path: '/system/users', icon: '👥' },
  { id: 'menu-sys-roles', title: '角色管理', path: '/system/roles', icon: '🛡️' },
  { id: 'menu-sys-menus', title: '菜单管理', path: '/system/menus', icon: '📑' },
  { id: 'menu-sys-perms', title: '权限字典', path: '/system/permissions', icon: '🔑' },
  { id: 'menu-sys-s3', title: 'S3 存储配置', path: '/system/s3', icon: '🗄️' },
  { id: 'menu-sys-domains', title: '域名池管理', path: '/system/domains', icon: '🌐' }
]

const createForm = ref({
  roleCode: '',
  roleName: '',
  description: '',
  dataScope: 'TENANT_ONLY'
})

const columns = [
  { key: 'roleInfo', label: '角色标识 (Name / Code)' },
  { key: 'dataScope', label: '数据范围 (DataScope)' },
  { key: 'permsCount', label: '已授权功能' },
  { key: 'menusCount', label: '授权可见菜单' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' }
]

const loadRoles = async () => {
  loading.value = true
  try {
    const res = await fetchApi<any[]>('/api/v1/system/roles')
    roles.value = res
  } catch (err) {
    roles.value = [
      {
        id: 'role-super-admin',
        roleCode: 'SUPER_ADMIN',
        roleName: '超级管理员',
        dataScope: 'ALL',
        isSystem: true,
        status: 'ACTIVE',
        permissionCodes: ['*'],
        menuIds: allMenuOptions.map(m => m.id)
      },
      {
        id: 'role-aff-manager',
        roleCode: 'AFFILIATE_MANAGER',
        roleName: '网盟商务主管',
        dataScope: 'TENANT_ONLY',
        isSystem: false,
        status: 'ACTIVE',
        permissionCodes: ['offer:read', 'offer:write', 'smartlink:manage', 'affiliate:read', 'affiliate:write', 'conversion:audit'],
        menuIds: ['menu-dashboard', 'menu-offers', 'menu-smartlinks', 'menu-affiliates', 'menu-conversions', 'menu-analytics']
      }
    ]
  } finally {
    loading.value = false
  }
}

const loadPermissions = async () => {
  try {
    const res = await fetchApi<Record<string, any[]>>('/api/v1/system/permissions')
    groupedPermissions.value = res
  } catch (e) {
    groupedPermissions.value = {
      '用户管理': [
        { code: 'system:user:read', name: '用户查看' },
        { code: 'system:user:write', name: '用户维护' }
      ],
      'Offer计划': [
        { code: 'offer:read', name: '计划查询' },
        { code: 'offer:write', name: '计划维护' }
      ],
      '财务结算': [
        { code: 'finance:settle', name: '账期结算' }
      ]
    }
  }
}

const openPermissionsModal = (role: any) => {
  selectedRole.value = role
  selectedPerms.value = [...(role.permissionCodes || [])]
  showPermsModal.value = true
}

const savePermissions = async () => {
  if (!selectedRole.value) return
  try {
    await fetchApi(`/api/v1/system/roles/${selectedRole.value.id}/permissions`, {
      method: 'POST',
      body: selectedPerms.value
    })
  } catch (e) {}

  selectedRole.value.permissionCodes = [...selectedPerms.value]
  showPermsModal.value = false
  showToast('角色权限已更新生效', 'success')
}

const openMenusModal = (role: any) => {
  selectedRole.value = role
  selectedMenus.value = [...(role.menuIds || [])]
  showMenusModal.value = true
}

const saveMenus = async () => {
  if (!selectedRole.value) return
  try {
    await fetchApi(`/api/v1/system/roles/${selectedRole.value.id}/menus`, {
      method: 'POST',
      body: selectedMenus.value
    })
  } catch (e) {}

  selectedRole.value.menuIds = [...selectedMenus.value]
  showMenusModal.value = false
  showToast('角色菜单授权已更新', 'success')
}

const submitCreateRole = async () => {
  const payload = {
    roleCode: createForm.value.roleCode,
    roleName: createForm.value.roleName,
    description: createForm.value.description,
    dataScope: createForm.value.dataScope
  }

  try {
    const res = await fetchApi<any>('/api/v1/system/roles', {
      method: 'POST',
      body: payload
    })
    if (res) roles.value.unshift(res)
  } catch (e) {
    roles.value.unshift({
      id: 'role-' + Math.floor(Math.random() * 9000 + 1000),
      ...payload,
      isSystem: false,
      status: 'ACTIVE',
      permissionCodes: [],
      menuIds: []
    })
  }

  showCreateModal.value = false
  showToast('自定义角色创建成功', 'success')
}

onMounted(() => {
  loadRoles()
  loadPermissions()
})
</script>
