<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { CircleCheck } from '@lucide/vue'
import { api, endpoints, ApiError } from '@/lib/api'
import UiPanel from '@/components/ui/UiPanel.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'

const props = defineProps<{
  dispatchId: string
}>()

const route = useRoute()

const isSubmitting = ref(false)
const isSuccess = ref(false)
const errorMessage = ref<string | null>(null)

async function handleAck() {
  isSubmitting.value = true
  errorMessage.value = null
  try {
    const token = String(route.query.token ?? '')
    await api.post(endpoints.signedAck(props.dispatchId, token), {
      via: 'WEB_ACK_LINK',
    })
    isSuccess.value = true
  } catch (e: unknown) {
    if (e instanceof ApiError && e.status === 410) {
      errorMessage.value = 'This link has expired.'
    } else {
      const msg = e instanceof Error ? e.message : String(e)
      errorMessage.value = `Could not confirm: ${msg}`
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="ack-page">
    <UiPanel title="Confirm your action" class="ack-panel">
      <!-- Success state -->
      <UiEmptyState
        v-if="isSuccess"
        :icon="CircleCheck"
        title="Confirmed. Thank you."
        body="Your action confirmation has been recorded."
      />

      <!-- Action confirmation form -->
      <div v-else class="ack-content">
        <p class="ack-text">
          JalDrishti sent you an action. Tap below once you have done it.
        </p>

        <div v-if="errorMessage" class="ack-error">
          <p>{{ errorMessage }}</p>
        </div>

        <UiButton
          variant="primary"
          block
          :busy="isSubmitting"
          @click="handleAck"
        >
          {{ errorMessage ? 'Try again' : 'I have done it' }}
        </UiButton>
      </div>

      <template #footer>
        <div class="ack-footer">
          <RouterLink to="/phone" class="phone-link">
            Open your duty inbox
          </RouterLink>
        </div>
      </template>
    </UiPanel>
  </div>
</template>

<style scoped>
.ack-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  background: var(--bg);
  box-sizing: border-box;
}

.ack-panel {
  width: 100%;
  max-width: 26rem;
  margin: var(--sp-8) auto;
}

.ack-content {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  padding: var(--sp-2) 0;
}

.ack-text {
  font-size: var(--fs-md);
  color: var(--text-2);
  line-height: var(--lh-normal);
}

.ack-error {
  color: var(--danger);
  font-size: var(--fs-sm);
  background: var(--danger-soft);
  border: 1px solid var(--danger-line);
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-sm);
  line-height: var(--lh-normal);
}

.ack-footer {
  display: flex;
  justify-content: center;
  width: 100%;
}

.phone-link {
  font-size: var(--fs-sm);
  color: var(--accent);
  font-weight: 500;
}
</style>
