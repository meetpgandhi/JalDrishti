<script setup lang="ts">
import type { Component } from 'vue'
import type { Tone } from '@/types/api'

export interface UiBadgeProps {
  tone?: Tone
  solid?: boolean
  size?: 'sm' | 'md'
  icon?: Component
}

withDefaults(defineProps<UiBadgeProps>(), {
  tone: 'neutral',
  solid: false,
  size: 'sm',
})
</script>

<template>
  <span
    class="ui-badge"
    :class="[
      `tone-${tone}`,
      `size-${size}`,
      { 'is-solid': solid },
    ]"
  >
    <component :is="icon" v-if="icon" :size="14" aria-hidden="true" class="badge-icon" />
    <span class="badge-text"><slot /></span>
  </span>
</template>

<style scoped>
.ui-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 1.375rem;
  padding: 0 var(--sp-2);
  border-radius: var(--r-pill);
  border: 1px solid var(--border);
  font-size: var(--fs-xs);
  font-weight: 600;
  white-space: nowrap;
  box-sizing: border-box;
}

.size-md {
  height: 1.75rem;
  padding: 0 var(--sp-3);
  font-size: var(--fs-sm);
}

.badge-icon {
  flex-shrink: 0;
}

.badge-text {
  display: inline-flex;
  align-items: center;
}

/* Soft styles (default) */
.tone-neutral {
  background: var(--surface-3);
  color: var(--text-2);
  border-color: var(--border);
}

.tone-ok {
  background: var(--ok-soft);
  color: var(--ok);
  border-color: var(--ok-line);
}

.tone-watch {
  background: var(--watch-soft);
  color: var(--watch);
  border-color: var(--watch-line);
}

.tone-danger {
  background: var(--danger-soft);
  color: var(--danger);
  border-color: var(--danger-line);
}

.tone-info {
  background: var(--info-soft);
  color: var(--info);
  border-color: var(--info-line);
}

.tone-ai {
  background: var(--ai-soft);
  color: var(--ai);
  border-color: var(--ai-line);
}

.tone-outage {
  background: var(--outage-soft);
  color: var(--outage);
  border-color: var(--outage-line);
}

/* Solid variants */
.is-solid.tone-danger {
  background: var(--danger-solid);
  color: var(--text-on-solid);
  border-color: transparent;
}

.is-solid.tone-watch {
  background: var(--watch-solid);
  color: var(--text-on-watch);
  border-color: transparent;
}

.is-solid.tone-info {
  background: var(--accent-solid);
  color: var(--text-on-solid);
  border-color: transparent;
}

.is-solid.tone-ok {
  background: var(--ok-soft);
  color: var(--ok);
  border-color: var(--ok-line);
}
</style>
