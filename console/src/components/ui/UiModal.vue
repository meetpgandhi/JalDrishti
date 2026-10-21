<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { X } from '@lucide/vue'
import UiButton from '@/components/ui/UiButton.vue'

export interface UiModalProps {
  open: boolean
  title: string
  subtitle?: string
  maxWidth?: string
}

const props = withDefaults(defineProps<UiModalProps>(), {
  maxWidth: '880px',
})

const emit = defineEmits<{
  (e: 'close'): void
}>()

const closeBtnRef = ref<InstanceType<typeof UiButton> | null>(null)

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    emit('close')
  }
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      window.addEventListener('keydown', onKeyDown)
      void nextTick(() => {
        const el = (closeBtnRef.value as unknown as { $el?: HTMLElement })?.$el ?? (closeBtnRef.value as unknown as HTMLElement)
        el?.focus?.()
      })
    } else {
      window.removeEventListener('keydown', onKeyDown)
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="scrim">
      <div
        v-if="open"
        class="ui-modal-scrim"
        aria-hidden="true"
        @click="emit('close')"
      />
    </Transition>

    <Transition name="modal">
      <div
        v-if="open"
        class="ui-modal-wrapper"
        @click.self="emit('close')"
      >
        <div
          class="ui-modal-dialog"
          :style="{ maxWidth: `min(100%, ${maxWidth})` }"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
        >
          <header class="modal-header">
            <div class="header-titles">
              <h2 class="modal-title">{{ title }}</h2>
              <p v-if="subtitle" class="modal-subtitle">{{ subtitle }}</p>
            </div>
            <UiButton
              ref="closeBtnRef"
              variant="ghost"
              :icon="X"
              icon-only
              label="Close"
              @click="emit('close')"
            />
          </header>

          <div class="modal-body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="modal-footer">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ui-modal-scrim {
  position: fixed;
  inset: 0;
  background: var(--scrim);
  z-index: 40;
}

.ui-modal-wrapper {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: var(--sp-4);
  box-sizing: border-box;
}

.scrim-enter-active,
.scrim-leave-active {
  transition: opacity 160ms ease;
}

.scrim-enter-from,
.scrim-leave-to {
  opacity: 0;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.98);
}

.ui-modal-dialog {
  width: 100%;
  max-height: calc(100vh - 2 * var(--sp-8));
  border-radius: var(--r-md);
  background: var(--surface-1);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-overlay);
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border-bottom: 1px solid var(--border);
}

.header-titles {
  min-width: 0;
}

.modal-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.modal-subtitle {
  font-size: var(--fs-sm);
  color: var(--text-2);
  margin-top: 2px;
  line-height: var(--lh-normal);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-4);
  min-height: 0;
}

.modal-footer {
  border-top: 1px solid var(--border);
  padding: var(--sp-3) var(--sp-4);
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--sp-2);
}
</style>
