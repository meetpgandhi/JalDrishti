<script setup lang="ts">
import type { Component } from 'vue'

export interface UiButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'onBrand'
  size?: 'sm' | 'md'
  icon?: Component
  iconOnly?: boolean
  label?: string
  disabled?: boolean
  busy?: boolean
  pressed?: boolean
  block?: boolean
}

withDefaults(defineProps<UiButtonProps>(), {
  variant: 'secondary',
  size: 'md',
  iconOnly: false,
  disabled: false,
  busy: false,
  block: false,
})
</script>

<template>
  <button
    type="button"
    class="ui-btn"
    :class="[
      `variant-${variant}`,
      `size-${size}`,
      { 'is-icon-only': iconOnly, 'is-block': block, 'is-pressed': pressed === true },
    ]"
    :disabled="disabled || busy"
    :aria-busy="busy ? 'true' : undefined"
    :aria-pressed="pressed !== undefined ? (pressed ? 'true' : 'false') : undefined"
    :aria-label="iconOnly ? label : undefined"
    :title="label"
  >
    <component :is="icon" v-if="icon" :size="16" aria-hidden="true" class="btn-icon" />
    <span v-if="busy" class="btn-label">Working…</span>
    <span v-else-if="!iconOnly" class="btn-label">
      <slot />
    </span>
  </button>
</template>

<style scoped>
.ui-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  height: 2.25rem;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-md);
  font-weight: 500;
  white-space: nowrap;
  transition: background-color 120ms;
  border: 1px solid transparent;
  box-sizing: border-box;
}

.size-sm {
  height: 1.875rem;
  padding: 0 var(--sp-2);
  font-size: var(--fs-sm);
}

.is-icon-only {
  width: 2.25rem;
  padding: 0;
}

.size-sm.is-icon-only {
  width: 1.875rem;
  padding: 0;
}

.is-block {
  width: 100%;
  height: 3rem;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.variant-primary {
  background: var(--accent-solid);
  color: var(--text-on-solid);
}
.variant-primary:hover:not(:disabled) {
  filter: brightness(1.1);
}

.variant-secondary {
  background: var(--surface-1);
  border-color: var(--border-strong);
  color: var(--text);
}
.variant-secondary:hover:not(:disabled) {
  background: var(--surface-3);
}

.variant-ghost {
  background: transparent;
  color: var(--text-2);
}
.variant-ghost:hover:not(:disabled) {
  background: var(--surface-3);
  color: var(--text);
}

.variant-danger {
  background: var(--surface-1);
  border-color: var(--danger-line);
  color: var(--danger);
}
.variant-danger:hover:not(:disabled) {
  background: var(--danger-soft);
}

.variant-onBrand {
  background: transparent;
  border-color: var(--brand-bar-line);
  color: var(--text-on-brand);
}
.variant-onBrand:hover:not(:disabled),
.variant-onBrand.is-pressed {
  background: var(--brand-bar-hover);
}

.is-pressed:not(.variant-onBrand) {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--info-line);
}

.ui-btn:disabled {
  opacity: 0.5;
}

.btn-icon {
  flex-shrink: 0;
}

.btn-label {
  display: inline-flex;
  align-items: center;
}
</style>
