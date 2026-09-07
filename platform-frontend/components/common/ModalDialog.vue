<template>
  <Teleport to="body">
    <transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="show" class="fixed inset-0 z-50 overflow-y-auto bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
        <div class="relative bg-white rounded-xl shadow-xl border border-slate-200 w-full max-w-lg overflow-hidden transform transition-all">
          <!-- 弹窗标题 -->
          <div class="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
            <h3 class="text-base font-semibold text-slate-900">{{ title }}</h3>
            <button type="button" class="text-slate-400 hover:text-slate-500 text-lg" @click="$emit('close')">
              ✕
            </button>
          </div>

          <!-- 弹窗内容区 -->
          <div class="px-6 py-4 max-h-[75vh] overflow-y-auto">
            <slot></slot>
          </div>

          <!-- 底部操作按钮 -->
          <div class="px-6 py-3 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-3">
            <button
              type="button"
              class="px-4 py-2 text-sm font-medium rounded-lg text-slate-700 bg-white border border-slate-300 hover:bg-slate-50 transition-colors"
              @click="$emit('close')"
            >
              {{ cancelText || '取消' }}
            </button>
            <button
              v-if="!hideConfirm"
              type="button"
              :disabled="loading"
              class="px-4 py-2 text-sm font-medium rounded-lg text-white bg-brand-600 hover:bg-brand-700 transition-colors disabled:opacity-50 flex items-center gap-2"
              @click="$emit('confirm')"
            >
              <span v-if="loading">处理中...</span>
              <span v-else>{{ confirmText || '确认' }}</span>
            </button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{
  show: boolean
  title: string
  confirmText?: string
  cancelText?: string
  loading?: boolean
  hideConfirm?: boolean
}>()

defineEmits<{
  (e: 'close'): void
  (e: 'confirm'): void
}>()
</script>
