<script setup lang="ts">
import type { Tone } from '@/types/api'

defineProps<{
  label: string
  value: string
  hint?: string
  tone?: Tone
}>()
</script>

<template>
  <div class="ui-stat">
    <span class="stat-label">{{ label }}</span>
    <span
      class="stat-value tabular"
      :class="[tone && tone !== 'neutral' ? `tone-${tone}` : '']"
    >
      {{ value }}
    </span>
    <span v-if="hint" class="stat-hint">{{ hint }}</span>
  </div>
</template>

<style scoped>
.ui-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat-label {
  font-size: var(--fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-3);
  line-height: var(--lh-tight);
}

.stat-value {
  font-size: var(--fs-xl);
  font-weight: 600;
  line-height: var(--lh-tight);
  color: var(--text);
  white-space: nowrap;
}

.stat-hint {
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: var(--lh-normal);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tone-ok {
  color: var(--ok);
}

.tone-watch {
  color: var(--watch);
}

.tone-danger {
  color: var(--danger);
}

.tone-info {
  color: var(--info);
}

.tone-ai {
  color: var(--ai);
}

.tone-outage {
  color: var(--outage);
}
</style>
