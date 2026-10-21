<script setup lang="ts">
import { computed } from 'vue'
import { PROVENANCE } from '@/lib/labels'
import type { Tag } from '@/types/api'

const props = defineProps<{
  tag: Tag
}>()

const info = computed(() => PROVENANCE[props.tag] ?? { label: props.tag, meaning: '' })
const titleText = computed(() => `${info.value.label}: ${info.value.meaning}`)
</script>

<template>
  <abbr
    class="ui-provenance"
    :class="`tag-${tag}`"
    :title="titleText"
  >{{ tag }}</abbr>
</template>

<style scoped>
.ui-provenance {
  display: inline-grid;
  place-items: center;
  width: 1.25rem;
  height: 1.25rem;
  border: 1px solid currentColor;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  font-weight: 600;
  text-decoration: none;
  cursor: help;
  box-sizing: border-box;
}

.tag-P {
  color: var(--ok);
}

.tag-D {
  color: var(--info);
}

.tag-A {
  color: var(--watch);
}
</style>
