<template>
  <div v-if="isOpen" class="modal-backdrop" @click.self="close">
    <div class="why-modal card">
      <div class="modal-header">
        <div class="title-group">
          <span class="rule-chip text-mono">Rule R5</span>
          <h3>Decision Audit & Physical Derivation</h3>
        </div>
        <button class="btn-close" @click="close">✕</button>
      </div>

      <div class="modal-body">
        <div class="claim-banner">
          <span class="claim-title text-mono">AUTOMATED WARNING RATIONALE:</span>
          <p class="claim-text">
            Corridor WARNING fired at <strong>08:53:00 NPT</strong> based on upstream-to-downstream telemetry loss in physical river order without waiting for telephone confirmation.
          </p>
        </div>

        <div class="formula-box text-mono">
          <div class="formula-title">Physical Order-Consistency Formula (ADR-01):</div>
          <div class="formula-code">
            t_earliest = t_lastGood_i + (D_ij / v_max)  ·  v_max = 35.0 m/s<br>
            t_latest   = t_firstMissed_i + (D_ij / v_min) ·  v_min = 3.0 m/s
          </div>
        </div>

        <div class="metrics-grid text-mono">
          <div class="metric-item">
            <span class="m-label">Anchor Gauge (i):</span>
            <span class="m-val text-bold">RSW (Rasuwagadhi)</span>
          </div>
          <div class="metric-item">
            <span class="m-label">Downstream Gauge (j):</span>
            <span class="m-val text-bold">SYP (Syaphrubesi)</span>
          </div>
          <div class="metric-item">
            <span class="m-label">Corridor Distance:</span>
            <span class="m-val">15.0 km</span>
          </div>
          <div class="metric-item">
            <span class="m-label">Wave Velocity Bounds:</span>
            <span class="m-val">[3.0, 35.0] m/s [A]</span>
          </div>
          <div class="metric-item full-width">
            <span class="m-label">Calculated Arrival Window:</span>
            <span class="m-val text-cyan text-bold">[08:47:08 NPT, 10:13:20 NPT]</span>
          </div>
          <div class="metric-item full-width">
            <span class="m-label">Gauge j Silence Interval:</span>
            <span class="m-val text-bold text-ok">(08:40, 08:50] NPT — OVERLAPS (100% Consistent)</span>
          </div>
        </div>

        <div class="audit-footer text-mono">
          <span>Config Version: 1</span>
          <span>Provenance: <span class="badge badge-d">[D] Physical Wave Model</span></span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  isOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

function close() {
  emit('close')
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

.why-modal {
  width: 100%;
  max-width: 580px;
  background: #0f172a;
  border: 1px solid #38bdf8;
  border-radius: var(--radius-lg);
  box-shadow: 0 25px 50px -12px rgba(56, 189, 248, 0.25);
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: rgba(56, 189, 248, 0.1);
  border-bottom: 1px solid rgba(56, 189, 248, 0.2);
}

.title-group {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.rule-chip {
  background: #38bdf8;
  color: #000;
  font-weight: 800;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
}

.modal-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.btn-close {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  font-size: 16px;
  cursor: pointer;
}

.btn-close:hover {
  color: #fff;
}

.modal-body {
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.claim-banner {
  background: rgba(255, 255, 255, 0.03);
  border-left: 3px solid #38bdf8;
  padding: var(--space-3);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.claim-title {
  font-size: 10px;
  color: #38bdf8;
  display: block;
  margin-bottom: 4px;
}

.claim-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.formula-box {
  background: #090d16;
  border: 1px solid var(--color-border);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  font-size: 11px;
}

.formula-title {
  color: var(--color-text-muted);
  margin-bottom: 6px;
}

.formula-code {
  color: #38bdf8;
  line-height: 1.6;
}

.metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2);
  background: rgba(255, 255, 255, 0.02);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  font-size: 11px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-item.full-width {
  grid-column: span 2;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: var(--space-2);
}

.m-label {
  color: var(--color-text-muted);
  font-size: 10px;
}

.text-cyan {
  color: #38bdf8;
}

.text-ok {
  color: #22c55e;
}

.audit-footer {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--color-text-muted);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-2);
}
</style>
