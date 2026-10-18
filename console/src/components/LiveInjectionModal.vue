<template>
  <div v-if="isOpen" class="modal-backdrop" @click.self="close">
    <div class="injection-modal card">
      <div class="modal-header">
        <div class="title-group">
          <span class="inject-chip text-mono">FR-16 INJECT</span>
          <h3>Live Scenario Injection & Stress Test</h3>
        </div>
        <button class="btn-close" @click="close">✕</button>
      </div>

      <div class="modal-body">
        <p class="inject-intro text-mono">
          Inject real-time telemetry anomalies, silent gauges, rainfall bursts, or field reports to evaluate Rule R1–R11 engine response during active runs.
        </p>

        <!-- Injection Action Grid -->
        <div class="action-grid">
          <!-- 1. Destroy Upstream Gauge (RSW) -->
          <div class="action-card">
            <div class="card-title text-mono">💥 Sever Telemetry (RSW)</div>
            <p class="card-desc">Simulate physical telemetry loss at Rasuwagadhi. Initiates 3-min silence grace period.</p>
            <button class="btn-action text-mono btn-danger" :disabled="isInjecting" @click="inject('DESTROY_GAUGE', { stationId: 'RSW' })">
              Destroy RSW Gauge
            </button>
          </div>

          <!-- 2. Destroy Downstream Gauge (SYP) -->
          <div class="action-card">
            <div class="card-title text-mono">💥 Sever Downstream (SYP)</div>
            <p class="card-desc">Simulate flood wave reaching Syaphrubesi (15km). Triggers Rule R5 Corridor WARNING.</p>
            <button class="btn-action text-mono btn-warning" :disabled="isInjecting" @click="inject('DESTROY_GAUGE', { stationId: 'SYP' })">
              Destroy SYP Gauge
            </button>
          </div>

          <!-- 3. Water Level Spike -->
          <div class="action-card">
            <div class="card-title text-mono">📈 Force Water Level Spike</div>
            <p class="card-desc">Inject reading exceeding danger threshold (6.8m at Betrawati BTW). Triggers Rule R1.</p>
            <button class="btn-action text-mono btn-primary" :disabled="isInjecting" @click="inject('SET_LEVEL', { stationId: 'BTW', levelM: 6.8 })">
              Spike BTW to 6.8m
            </button>
          </div>

          <!-- 4. Torrential Rain Burst -->
          <div class="action-card">
            <div class="card-title text-mono">🌧️ Torrential Rain Burst</div>
            <p class="card-desc">Inject 55mm rain burst at Rasuwagadhi. Rule R6 evaluates whether flood is rain-driven.</p>
            <button class="btn-action text-mono btn-primary" :disabled="isInjecting" @click="inject('RAIN', { stationId: 'RSW', rainMm: 55.0 })">
              Inject 55mm Rain
            </button>
          </div>

          <!-- 5. Nepali Field Report -->
          <div class="action-card">
            <div class="card-title text-mono">🗣️ Inject Observer Field Report</div>
            <p class="card-desc">Inject Nepali eyewitness report from Syaphrubesi for Gemini extraction and corroboration.</p>
            <button class="btn-action text-mono btn-primary" :disabled="isInjecting" @click="inject('REPORT', { reportText: 'म स्याफ्रुबेसीबाट बोल्दैछु। भोटेकोसीमा ठूलो बाढी आयो!', role: 'OFFICIAL' })">
              Inject Nepali Report
            </button>
          </div>

          <!-- 6. Toggle Heightened Watch (R10) -->
          <div class="action-card">
            <div class="card-title text-mono">⚠️ Toggle Heightened Watch (R10)</div>
            <p class="card-desc">Forecaster heightened watch: halves rate thresholds (R2) and escalates top station silence to WARNING.</p>
            <button class="btn-action text-mono btn-warning" :disabled="isInjecting" @click="inject('HEIGHTENED_WATCH', { active: true })">
              Activate Heightened Watch
            </button>
          </div>
        </div>

        <!-- Result Status Box -->
        <div v-if="lastResult" class="result-box text-mono" :class="lastResult.success ? 'result-ok' : 'result-err'">
          <div class="result-header">
            <span>Result: {{ lastResult.type }} on {{ lastResult.target }}</span>
            <span>{{ new Date(lastResult.at).toLocaleTimeString() }}</span>
          </div>
          <div class="result-msg">{{ lastResult.message }}</div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn-primary text-mono" @click="close">
          Done
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  isOpen: boolean
  runId?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'injected'): void
}>()

const isInjecting = ref(false)
const lastResult = ref<{
  success: boolean
  type: string
  target: string
  message: string
  at: string
} | null>(null)

function close() {
  emit('close')
}

async function inject(type: string, payload: any) {
  isInjecting.value = true
  const rid = props.runId || 'live'
  try {
    const res = await fetch(`/api/v1/sim/runs/${encodeURIComponent(rid)}/inject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type,
        ...payload
      })
    })

    if (res.ok) {
      lastResult.value = await res.json()
      emit('injected')
    } else {
      lastResult.value = {
        success: false,
        type,
        target: payload.stationId || 'CORRIDOR',
        message: 'Injection failed on server. Status ' + res.status,
        at: new Date().toISOString()
      }
    }
  } catch (err: any) {
    lastResult.value = {
      success: false,
      type,
      target: payload.stationId || 'CORRIDOR',
      message: 'Network error executing injection: ' + err.message,
      at: new Date().toISOString()
    }
  } finally {
    isInjecting.value = false
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  padding: var(--space-4);
}

.injection-modal {
  width: 100%;
  max-width: 680px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  background: #0d1117;
  border: 1px solid #f59e0b;
  border-radius: var(--radius-lg);
  box-shadow: 0 25px 50px -12px rgba(245, 158, 11, 0.25);
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: rgba(22, 27, 34, 0.9);
  border-bottom: 1px solid var(--color-border);
}

.title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.inject-chip {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  border: 1px solid rgba(245, 158, 11, 0.4);
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: #f0f6fc;
}

.btn-close {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  font-size: 16px;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.inject-intro {
  font-size: 11px;
  color: #8b949e;
  line-height: 1.5;
}

.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.action-card {
  background: #161b22;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 8px;
}

.card-title {
  font-size: 12px;
  font-weight: 700;
  color: #e6edf3;
}

.card-desc {
  font-size: 11px;
  color: #8b949e;
  line-height: 1.4;
  margin: 0;
  flex: 1;
}

.btn-action {
  width: 100%;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
}

.btn-danger {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.4);
}
.btn-danger:hover:not(:disabled) {
  background: #ef4444;
  color: #fff;
}

.btn-warning {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
  border-color: rgba(245, 158, 11, 0.4);
}
.btn-warning:hover:not(:disabled) {
  background: #f59e0b;
  color: #000;
}

.btn-primary {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border-color: rgba(56, 189, 248, 0.4);
}
.btn-primary:hover:not(:disabled) {
  background: #38bdf8;
  color: #000;
}

.result-box {
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 11px;
}

.result-ok {
  background: rgba(35, 134, 54, 0.2);
  border: 1px solid #238636;
  color: #3fb950;
}

.result-err {
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid #ef4444;
  color: #f87171;
}

.result-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-weight: 600;
}

.result-msg {
  color: #c9d1d9;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
  background: rgba(22, 27, 34, 0.9);
  border-top: 1px solid var(--color-border);
}
</style>
