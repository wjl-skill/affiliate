<template>
  <div class="w-full">
    <label v-if="label" class="block text-sm font-medium text-slate-700 mb-1">
      {{ label }}
      <span v-if="required" class="text-rose-500">*</span>
    </label>
    <div class="relative rounded-md shadow-sm">
      <input
        ref="inputRef"
        type="text"
        :value="modelValue"
        :placeholder="placeholder || '请输入 URL 模版...'"
        class="block w-full rounded-lg border-slate-300 px-3.5 py-2 text-sm text-slate-900 border focus:border-brand-500 focus:ring-brand-500 font-mono"
        @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      />
    </div>
    <!-- 快捷插入宏标签 -->
    <div class="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
      <span class="text-slate-400">点击插入宏参数:</span>
      <button
        v-for="macro in availableMacros"
        :key="macro"
        type="button"
        class="px-2 py-0.5 rounded bg-slate-100 hover:bg-brand-50 text-slate-600 hover:text-brand-600 border border-slate-200 transition-colors font-mono"
        @click="insertMacro(macro)"
      >
        {{ macro }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: string
  label?: string
  placeholder?: string
  required?: boolean
  macros?: string[]
}>(), {
  macros: () => [
    '{click_id}',
    '{payout}',
    '{txid}',
    '{sub1}',
    '{sub2}',
    '{sub3}',
    '{currency}'
  ]
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const availableMacros = props.macros

const insertMacro = (macroText: string) => {
  const input = inputRef.value
  if (!input) {
    emit('update:modelValue', (props.modelValue || '') + macroText)
    return
  }

  const start = input.selectionStart || 0
  const end = input.selectionEnd || 0
  const current = props.modelValue || ''
  const updated = current.substring(0, start) + macroText + current.substring(end)

  emit('update:modelValue', updated)

  // 恢复焦点与光标位置
  setTimeout(() => {
    input.focus()
    const newPos = start + macroText.length
    input.setSelectionRange(newPos, newPos)
  }, 0)
}
</script>
