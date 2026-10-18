<template>
  <div class="report-feed card">
    <div class="feed-header">
      <div class="header-title">
        <h3>Field Reports & Gemini Parse</h3>
        <span class="badge badge-d">[D]</span>
      </div>
      <span class="model-badge text-mono">gemini-3.6-flash · temp 0</span>
    </div>

    <!-- Quick interactive injection buttons for judges -->
    <div class="demo-actions">
      <span class="section-label text-mono">Live Ingestion Test:</span>
      <button class="demo-btn" @click="sendDemoReport('nepali')">
        🗣 Dhunche Call (Nepali)
      </button>
      <button class="demo-btn" @click="sendDemoReport('hindi')">
        🗣 Galchhi Report (Hindi)
      </button>
      <button class="demo-btn" @click="sendDemoReport('calm')">
        ✓ Calm River (Syaphrubesi)
      </button>
    </div>

    <div v-if="reports.length === 0" class="empty-state text-mono muted">
      No field reports received. Use the buttons above to inject a voice/text report.
    </div>

    <div v-else class="reports-list">
      <div v-for="r in reports" :key="r.id" class="report-card">
        <div class="card-top">
          <div class="sender-info">
            <span class="trust-badge" :class="`trust-${r.trust.toLowerCase()}`">{{ r.trust }}</span>
            <span class="channel-tag text-mono">{{ r.channel }}</span>
          </div>
          <span class="status-tag" :class="`status-${r.status.toLowerCase()}`">{{ r.status }}</span>
        </div>

        <div class="raw-section">
          <p class="raw-text text-devanagari">"{{ r.rawText }}"</p>
        </div>

        <div v-if="r.parsed" class="parsed-box text-mono">
          <div class="parsed-row">
            <span class="parsed-key">Location:</span>
            <span class="parsed-val text-bold">{{ r.parsed.locationId || r.parsed.locationText || 'Unresolved' }}</span>
          </div>
          <div class="parsed-row">
            <span class="parsed-key">Hazard:</span>
            <span class="hazard-badge" :class="`hazard-${r.parsed.hazardType.toLowerCase()}`">
              {{ r.parsed.hazardType }}
            </span>
          </div>
          <div v-if="r.parsed.evidence && r.parsed.evidence.length > 0" class="parsed-row">
            <span class="parsed-key">Evidence:</span>
            <div class="evidence-tags">
              <span v-for="ev in r.parsed.evidence" :key="ev" class="ev-tag">{{ ev }}</span>
            </div>
          </div>
          <div class="parsed-row">
            <span class="parsed-key">Confidence:</span>
            <span class="parsed-val">{{ (r.parsed.confidence * 100).toFixed(0) }}%</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface FieldReportItem {
  id: string
  receivedAt: string
  trust: string
  channel: string
  rawText: string
  status: string
  parsed?: {
    locationId?: string
    locationText?: string
    hazardType: string
    evidence?: string[]
    confidence: number
  }
}

const reports = ref<FieldReportItem[]>([])

const demoPresets = {
  nepali: {
    text: 'म स्याफ्रुबेसीबाट बोल्दैछु। भोटेकोसीमा ठूलो गेग्रान सहितको बाढी आयो, पुल बग्यो, मानिसहरू माथि भाग्दैछन्।',
    role: 'OFFICIAL'
  },
  hindi: {
    text: 'गल्छी के पास त्रिशूली नदी बहुत तेज़ी से बढ़ रही है, पानी मटमैला है और पत्थरों की आवाज़ आ रही है।',
    role: 'OBSERVER'
  },
  calm: {
    text: 'नदी सामान्य छ, पानीको बहाव शान्त छ।',
    role: 'OFFICIAL'
  }
}

async function sendDemoReport(type: 'nepali' | 'hindi' | 'calm') {
  const preset = demoPresets[type]
  try {
    const res = await fetch('/api/v1/reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: preset.text,
        role: preset.role
      })
    })

    if (res.ok) {
      const saved = await res.json()
      let parsedObj = undefined
      if (saved.parsedJson) {
        try {
          parsedObj = JSON.parse(saved.parsedJson)
        } catch (e) {}
      }
      reports.value.unshift({
        id: saved.id,
        receivedAt: saved.receivedAt,
        trust: saved.trust,
        channel: saved.channel,
        rawText: saved.rawText,
        status: saved.status,
        parsed: parsedObj
      })
    }
  } catch (err) {
    console.error('Failed to submit demo report:', err)
  }
}
</script>

<style scoped>
.report-feed {
  padding: var(--space-4);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-top: var(--space-4);
}

.feed-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-border);
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.header-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
}

.model-badge {
  font-size: 11px;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.05);
  padding: 3px 8px;
  border-radius: 4px;
}

.demo-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  flex-wrap: wrap;
}

.section-label {
  font-size: 11px;
  color: var(--color-text-muted);
}

.demo-btn {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  color: var(--color-text);
  padding: 4px 10px;
  border-radius: var(--radius-md);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.demo-btn:hover {
  background: #38bdf8;
  color: #000;
  border-color: #38bdf8;
}

.reports-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.report-card {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2);
}

.sender-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.trust-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.trust-official {
  background: rgba(34, 197, 94, 0.2);
  color: #22c55e;
}

.trust-observer {
  background: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
}

.trust-public {
  background: rgba(148, 163, 184, 0.2);
  color: #94a3b8;
}

.channel-tag {
  font-size: 11px;
  color: var(--color-text-muted);
}

.status-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.status-parsed {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
}

.status-unparsed {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.raw-text {
  margin: 0 0 var(--space-2) 0;
  font-size: 13px;
  line-height: 1.4;
  color: var(--color-text);
}

.parsed-box {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 4px;
  padding: var(--space-2);
  font-size: 11px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.parsed-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.parsed-key {
  color: var(--color-text-muted);
  width: 80px;
}

.hazard-badge {
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 700;
  font-size: 10px;
}

.hazard-debris_flood {
  background: rgba(239, 68, 68, 0.25);
  color: #ef4444;
}

.hazard-rapid_rise {
  background: rgba(245, 158, 11, 0.25);
  color: #f59e0b;
}

.hazard-no_hazard {
  background: rgba(34, 197, 94, 0.2);
  color: #22c55e;
}

.evidence-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.ev-tag {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 10px;
}

.empty-state {
  text-align: center;
  padding: var(--space-6);
}
</style>
