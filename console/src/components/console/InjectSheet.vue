<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { CircleCheck, CircleX } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { humanizeKey } from '@/lib/labels'
import type { InjectionRequest, Trust } from '@/types/api'
import UiSheet from '@/components/ui/UiSheet.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const store = useConsoleStore()

// State for 5 blocks
const station1 = ref<string>('')
const isBusy1Cut = ref(false)
const isBusy1Restore = ref(false)

const station2 = ref<string>('')
const levelM2 = ref<number>(4)
const isBusy2 = ref(false)

const station3 = ref<string>('')
const rainMm3 = ref<number>(20)
const isBusy3 = ref(false)

const reportText4 = ref<string>('')
const reportRole4 = ref<Trust>('OFFICIAL')
const isBusy4 = ref(false)

const heightenedActive5 = ref<boolean>(true)
const isBusy5 = ref(false)

interface LoggedResult {
  success: boolean
  type: string
  target: string
  message: string
}

const results = ref<LoggedResult[]>([])

const initialStationId = computed(() => store.stationsSorted[0]?.stationId ?? '')

watch(
  initialStationId,
  (id) => {
    if (id) {
      if (!station1.value) station1.value = id
      if (!station2.value) {
        station2.value = id
        levelM2.value = store.stationsSorted[0]?.warningM ?? 4
      }
      if (!station3.value) station3.value = id
    }
  },
  { immediate: true },
)

function onStation2Change() {
  const s = store.stationsSorted.find((x) => x.stationId === station2.value)
  if (s) {
    levelM2.value = s.warningM ?? 4
  }
}

async function executeInject(
  req: InjectionRequest,
  setBusy: (b: boolean) => void,
) {
  setBusy(true)
  try {
    const res = await store.inject(req)
    results.value = [
      {
        success: res.success,
        type: res.type,
        target: res.target,
        message: res.message,
      },
      ...results.value.slice(0, 5),
    ]
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    results.value = [
      {
        success: false,
        type: req.type,
        target: req.stationId ?? 'corridor',
        message: msg,
      },
      ...results.value.slice(0, 5),
    ]
  } finally {
    setBusy(false)
  }
}

async function cutTelemetry() {
  await executeInject({ type: 'DESTROY_GAUGE', stationId: station1.value }, (b) => (isBusy1Cut.value = b))
}

async function restoreTelemetry() {
  await executeInject({ type: 'RESTORE_GAUGE', stationId: station1.value }, (b) => (isBusy1Restore.value = b))
}

async function setWaterLevel() {
  await executeInject({ type: 'SET_LEVEL', stationId: station2.value, levelM: Number(levelM2.value) }, (b) => (isBusy2.value = b))
}

async function injectRain() {
  await executeInject({ type: 'RAIN', stationId: station3.value, rainMm: Number(rainMm3.value) }, (b) => (isBusy3.value = b))
}

async function submitReport() {
  if (!reportText4.value.trim()) return
  await executeInject(
    { type: 'REPORT', reportText: reportText4.value.trim(), role: reportRole4.value },
    (b) => (isBusy4.value = b),
  )
  reportText4.value = ''
}

async function applyHeightenedWatch() {
  await executeInject({ type: 'HEIGHTENED_WATCH', active: heightenedActive5.value }, (b) => (isBusy5.value = b))
}

const toggleOptions = [
  { value: true, label: 'On' },
  { value: false, label: 'Off' },
]
</script>

