<template>
  <div class="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
    <!-- 表格顶部工具栏 -->
    <div class="p-4 border-b border-slate-100 flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-3 flex-1 max-w-sm">
        <div class="relative w-full">
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="searchPlaceholder || '搜索关键词...'"
            class="w-full rounded-lg border border-slate-200 pl-9 pr-4 py-1.5 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500"
          />
          <span class="absolute left-3 top-2 text-slate-400 text-xs">🔍</span>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <slot name="actions"></slot>
      </div>
    </div>

    <!-- 表格主体 -->
    <div class="overflow-x-auto min-h-[220px]">
      <table class="min-w-full divide-y divide-slate-200 text-left text-xs">
        <thead class="bg-slate-50 text-slate-500 uppercase tracking-wider font-semibold">
          <tr>
            <th
              v-for="col in columns"
              :key="col.key"
              scope="col"
              class="px-4 py-3"
              :style="col.width ? { width: col.width } : {}"
            >
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100 bg-white text-slate-700">
          <tr v-if="loading">
            <td :colspan="columns.length" class="text-center py-12 text-slate-400">
              <div class="inline-flex items-center gap-2">
                <span class="animate-spin text-brand-600 text-lg">⏳</span>
                <span>正在加载数据...</span>
              </div>
            </td>
          </tr>
          <tr v-else-if="filteredData.length === 0">
            <td :colspan="columns.length" class="text-center py-12 text-slate-400">
              <div class="flex flex-col items-center justify-center gap-1">
                <span class="text-2xl">📦</span>
                <span class="text-xs text-slate-400">暂无匹配的数据记录</span>
              </div>
            </td>
          </tr>
          <tr
            v-for="(row, idx) in paginatedData"
            :key="idx"
            class="hover:bg-slate-50/80 transition-colors"
          >
            <td
              v-for="col in columns"
              :key="col.key"
              class="px-4 py-3 whitespace-nowrap"
            >
              <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
                {{ row[col.key] !== undefined && row[col.key] !== null ? row[col.key] : '-' }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 表格底部简易分页器 -->
    <div class="px-4 py-3 bg-slate-50 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
      <div>
        共 <span class="font-semibold text-slate-800">{{ filteredData.length }}</span> 条数据
      </div>
      <div v-if="totalPages > 1" class="flex items-center gap-1">
        <button
          type="button"
          :disabled="currentPage === 1"
          class="px-2.5 py-1 rounded border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-40 transition-colors"
          @click="currentPage--"
        >
          上一页
        </button>
        <span class="px-2">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button
          type="button"
          :disabled="currentPage >= totalPages"
          class="px-2.5 py-1 rounded border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-40 transition-colors"
          @click="currentPage++"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

export interface TableColumn {
  key: string
  label: string
  width?: string
}

const props = withDefaults(defineProps<{
  columns: TableColumn[]
  data: any[]
  loading?: boolean
  searchPlaceholder?: string
  pageSize?: number
}>(), {
  pageSize: 10
})

const searchQuery = ref('')
const currentPage = ref(1)

const filteredData = computed(() => {
  if (!searchQuery.value.trim()) {
    return props.data
  }
  const q = searchQuery.value.toLowerCase().trim()
  return props.data.filter(item => {
    return Object.values(item).some(val => {
      if (val === null || val === undefined) return false
      return String(val).toLowerCase().includes(q)
    })
  })
})

const totalPages = computed(() => {
  return Math.ceil(filteredData.value.length / props.pageSize) || 1
})

const paginatedData = computed(() => {
  const start = (currentPage.value - 1) * props.pageSize
  return filteredData.value.slice(start, start + props.pageSize)
})
</script>
