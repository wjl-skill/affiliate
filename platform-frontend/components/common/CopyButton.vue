<template>
  <button
    type="button"
    class="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium rounded-md border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 transition-colors focus:outline-none focus:ring-2 focus:ring-brand-500"
    @click="copyText"
  >
    <span v-if="copied" class="text-emerald-600 font-semibold flex items-center gap-1">
      ✓ 已复制
    </span>
    <span v-else class="flex items-center gap-1">
      <slot>复制</slot>
    </span>
  </button>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  text: string
}>()

const copied = ref(false)

const copyText = async () => {
  if (!props.text) return
  try {
    await navigator.clipboard.writeText(props.text)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (err) {
    console.error('Copy failed:', err)
  }
}
</script>
