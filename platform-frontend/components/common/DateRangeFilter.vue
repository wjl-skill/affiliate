<template>
  <div class="inline-flex rounded-lg bg-slate-100 p-0.5 border border-slate-200 text-xs font-medium text-slate-600">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="px-3 py-1.5 rounded-md transition-colors"
      :class="modelValue === opt.value ? 'bg-white text-slate-900 shadow-sm font-semibold' : 'hover:text-slate-900'"
      @click="$emit('update:modelValue', opt.value)"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: string
}>()

defineEmits<{
  (e: 'update:modelValue', val: string): void
}>()

const options = [
  { label: '今天', value: 'today' },
  { label: '昨天', value: 'yesterday' },
  { label: '近 7 天', value: '7d' },
  { label: '近 30 天', value: '30d' }
]
</script>
