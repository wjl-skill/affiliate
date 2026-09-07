import { ref } from 'vue'

export interface ToastItem {
  id: number
  message: string
  type: 'info' | 'success' | 'error' | 'warning'
}

const toasts = ref<ToastItem[]>([])
let counter = 0

export function useToasts() {
  const showToast = (message: string, type: 'info' | 'success' | 'error' | 'warning' = 'info', duration = 3000) => {
    const id = ++counter
    toasts.value.push({ id, message, type })
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== id)
    }, duration)
  }

  return {
    toasts,
    showToast
  }
}