<template>
  <UiSheet
    :open="open"
    :modal="false"
    title="Inject an event"
    subtitle="Test the real rule engine during a run. Every injection is logged and appears in the replay report."
    @close="emit('close')"
  >
    <UiEmptyState
      v-if="!store.runId"
      title="Start a run first"
      body="Live injections require an active simulation run to test the rule engine."
    />

    <div v-else class="inject-content">
      <!-- Block 1: Gauge telemetry -->
      <section class="inject-block">
        <div class="block-header">
          <h3 class="block-title">Gauge telemetry</h3>
          <span class="block-exercise">Exercises R3 · Silence, R5 · Cascade, R11 · Auto-cancel</span>
        </div>
        <div class="block-row">
          <select v-model="station1" class="station-select">
            <option
              v-for="s in store.stationsSorted"
              :key="s.stationId"
              :value="s.stationId"
            >
              {{ s.stationId }} · {{ s.name }}
            </option>
          </select>
          <div class="actions-group">
            <UiButton
              variant="danger"
              size="sm"
              :busy="isBusy1Cut"
              @click="cutTelemetry"
            >
              Cut telemetry
            </UiButton>
            <UiButton
              variant="secondary"
              size="sm"
              :busy="isBusy1Restore"
              @click="restoreTelemetry"
            >
              Restore
            </UiButton>
          </div>
        </div>
      </section>

      <!-- Block 2: Set water level -->
      <section class="inject-block">
        <div class="block-header">
          <h3 class="block-title">Set a water level</h3>
          <span class="block-exercise">Exercises R1 · Height, R2 · Rate of rise</span>
        </div>
        <div class="block-row">
          <select
            v-model="station2"
            class="station-select"
            @change="onStation2Change"
          >
            <option
              v-for="s in store.stationsSorted"
              :key="s.stationId"
              :value="s.stationId"
            >
              {{ s.stationId }} · {{ s.name }}
            </option>
          </select>
          <div class="level-input-box">
            <label for="inject-level-m" class="sr-only">Level (m)</label>
            <input
              id="inject-level-m"
              v-model.number="levelM2"
              type="number"
              step="0.1"
              min="0"
              placeholder="Level (m)"
            />
          </div>
          <UiButton
            variant="secondary"
            size="sm"
            :busy="isBusy2"
            @click="setWaterLevel"
          >
            Set level
          </UiButton>
        </div>
      </section>

      <!-- Block 3: Rain -->
      <section class="inject-block">
        <div class="block-header">
          <h3 class="block-title">Rain</h3>
          <span class="block-exercise">Exercises R6 · Rise without rain</span>
        </div>
        <div class="block-row">
          <select v-model="station3" class="station-select">
            <option
              v-for="s in store.stationsSorted"
              :key="s.stationId"
              :value="s.stationId"
            >
              {{ s.stationId }} · {{ s.name }}
            </option>
          </select>
          <div class="level-input-box">
            <label for="inject-rain-mm" class="sr-only">Rain (mm)</label>
            <input
              id="inject-rain-mm"
              v-model.number="rainMm3"
              type="number"
              min="0"
              placeholder="Rain (mm)"
            />
          </div>
          <UiButton
            variant="secondary"
            size="sm"
            :busy="isBusy3"
            @click="injectRain"
          >
            Set rain
          </UiButton>
        </div>
      </section>

      <!-- Block 4: Field report -->
      <section class="inject-block">
        <div class="block-header">
          <h3 class="block-title">Field report</h3>
          <span class="block-exercise">Exercises R7 · Trusted report, R8 · Observer reports</span>
        </div>
        <div class="block-col">
          <textarea
            v-model="reportText4"
            rows="2"
            class="deva"
            placeholder="Type field report observation"
          />
          <div class="report-block-actions">
            <select v-model="reportRole4" class="role-select">
              <option value="OFFICIAL">Official</option>
              <option value="OBSERVER">Observer</option>
            </select>
            <UiButton
              variant="primary"
              size="sm"
              :disabled="!reportText4.trim()"
              :busy="isBusy4"
              @click="submitReport"
            >
              Inject report
            </UiButton>
          </div>
        </div>
      </section>

      <!-- Block 5: Heightened watch -->
      <section class="inject-block">
        <div class="block-header">
          <h3 class="block-title">Heightened watch</h3>
          <span class="block-exercise">Exercises R10 · Heightened watch</span>
        </div>
        <div class="block-row">
          <UiSegmented
            label="Heightened watch mode"
            :model-value="heightenedActive5"
            :options="toggleOptions"
            @update:model-value="(val) => heightenedActive5 = Boolean(val)"
          />
          <UiButton
            variant="secondary"
            size="sm"
            :busy="isBusy5"
            @click="applyHeightenedWatch"
          >
            Apply
          </UiButton>
        </div>
      </section>

      <!-- Results list -->
      <div v-if="results.length > 0" class="results-section">
        <h4 class="results-title">Results</h4>
        <div class="results-list">
          <div
            v-for="(res, idx) in results"
            :key="idx"
            class="result-item"
          >
            <component
              :is="res.success ? CircleCheck : CircleX"
              :size="16"
              aria-hidden="true"
              :class="res.success ? 'icon-success' : 'icon-fail'"
            />
            <div class="result-details">
              <span class="result-target">{{ humanizeKey(res.type) }} · {{ res.target }}</span>
              <span class="result-msg">{{ res.message }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </UiSheet>
</template>

<style scoped>
.inject-content {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.inject-block {
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  background: var(--surface-1);
}

.block-header {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.block-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.block-exercise {
  font-size: var(--fs-xs);
  color: var(--text-3);
  line-height: var(--lh-normal);
}

.block-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.block-col {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.station-select {
  flex: 1;
  min-width: 10rem;
}

.level-input-box {
  width: 6rem;
}

.actions-group {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.report-block-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-2);
}

.role-select {
  width: auto;
  min-width: 7rem;
}

.results-section {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--border);
}

.results-title {
  font-size: var(--fs-xs);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-3);
  letter-spacing: 0.04em;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.result-item {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  padding: var(--sp-2);
  background: var(--surface-2);
  border-radius: var(--r-sm);
}

.icon-success {
  color: var(--ok);
  flex-shrink: 0;
  margin-top: 2px;
}

.icon-fail {
  color: var(--danger);
  flex-shrink: 0;
  margin-top: 2px;
}

.result-details {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.result-target {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text);
}

.result-msg {
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: var(--lh-normal);
}
</style>
