<script setup lang="ts">
import type { Component } from 'vue'

export interface UiEmptyStateProps {
  title: string
  body?: string
  icon?: Component
  tone?: 'neutral' | 'danger'
}

withDefaults(defineProps<UiEmptyStateProps>(), {
  tone: 'neutral',
})
</script>

<template>
  <div class="ui-empty-state" :class="`tone-${tone}`">
    <component :is="icon" v-if="icon" :size="24" aria-hidden="true" class="empty-icon" />
    <h3 class="empty-title">{{ title }}</h3>
    <p v-if="body" class="empty-body">{{ body }}</p>
    <div v-if="$slots.default" class="empty-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.ui-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: var(--sp-2);
  padding: var(--sp-6) var(--sp-4);
  box-sizing: border-box;
}

.empty-icon {
  color: var(--text-3);
  margin-bottom: var(--sp-1);
}

.tone-danger .empty-icon {
  color: var(--danger);
}

.empty-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.empty-body {
  font-size: var(--fs-sm);
  color: var(--text-2);
  max-width: 48ch;
  line-height: var(--lh-normal);
}

.empty-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  flex-wrap: wrap;
}
</style>
