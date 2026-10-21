<script setup lang="ts">
export interface SegmentedOption {
  value: string | number | boolean
  label: string
  count?: number
}

defineProps<{
  modelValue: string | number | boolean
  options: SegmentedOption[]
  label: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number | boolean): void
}>()
</script>

<template>
  <div class="ui-segmented" role="group" :aria-label="label">
    <button
      v-for="opt in options"
      :key="String(opt.value)"
      type="button"
      class="segment-btn"
      :class="{ 'is-selected': modelValue === opt.value }"
      :aria-pressed="modelValue === opt.value ? 'true' : 'false'"
      @click="emit('update:modelValue', opt.value)"
    >
      <span class="segment-label">{{ opt.label }}</span>
      <span v-if="opt.count !== undefined" class="segment-count tabular"> · {{ opt.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.ui-segmented {
  display: inline-flex;
  align-items: center;
  padding: 2px;
  gap: 2px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  box-sizing: border-box;
}

.segment-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 1.75rem;
  padding: 0 var(--sp-3);
  border-radius: 4px;
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-2);
  background: transparent;
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 120ms, color 120ms;
}

.segment-btn:hover:not(.is-selected) {
  color: var(--text);
  background: var(--surface-3);
}

.segment-btn.is-selected {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.segment-count {
  margin-left: 2px;
}
</style>
