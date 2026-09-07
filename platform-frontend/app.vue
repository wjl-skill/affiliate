<template>
  <div class="min-h-screen bg-slate-50">
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>

    <!-- 全局轻量 Toast 提示挂载层 -->
    <div class="fixed bottom-5 right-5 z-50 flex flex-col gap-2 pointer-events-none">
      <transition-group
        enter-active-class="transition duration-300 ease-out transform"
        enter-from-class="translate-y-2 opacity-0"
        enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition duration-200 ease-in transform"
        leave-from-class="translate-y-0 opacity-100"
        leave-to-class="translate-y-2 opacity-0"
      >
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg border text-sm max-w-sm"
          :class="{
            'bg-white text-slate-800 border-slate-200': toast.type === 'info',
            'bg-emerald-50 text-emerald-800 border-emerald-200': toast.type === 'success',
            'bg-rose-50 text-rose-800 border-rose-200': toast.type === 'error',
            'bg-amber-50 text-amber-800 border-amber-200': toast.type === 'warning'
          }"
        >
          <span class="font-medium">{{ toast.message }}</span>
        </div>
      </transition-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToasts } from '~/composables/useNotification'

const { toasts } = useToasts()
</script>
