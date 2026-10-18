<template>
  <div class="ack-container">
    <div class="ack-card card">
      <h1>Action Acknowledgement</h1>

      <div v-if="loading" class="status-msg text-mono">Loading…</div>
      <div v-else-if="error" class="status-msg error">{{ error }}</div>
      <div v-else-if="done" class="status-msg success">
        ✓ Acknowledged. Thank you.
      </div>
      <div v-else class="action-detail">
        <p class="site-name">{{ action?.siteName }}</p>
        <p class="action-text text-devanagari">{{ action?.text }}</p>
        <p class="deadline text-mono">Deadline: {{ action?.deadline }}</p>
        <button class="ack-btn" @click="submitAck">{{ action?.ackMeans ?? 'Confirm' }}</button>
      </div>
    </div>

    <p class="expiry-note text-mono">Link expires 24 hours after issue.</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'

interface ActionDetail {
  siteName: string
  text: string
  deadline: string
  ackMeans: string
}

const props = defineProps<{ dispatchId: string }>()
const route = useRoute()

const loading = ref(true)
const error = ref<string | null>(null)
const done = ref(false)
const action = ref<ActionDetail | null>(null)

onMounted(async () => {
  try {
    // GET /api/v1/acks/:dispatchId — Sprint 2 wires real endpoint
    // Signed link validated by the server (expires 24h per spec Section 9)
    const sig = route.query.sig as string
    const resp = await fetch(`/api/v1/acks/${props.dispatchId}?sig=${sig}`)
    if (!resp.ok) throw new Error(resp.status === 410 ? 'This link has expired.' : 'Could not load action.')
    action.value = await resp.json()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Unknown error'
  } finally {
    loading.value = false
  }
})

async function submitAck() {
  try {
    const sig = route.query.sig as string
    await fetch(`/api/v1/acks/${props.dispatchId}?sig=${sig}`, { method: 'POST' })
    done.value = true
  } catch {
    error.value = 'Failed to submit acknowledgement. Please try again.'
  }
}
</script>

<style scoped>
.ack-container {
  max-width: 390px;
  margin: var(--space-8) auto;
  padding: var(--space-4);
}
.ack-card { text-align: center; }
h1 { font-size: 18px; margin-bottom: var(--space-6); }
.status-msg { padding: var(--space-4); }
.status-msg.error   { color: var(--color-warning); }
.status-msg.success { color: var(--color-ok); font-size: 18px; }
.site-name  { font-weight: 600; font-size: 16px; margin-bottom: var(--space-3); }
.action-text { margin-bottom: var(--space-3); line-height: 1.5; }
.deadline   { color: var(--color-text-muted); font-size: 12px; margin-bottom: var(--space-4); }
.ack-btn {
  width: 100%; padding: var(--space-3);
  background: var(--color-ok); color: #000;
  border: none; border-radius: var(--radius-md);
  font-weight: 600; cursor: pointer; font-family: var(--font-ui);
  font-size: 15px;
}
.expiry-note { text-align: center; color: var(--color-text-muted); font-size: 11px; margin-top: var(--space-3); }
</style>
