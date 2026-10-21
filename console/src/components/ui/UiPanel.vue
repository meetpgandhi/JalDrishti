<script setup lang="ts">
import { useId, type Component } from 'vue'

export interface UiPanelProps {
  title: string
  subtitle?: string
  icon?: Component
  flush?: boolean
}

withDefaults(defineProps<UiPanelProps>(), {
  flush: false,
})

const id = useId()
</script>

<template>
  <section class="ui-panel" :aria-labelledby="id">
    <header class="panel-header">
      <div class="header-left">
        <component :is="icon" v-if="icon" :size="18" aria-hidden="true" class="header-icon" />
        <div class="header-titles">
          <h2 :id="id" class="panel-title">{{ title }}</h2>
          <p v-if="subtitle" class="panel-subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="header-actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="panel-body" :class="{ 'is-flush': flush }">
      <slot />
    </div>

    <footer v-if="$slots.footer" class="panel-footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<style scoped>
.ui-panel {
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-card);
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-4) var(--sp-3);
  border-bottom: 1px solid var(--border);
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  min-width: 0;
}

.header-icon {
  color: var(--text-3);
  flex-shrink: 0;
  margin-top: 2px;
}

.header-titles {
  min-width: 0;
}

.panel-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.panel-subtitle {
  font-size: var(--fs-sm);
  color: var(--text-2);
  margin-top: 2px;
  line-height: var(--lh-normal);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-shrink: 0;
}

.panel-body {
  padding: var(--sp-4);
  flex: 1;
  min-width: 0;
}

.panel-body.is-flush {
  padding: 0;
}

.panel-footer {
  padding: var(--sp-3) var(--sp-4);
  border-top: 1px solid var(--border);
  font-size: var(--fs-sm);
  color: var(--text-3);
  line-height: var(--lh-normal);
}
</style>
