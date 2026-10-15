import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchPublicRule } from '@/api/notices'
import type { PublicRule } from '@/types'

/* Cached display rules; invalidated after a rule publish. */
export const useRuleStore = defineStore('rule', () => {
  const rule = ref<PublicRule | null>(null)
  const loadedAt = ref(0)

  async function load(force = false): Promise<PublicRule | null> {
    if (!force && rule.value) {
      return rule.value
    }
    rule.value = await fetchPublicRule()
    loadedAt.value = Date.now()
    return rule.value
  }

  function invalidate(): void {
    rule.value = null
    loadedAt.value = 0
  }

  return { rule, loadedAt, load, invalidate }
})
