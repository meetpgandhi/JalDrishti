<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Play } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { SCENARIOS, VARIANT_OPTIONS, type ScenarioInfo } from '@/lib/labels'
import UiSheet from '@/components/ui/UiSheet.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const store = useConsoleStore()

const selectedId = ref<string>('aug26-as-happened')
const variantSelections = ref<Record<string, string | number | boolean>>({})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      selectedId.value = store.run?.scenario ?? 'aug26-as-happened'
      variantSelections.value = {}
    }
  },
  { immediate: true },
)

const selectedScenario = computed<ScenarioInfo | undefined>(() =>
  SCENARIOS.find((s) => s.id === selectedId.value),
)

function selectScenario(id: string) {
  selectedId.value = id
  variantSelections.value = {}
}

function getVariantOptions(key: keyof typeof VARIANT_OPTIONS) {
  const config = VARIANT_OPTIONS[key]
  if (!config) return []
  return [
    { value: '__default', label: 'Default' },
    ...config.options.map((opt) => ({
      value: opt,
      label: typeof opt === 'boolean' ? (opt ? 'On' : 'Off') : typeof opt === 'number' ? `${opt} min` : String(opt),
    })),
  ]
}

function setVariant(key: string, val: string | number | boolean) {
  variantSelections.value[key] = val
}

async function handleStart() {
  const customVariants: Record<string, unknown> = {}
  let hasCustom = false

  for (const [key, val] of Object.entries(variantSelections.value)) {
    if (val !== '__default') {
      customVariants[key] = val
      hasCustom = true
    }
  }

  await store.startRun(selectedId.value, hasCustom ? customVariants : null)
  if (!store.errors.run) {
    emit('close')
  }
}
</script>

<template>
  <UiSheet
    :open="open"
    title="Choose a scenario"
    subtitle="Starting a run resets the console."
    @close="emit('close')"
  >
    <div class="scenarios-content">
      <div class="scenarios-list">
        <button
          v-for="s in SCENARIOS"
          :key="s.id"
          type="button"
          class="scenario-item"
          :class="{ 'is-selected': s.id === selectedId }"
          :aria-pressed="s.id === selectedId ? 'true' : 'false'"
          @click="selectScenario(s.id)"
        >
          <div class="scenario-header">
            <UiBadge tone="neutral">{{ s.code }}</UiBadge>
            <span class="scenario-title">{{ s.title }}</span>
          </div>
          <p class="scenario-proves">{{ s.proves }}</p>
        </button>
      </div>

      <div v-if="selectedScenario?.variants?.length" class="variants-section">
        <h3 class="variants-title">Variants</h3>
        <div
          v-for="varKey in selectedScenario.variants"
          :key="varKey"
          class="variant-group"
        >
          <div class="variant-meta">
            <span class="variant-label">{{ VARIANT_OPTIONS[varKey].label }}</span>
            <span class="variant-help">{{ VARIANT_OPTIONS[varKey].help }}</span>
          </div>
          <UiSegmented
            :label="VARIANT_OPTIONS[varKey].label"
            :model-value="variantSelections[varKey] ?? '__default'"
            :options="getVariantOptions(varKey)"
            @update:model-value="(val) => setVariant(varKey, val)"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <div v-if="store.errors.run" class="run-error">
        {{ store.errors.run }}
      </div>
      <UiButton variant="secondary" @click="emit('close')">Cancel</UiButton>
      <UiButton
        variant="primary"
        :icon="Play"
        :busy="store.starting"
        @click="handleStart"
      >
        Start run
      </UiButton>
    </template>
  </UiSheet>
</template>

<style scoped>
.scenarios-content {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

.scenarios-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.scenario-item {
  width: 100%;
  text-align: left;
  padding: var(--sp-3);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface-1);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  cursor: pointer;
  transition: border-color 120ms, background-color 120ms;
}

.scenario-item:hover:not(.is-selected) {
  background: var(--surface-2);
}

.scenario-item.is-selected {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.scenario-header {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.scenario-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}

.scenario-proves {
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: var(--lh-normal);
}

.variants-section {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border);
}

.variants-title {
  font-size: var(--fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-3);
  letter-spacing: 0.04em;
}

.variant-group {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.variant-meta {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--sp-2);
}

.variant-label {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text);
}

.variant-help {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.run-error {
  color: var(--danger);
  font-size: var(--fs-sm);
  margin-right: auto;
}
</style>
