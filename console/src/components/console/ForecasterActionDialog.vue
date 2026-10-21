<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useConsoleStore, type ForecasterAction } from '@/stores/console'
import UiModal from '@/components/ui/UiModal.vue'
import UiButton from '@/components/ui/UiButton.vue'

const props = defineProps<{
  kind: ForecasterAction | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const store = useConsoleStore()

const reason = ref('')
const isBusy = ref(false)
const errorMsg = ref<string | null>(null)

watch(
  () => props.kind,
  () => {
    reason.value = ''
    isBusy.value = false
    errorMsg.value = null
  },
)

const dialogConfig = computed(() => {
  switch (props.kind) {
    case 'escalate':
      return {
        title: 'Escalate to warning',
        body: 'This raises a public WARNING and sends actions to every duty-holder downstream.',
        btnText: 'Escalate',
        btnVariant: 'primary' as const,
      }
    case 'cancel-watch':
      return {
        title: 'Cancel the watch',
        body: 'This closes the internal watch. Nobody outside DHM has been alerted at this level.',
        btnText: 'Cancel watch',
        btnVariant: 'primary' as const,
      }
    case 'stand-down':
      return {
        title: 'Stand down the incident',
        body: 'Everyone who received the warning is told to stand down. Do this only when the danger has passed.',
        btnText: 'Stand down',
        btnVariant: 'danger' as const,
      }
    default:
      return {
        title: '',
        body: '',
        btnText: '',
        btnVariant: 'primary' as const,
      }
  }
})

const isValid = computed(() => reason.value.trim().length >= 10)

async function handleConfirm() {
  if (!props.kind || !isValid.value) return
  isBusy.value = true
  errorMsg.value = null
  try {
    const sanitizedReason = reason.value.trim().replace(/["\\]/g, "'")
    await store.forecasterAction(props.kind, sanitizedReason)
    reason.value = ''
    emit('close')
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : String(e)
  } finally {
    isBusy.value = false
  }
}
</script>

<template>
  <UiModal
    :open="kind !== null"
    :title="dialogConfig.title"
    max-width="520px"
    @close="emit('close')"
  >
    <div class="dialog-content">
      <p class="dialog-body">{{ dialogConfig.body }}</p>

      <div class="field-group">
        <label for="forecaster-reason">Reason (required, recorded in the audit log)</label>
        <textarea
          id="forecaster-reason"
          v-model="reason"
          rows="3"
          placeholder="Enter reason for this decision (min 10 characters)"
        />
        <div class="char-count">
          <span>{{ reason.trim().length }} / 10 characters minimum</span>
        </div>
      </div>

      <div v-if="errorMsg" class="dialog-error">
        {{ errorMsg }}
      </div>
    </div>

    <template #footer>
      <UiButton
        variant="secondary"
        @click="emit('close')"
      >
        Back
      </UiButton>
      <UiButton
        :variant="dialogConfig.btnVariant"
        :disabled="!isValid"
        :busy="isBusy"
        @click="handleConfirm"
      >
        {{ dialogConfig.btnText }}
      </UiButton>
    </template>
  </UiModal>
</template>

<style scoped>
.dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.dialog-body {
  font-size: var(--fs-md);
  color: var(--text-2);
  line-height: var(--lh-normal);
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.char-count {
  font-size: var(--fs-xs);
  color: var(--text-3);
  text-align: right;
}

.dialog-error {
  color: var(--danger);
  font-size: var(--fs-sm);
  padding: var(--sp-2);
  background: var(--danger-soft);
  border: 1px solid var(--danger-line);
  border-radius: var(--r-sm);
}
</style>
