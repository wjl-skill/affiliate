<template>
  <span
    class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-wide border uppercase"
    :class="tagClass"
  >
    <span class="w-1.5 h-1.5 rounded-full" :class="dotClass"></span>
    <slot>{{ status }}</slot>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status?: string | null
}>()

const tagClass = computed(() => {
  const s = (props.status || '').toUpperCase()
  switch (s) {
    case 'ACTIVE':
    case 'APPROVED':
    case 'PAID':
    case 'VIP':
      return 'bg-emerald-50 text-emerald-700 border-emerald-200'
    case 'PENDING':
    case 'PENDING_REVIEW':
    case 'GENERATED':
    case 'GOLD':
      return 'bg-amber-50 text-amber-700 border-amber-200'
    case 'REJECTED':
    case 'SUSPENDED':
    case 'EXPIRED':
      return 'bg-rose-50 text-rose-700 border-rose-200'
    case 'FRAUD_SUSPECTED':
      return 'bg-purple-50 text-purple-700 border-purple-200 animate-pulse'
    case 'PAUSED':
    case 'SILVER':
    case 'STANDARD':
    default:
      return 'bg-slate-100 text-slate-700 border-slate-200'
  }
})

const dotClass = computed(() => {
  const s = (props.status || '').toUpperCase()
  switch (s) {
    case 'ACTIVE':
    case 'APPROVED':
    case 'PAID':
    case 'VIP':
      return 'bg-emerald-500'
    case 'PENDING':
    case 'PENDING_REVIEW':
    case 'GENERATED':
    case 'GOLD':
      return 'bg-amber-500'
    case 'REJECTED':
    case 'SUSPENDED':
    case 'EXPIRED':
      return 'bg-rose-500'
    case 'FRAUD_SUSPECTED':
      return 'bg-purple-500'
    default:
      return 'bg-slate-400'
  }
})
</script>
