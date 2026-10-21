<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { X } from '@lucide/vue'
import UiButton from '@/components/ui/UiButton.vue'

export interface UiSheetProps {
  open: boolean
  title: string
  subtitle?: string
  modal?: boolean
  width?: string
}

const props = withDefaults(defineProps<UiSheetProps>(), {
  modal: true,
  width: 'var(--sheet-w)',
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
        v-if="open && modal"
        class="ui-sheet-scrim"
        aria-hidden="true"
        @click="emit('close')"
      />
    </Transition>

    <Transition name="sheet">
      <aside
        v-if="open"
        class="ui-sheet-panel"
        :style="{ width: `min(100vw, ${width})` }"
        role="dialog"
        :aria-modal="modal ? 'true' : 'false'"
        :aria-label="title"
      >
        <header class="sheet-header">
          <div class="header-titles">
            <h2 class="sheet-title">{{ title }}</h2>
            <p v-if="subtitle" class="sheet-subtitle">{{ subtitle }}</p>
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

        <div class="sheet-body">
          <slot />
        </div>

        <footer v-if="$slots.footer" class="sheet-footer">
          <slot name="footer" />
        </footer>
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ui-sheet-scrim {
  position: fixed;
  inset: 0;
  background: var(--scrim);
  z-index: 40;
}

.scrim-enter-active,
.scrim-leave-active {
  transition: opacity 160ms ease;
}

.scrim-enter-from,
.scrim-leave-to {
  opacity: 0;
}

.ui-sheet-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  background: var(--surface-1);
  border-left: 1px solid var(--border);
  box-shadow: var(--shadow-overlay);
  z-index: 50;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 160ms ease, transform 160ms ease;
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
  transform: translateX(16px);
}

.sheet-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border-bottom: 1px solid var(--border);
}

.header-titles {
  min-width: 0;
}

.sheet-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.sheet-subtitle {
  font-size: var(--fs-sm);
  color: var(--text-2);
  margin-top: 2px;
  line-height: var(--lh-normal);
}

.sheet-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-4);
  min-height: 0;
}

.sheet-footer {
  border-top: 1px solid var(--border);
  padding: var(--sp-3) var(--sp-4);
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--sp-2);
}
</style>
