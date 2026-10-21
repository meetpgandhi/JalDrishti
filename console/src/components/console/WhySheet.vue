<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { api, endpoints } from '@/lib/api'
import { ruleLabel, RULES, PROVENANCE, humanizeKey } from '@/lib/labels'
import { formatNpt } from '@/lib/time'
import type { WhyExplanation } from '@/types/api'
import UiSheet from '@/components/ui/UiSheet.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiProvenance from '@/components/ui/UiProvenance.vue'

const props = defineProps<{
  open: boolean
  incidentId: string | null
  seq: number | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const explanation = ref<WhyExplanation | null>(null)
const isLoading = ref(false)
const errorMsg = ref<string | null>(null)

async function loadExplanation() {
  if (!props.incidentId) {
    explanation.value = null
    return
  }
  isLoading.value = true
  errorMsg.value = null
  try {
    const url =
      props.seq !== null
        ? endpoints.eventWhy(props.incidentId, props.seq)
        : endpoints.why(props.incidentId)
    explanation.value = await api.get<WhyExplanation>(url)
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : String(e)
    explanation.value = null
  } finally {
    isLoading.value = false
  }
}

watch(
  () => [props.open, props.incidentId, props.seq],
  ([isOpen]) => {
    if (isOpen) {
      void loadExplanation()
    } else {
      explanation.value = null
      errorMsg.value = null
    }
  },
  { immediate: true },
)

const subtitle = computed(() => {
  if (!explanation.value) return undefined
  const x = explanation.value
  return `${ruleLabel(x.ruleId)} · ${x.timeNpt} · config v${x.configVersion}`
})

const ruleInfo = computed(() => {
  if (!explanation.value?.ruleId) return null
  return RULES[explanation.value.ruleId] ?? null
})

function formatInputValue(v: unknown): string {
  if (typeof v === 'string') {
    if (/^\d{4}-\d{2}-\d{2}T/.test(v)) {
      return `${formatNpt(v, true)} NPT`
    }
    return v
  }
  if (typeof v === 'number') {
    return Number.isInteger(v) ? String(v) : v.toFixed(2)
  }
  if (typeof v === 'boolean') {
    return v ? 'Yes' : 'No'
  }
  if (Array.isArray(v)) {
    return v.map((item) => (typeof item === 'object' && item !== null ? JSON.stringify(item) : String(item))).join(', ')
  }
  return ''
}

function isComplexObject(v: unknown): boolean {
  return typeof v === 'object' && v !== null && !Array.isArray(v)
}
</script>

<template>
  <UiSheet
    :open="open"
    title="Why this decision?"
    :subtitle="subtitle"
    @close="emit('close')"
  >
    <!-- No incident state -->
    <UiEmptyState
      v-if="!incidentId"
      title="No open incident"
      body="Decisions and explanations are recorded for open incidents and timeline events."
    />

    <!-- Loading skeleton -->
    <div v-else-if="isLoading" class="skeletons-wrapper">
      <div class="skeleton skeleton-bar-large" />
      <div class="skeleton skeleton-bar-medium" />
      <div class="skeleton skeleton-bar-small" />
    </div>

    <!-- Error state -->
    <UiEmptyState
      v-else-if="errorMsg"
      tone="danger"
      title="Could not load the explanation"
      :body="errorMsg"
    >
      <UiButton variant="secondary" @click="loadExplanation">Retry</UiButton>
    </UiEmptyState>

    <!-- Content -->
    <div v-else-if="explanation" class="why-content">
      <!-- Section 1: What the rule found -->
      <section class="why-section">
        <h3 class="section-title">What the rule found</h3>
        <p class="explanation-text">{{ explanation.explanation }}</p>
      </section>

      <!-- Section 2: Inputs the rule used -->
      <section class="why-section">
        <h3 class="section-title">Inputs the rule used</h3>
        <div
          v-if="Object.keys(explanation.mathematicalInputs || {}).length > 0"
          class="inputs-table-wrapper"
        >
          <table class="inputs-table">
            <thead>
              <tr>
                <th>Input</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(val, key) in explanation.mathematicalInputs"
                :key="key"
              >
                <td class="key-cell">{{ humanizeKey(key) }}</td>
                <td class="val-cell">
                  <pre
                    v-if="isComplexObject(val)"
                    class="mono object-preview"
                  >{{ JSON.stringify(val, null, 2) }}</pre>
                  <span v-else class="mono">{{ formatInputValue(val) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="no-inputs-text">This decision recorded no numeric inputs.</p>
      </section>

      <!-- Section 3: About this rule -->
      <section v-if="ruleInfo" class="why-section">
        <h3 class="section-title">About this rule</h3>
        <dl class="rule-details-list">
          <dt>Rule</dt>
          <dd>{{ ruleLabel(explanation.ruleId) }}</dd>
          <dt>Fires when</dt>
          <dd>{{ ruleInfo.fires }}</dd>
          <dt>Result</dt>
          <dd>{{ ruleInfo.result }}</dd>
        </dl>
      </section>

      <!-- Section 4: Provenance -->
      <section class="why-section">
        <h3 class="section-title">Provenance</h3>
        <div class="provenance-detail">
          <UiProvenance :tag="explanation.tag" />
          <div class="provenance-texts">
            <span class="prov-name">{{ PROVENANCE[explanation.tag].label }}</span>
            <span class="prov-meaning">{{ PROVENANCE[explanation.tag].meaning }}</span>
          </div>
        </div>
      </section>

      <p class="bottom-note">
        Alert levels come from deterministic rules over stored inputs and a versioned configuration. Gemini never sets an alert level.
      </p>
    </div>
  </UiSheet>
</template>

<style scoped>
.skeletons-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
}

.skeleton-bar-large {
  height: 4rem;
  width: 100%;
}

.skeleton-bar-medium {
  height: 6rem;
  width: 100%;
}

.skeleton-bar-small {
  height: 3rem;
  width: 75%;
}

.why-content {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

.why-section {
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: var(--fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-3);
  letter-spacing: 0.04em;
  margin-bottom: var(--sp-2);
}

.explanation-text {
  font-size: var(--fs-md);
  color: var(--text);
  line-height: var(--lh-normal);
}

.inputs-table-wrapper {
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  overflow: hidden;
}

.inputs-table {
  width: 100%;
}

.key-cell {
  color: var(--text-2);
  font-weight: 500;
  width: 40%;
}

.val-cell {
  color: var(--text);
}

.object-preview {
  font-size: var(--fs-xs);
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.no-inputs-text {
  font-size: var(--fs-sm);
  color: var(--text-3);
}

.rule-details-list {
  display: grid;
  grid-template-columns: 6rem 1fr;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
}

.rule-details-list dt {
  color: var(--text-3);
  font-weight: 500;
}

.rule-details-list dd {
  color: var(--text);
  line-height: var(--lh-normal);
}

.provenance-detail {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
}

.provenance-texts {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.prov-name {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text);
}

.prov-meaning {
  font-size: var(--fs-sm);
  color: var(--text-2);
}

.bottom-note {
  font-size: var(--fs-xs);
  color: var(--text-3);
  line-height: var(--lh-normal);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border);
}
</style>
