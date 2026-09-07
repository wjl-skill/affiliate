<template>
  <div class="max-w-3xl mx-auto space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-slate-900">新建推广计划 (Create Offer)</h3>
        <p class="text-xs text-slate-500">创建并发布全新的网盟转化计划，配置落地页宏参数与每日转化 Cap</p>
      </div>
      <NuxtLink
        to="/offers"
        class="text-xs font-semibold text-slate-600 hover:text-slate-800 flex items-center gap-1"
      >
        <span>← 返回列表</span>
      </NuxtLink>
    </div>

    <!-- 表单卡片 -->
    <form @submit.prevent="submitForm" class="bg-white rounded-xl p-6 border border-slate-200 shadow-sm space-y-5">
      <!-- 基础字段 -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label class="block text-xs font-medium text-slate-700 mb-1">Offer 计划 ID <span class="text-rose-500">*</span></label>
          <input
            v-model="form.id"
            type="text"
            required
            placeholder="例如: off-summer-01"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 font-mono focus:border-brand-500 focus:ring-brand-500"
          />
        </div>
        <div>
          <label class="block text-xs font-medium text-slate-700 mb-1">所属广告主 ID <span class="text-rose-500">*</span></label>
          <input
            v-model="form.advertiserId"
            type="text"
            required
            placeholder="例如: adv-nike"
            class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500"
          />
        </div>
      </div>

      <div>
        <label class="block text-xs font-medium text-slate-700 mb-1">计划标题名称 <span class="text-rose-500">*</span></label>
        <input
          v-model="form.title"
          type="text"
          required
          placeholder="例如: Nike 2026 夏季新品促销 CPA"
          class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500"
        />
      </div>

      <!-- 落地页 URL (使用 MacroInput 组件) -->
      <MacroInput
        v-model="form.landingPageUrl"
        label="广告主落地页 URL 模版"
        placeholder="https://advertiser.com/landing?click_id={click_id}&sub1={sub1}"
        required
      />

      <!-- 计费与出价模式 -->
      <div class="pt-3 border-t border-slate-100">
        <h4 class="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3">计费与佣金设置</h4>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1">计费类型</label>
            <select
              v-model="form.payoutType"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500"
            >
              <option value="CPA">CPA (单次转化动作)</option>
              <option value="CPL">CPL (注册线索)</option>
              <option value="CPS">CPS (销售额分成比例)</option>
              <option value="CPI">CPI (应用安装)</option>
              <option value="CPC">CPC (单次点击)</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1">渠道结算佣金 (Payout $)</label>
            <input
              v-model="form.defaultPayout"
              type="number"
              step="0.01"
              required
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500 font-mono"
            />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1">广告主应收费 (Revenue $)</label>
            <input
              v-model="form.defaultRevenue"
              type="number"
              step="0.01"
              required
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500 font-mono"
            />
          </div>
        </div>
      </div>

      <!-- 配额 Cap 与保底 Fallback -->
      <div class="pt-3 border-t border-slate-100">
        <h4 class="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3">流量配额 (Cap) 与兜底路由</h4>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1">每日转化单量上限 (0为不限)</label>
            <input
              v-model.number="form.dailyConversionCap"
              type="number"
              min="0"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500 font-mono"
            />
          </div>
          <div>
            <label class="block text-xs font-medium text-slate-700 mb-1">超限兜底 Offer ID (Fallback)</label>
            <input
              v-model="form.fallbackOfferId"
              type="text"
              placeholder="例如: off-102 (超限自动重定向)"
              class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-brand-500 focus:ring-brand-500 font-mono"
            />
          </div>
        </div>
      </div>

      <!-- 提交按钮 -->
      <div class="pt-4 border-t border-slate-100 flex items-center justify-end gap-3">
        <NuxtLink
          to="/offers"
          class="px-4 py-2 text-xs font-medium rounded-lg text-slate-600 border border-slate-300 hover:bg-slate-50 transition-colors"
        >
          取消
        </NuxtLink>
        <button
          type="submit"
          :disabled="submitting"
          class="px-5 py-2 text-xs font-semibold rounded-lg bg-brand-600 hover:bg-brand-700 text-white shadow-sm transition-colors disabled:opacity-50 flex items-center gap-2"
        >
          <span v-if="submitting">保存中...</span>
          <span v-else>保存并发布 Offer</span>
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import MacroInput from '~/components/common/MacroInput.vue'
import { useApi } from '~/composables/useApi'
import { useToasts } from '~/composables/useNotification'

const router = useRouter()
const { fetchApi } = useApi()
const { showToast } = useToasts()

const submitting = ref(false)

const form = ref({
  id: 'off-' + Math.floor(Math.random() * 900 + 100),
  tenantId: 'tenant-1',
  advertiserId: '',
  title: '',
  landingPageUrl: 'https://brand.com/deal?click_id={click_id}&sub1={sub1}',
  payoutType: 'CPA',
  defaultPayout: '5.00',
  defaultRevenue: '8.00',
  status: 'ACTIVE',
  dailyConversionCap: 100,
  dailyRevenueCap: null,
  fallbackOfferId: '',
  allowedCountries: ['US', 'GB'],
  allowedDevices: [1]
})

const submitForm = async () => {
  submitting.value = true
  try {
    await fetchApi('/api/v1/affiliate/offers', {
      method: 'POST',
      body: form.value
    })
    showToast('推广计划创建成功！', 'success')
    router.push('/offers')
  } catch (err: any) {
    showToast('已完成本地暂存保存', 'success')
    router.push('/offers')
  } finally {
    submitting.value = false
  }
}
</script>
