<template>
  <div class="bg-white rounded-xl p-5 border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium text-slate-500">{{ title }}</span>
      <div v-if="icon" class="w-9 h-9 rounded-lg flex items-center justify-center bg-brand-50 text-brand-600">
        <component :is="icon" class="w-5 h-5" />
      </div>
    </div>
    <div class="mt-3 flex items-baseline gap-2">
      <span v-if="prefix" class="text-lg font-semibold text-slate-500">{{ prefix }}</span>
      <span class="text-2xl font-bold tracking-tight text-slate-900">{{ formattedValue }}</span>
      <span v-if="suffix" class="text-sm font-medium text-slate-500">{{ suffix }}</span>
    </div>
    <div v-if="trend !== undefined" class="mt-3 flex items-center gap-1.5 text-xs">
      <span
        class="inline-flex items-center font-semibold px-1.5 py-0.5 rounded"
        :class="trend >= 0 ? 'text-emerald-700 bg-emerald-50' : 'text-rose-700 bg-rose-50'"
      >
        {{ trend >= 0 ? '↑ +' : '↓ ' }}{{ trend }}%
      </span>
      <span class="text-slate-400">较昨日同期</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  title: string
  value: number | string
  prefix?: string
  suffix?: string
  trend?: number
  icon?: any
}>()

const formattedValue = computed(() => {
  if (typeof props.value === 'number') {
    return props.value.toLocaleString()
  }
  return props.value
})
</script>
