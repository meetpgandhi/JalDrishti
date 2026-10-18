<template>
  <div class="console-layout">
    <!-- Header: corridor name, sim clock, run controls -->
    <header class="console-header">
      <div class="header-left">
        <span class="app-logo">🌊</span>
        <span class="app-name">JalDrishti</span>
        <span class="corridor-name">Bhote Koshi – Trishuli – Narayani</span>
      </div>

      <!-- Simulation Clock Controls & Status -->
      <div class="header-center" v-if="stationStore.hasActiveRun">
        <div class="sim-controls">
          <button class="btn-ctrl" :class="{ active: isPlaying }" @click="togglePlay" title="Play/Pause Simulation">
            {{ isPlaying ? '⏸ Pause' : '▶ Play' }}
          </button>
          <div class="speed-selector text-mono">
            <button
              v-for="s in [1, 2, 10, 60]"
              :key="s"
              class="btn-speed"
              :class="{ active: Math.round(stationStore.simSpeed) === s }"
              @click="stationStore.setRunSpeed(s)"
            >
              {{ s }}×
            </button>
          </div>
        </div>
        <div class="clock-box text-mono">
          <span class="sim-time" :class="{ 'sim-running': isPlaying }">
            {{ displaySimTime }}
          </span>
          <span class="badge-provenance badge-d text-mono">NPT (+05:45)</span>
        </div>
      </div>
      <div class="header-center" v-else>
        <button class="btn-start-replay" @click="startReplay" :disabled="stationStore.isLoading">
          ▶ Run the 26 August 2026 replay
        </button>
      </div>

      <div class="header-right">
        <button class="btn-tool" @click="isWhyOpen = true" title="Why did this incident fire?">
          🔍 Why?
        </button>
        <button class="btn-tool" @click="isReportOpen = true" title="Simulation Replay & Determinism Report">
          📑 Replay Report
        </button>
        <button class="btn-tool btn-tool-warn" @click="isInjectOpen = true" title="Live Injections Stress Test">
          ⚡ Injections
        </button>
        <button class="btn-tool btn-tool-cyan" :class="{ 'btn-active': isCopilotOpen }" @click="isCopilotOpen = !isCopilotOpen" title="Gemini Hydrological Copilot">
          🤖 Copilot
        </button>
        <router-link to="/phone" target="_blank" class="btn-phone-sim">
          📱 Duty Phone Sim
        </router-link>
        <div class="connection-status" :class="{ connected: isConnected }">
          <span class="conn-dot"></span>
          <span>{{ isConnected ? 'SSE Live Stream' : 'Connecting…' }}</span>
        </div>
      </div>
    </header>

    <!-- Incident Banner if active -->
    <section v-if="stationStore.openIncident" class="incident-banner" :class="bannerClass">
      <div class="banner-main">
        <div class="banner-badge">
          <span class="level-indicator">{{ incidentLevelLabel }}</span>
          <span class="badge-provenance badge-d">[D]</span>
          <span v-if="stationStore.openIncident.nonRain" class="badge-non-rain">NON_RAIN FLOOD</span>
        </div>
        <div class="banner-text">
          <h2 class="incident-headline">
            {{ incidentHeadline }}
          </h2>
          <p class="incident-sub text-mono">
            Anchor Gauge: <strong>{{ stationStore.openIncident.anchorStation }}</strong>
            <template v-if="stationStore.openIncident.openedAt">
              · Triggered: {{ formatTime(stationStore.openIncident.openedAt) }}
            </template>
          </p>
        </div>
      </div>
      <div class="banner-actions">
        <button class="btn-why-banner" @click="isWhyOpen = true">
          🔍 Why did this fire?
        </button>
        <button class="btn-ack-nav" @click="activeTab = 'bands'">
          View Arrival Bands ({{ stationStore.arrivalBands.length }})
        </button>
      </div>
    </section>

    <!-- Station strip: one tile per gauge -->
    <section class="station-strip-wrapper" aria-label="Gauge station strip">
      <StationStrip :stations="stationStore.stations" />
    </section>

    <!-- Main panels -->
    <main class="console-main">
      <!-- Incident & Arrival Bands panel -->
      <section class="incident-panel card" aria-label="Active incident">
        <div class="panel-header">
          <h2 class="panel-title">Downstream Arrival Bands</h2>
          <span class="badge-provenance badge-d">[D] Calibrated on 26 Aug</span>
        </div>

        <div v-if="stationStore.arrivalBands.length > 0" class="bands-table-container">
          <table class="bands-table text-mono">
            <thead>
              <tr>
                <th>Site</th>
                <th>River km</th>
                <th>Earliest Arrival</th>
                <th>Latest Arrival</th>
                <th>Window Span</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="b in stationStore.arrivalBands" :key="b.siteId">
                <td class="site-name">{{ b.siteName }}</td>
                <td>km {{ b.riverKm.toFixed(0) }}</td>
                <td class="text-arrival">{{ formatTime(b.earliestArrival) }}</td>
                <td class="text-arrival">{{ formatTime(b.latestArrival) }}</td>
                <td class="text-muted">{{ calculateSpan(b.earliestArrival, b.latestArrival) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-bands text-mono text-muted">
          No active downstream flood wave. Corroborated warnings will project calibrated arrival bands here.
        </div>
      </section>

      <!-- Acknowledgement board (spec Section 9) -->
      <AckBoard :incident-id="stationStore.openIncident?.id" />

      <!-- Comparative timeline (Section 12 proof point) -->
      <ComparisonTimeline :run-id="stationStore.runId" />
    </main>

    <!-- Field-report feed & Gemini integration -->
    <aside class="sidebar-wrapper" aria-label="Field reports & Gemini">
      <ReportFeed />
    </aside>

    <!-- Modals & Copilot Drawer -->
    <WhyModal :isOpen="isWhyOpen" @close="isWhyOpen = false" />
    <ReplayReportModal :isOpen="isReportOpen" :runId="stationStore.runId ?? undefined" @close="isReportOpen = false" />
    <LiveInjectionModal :isOpen="isInjectOpen" :runId="stationStore.runId ?? undefined" @close="isInjectOpen = false" @injected="refreshCorridorState" />
    <CopilotChat :isOpen="isCopilotOpen" :runId="stationStore.runId ?? undefined" @close="isCopilotOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useStationStore } from '@/stores/stationStore'
import { useSSE } from '@/composables/useSSE'
import StationStrip from '@/components/StationStrip.vue'
import AckBoard from '@/components/AckBoard.vue'
import ReportFeed from '@/components/ReportFeed.vue'
import ComparisonTimeline from '@/components/ComparisonTimeline.vue'
import WhyModal from '@/components/WhyModal.vue'
import ReplayReportModal from '@/components/ReplayReportModal.vue'
import LiveInjectionModal from '@/components/LiveInjectionModal.vue'
import CopilotChat from '@/components/CopilotChat.vue'

const stationStore = useStationStore()
const activeTab = ref('bands')

const isWhyOpen = ref(false)
const isReportOpen = ref(false)
const isInjectOpen = ref(false)
const isCopilotOpen = ref(false)

const isPlaying = computed(() => stationStore.simStatus === 'RUNNING')
const displaySimTime = computed(() => {
  if (!stationStore.simTime) return '—'
  try {
    const d = new Date(stationStore.simTime)
    const nptDate = new Date(d.getTime() + (5 * 60 + 45) * 60 * 1000)
    return nptDate.toISOString().substring(11, 19)
  } catch (e) {
    return '—'
  }
})

// Connect live SSE stream once a run exists; useSSE reconnects automatically if runId changes
const { isConnected } = useSSE({
  runId: computed(() => stationStore.runId || 'live'),
  onReading: (data) => stationStore.handleReadingEvent(data),
  onStationState: (data) => stationStore.handleStationStateEvent(data),
  onIncident: (data) => stationStore.handleIncidentEvent(data),
  onBands: (data) => stationStore.handleBandsEvent(data),
  onClock: (data) => stationStore.handleClockEvent(data)
})

const startReplay = async () => {
  await stationStore.startRun('aug26-as-happened')
}

const refreshCorridorState = () => {
  if (stationStore.runId) {
    stationStore.fetchCorridorState(stationStore.runId)
  }
}

const togglePlay = () => {
  if (isPlaying.value) {
    stationStore.pauseRun()
  } else {
    stationStore.playRun()
  }
}

const bannerClass = computed(() => {
  if (stationStore.activeLevel === 'WARNING') return 'banner-warning'
  if (stationStore.activeLevel === 'WATCH') return 'banner-watch'
  return 'banner-normal'
})

const incidentLevelLabel = computed(() => {
  return stationStore.activeLevel === 'WARNING' ? 'WARNING' : 'WATCH'
})

const incidentHeadline = computed(() => {
  const anchor = stationStore.openIncident?.anchorStation
  if (stationStore.activeLevel === 'WARNING') {
    return anchor ? `CORRIDOR WARNING: Cascade corroboration anchored at ${anchor}` : 'CORRIDOR WARNING'
  }
  return anchor ? `INTERNAL WATCH: Overdue telemetry at ${anchor}` : 'INTERNAL WATCH'
})

const formatTime = (isoString: string) => {
  try {
    const d = new Date(isoString)
    // Format to NPT (+05:45)
    const nptMillis = d.getTime() + (5 * 60 + 45) * 60 * 1000
    const nptDate = new Date(nptMillis)
    return nptDate.toISOString().substring(11, 16) + ' NPT'
  } catch (e) {
    return isoString
  }
}

const calculateSpan = (earliest: string, latest: string) => {
  try {
    const d1 = new Date(earliest).getTime()
    const d2 = new Date(latest).getTime()
    const diffMin = Math.round((d2 - d1) / 60000)
    return `±${diffMin} min`
  } catch (e) {
    return '—'
  }
}
</script>

<style scoped>
.console-layout {
  display: grid;
  grid-template-rows: 56px auto auto 1fr;
  grid-template-columns: 1fr 340px;
  grid-template-areas:
    "header  header"
    "banner  banner"
    "strip   strip"
    "main    feed"
    ;
  min-height: 100vh;
  gap: var(--space-3);
  padding: var(--space-3);
  background: radial-gradient(circle at 10% 20%, #0d1117 0%, #080c10 100%);
}

.console-header {
  grid-area: header;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-4);
  background: rgba(22, 27, 34, 0.7);
  backdrop-filter: blur(16px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.app-logo {
  font-size: 20px;
}

.app-name {
  font-weight: 800;
  font-size: 18px;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, #ffffff 0%, #94a3b8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.corridor-name {
  color: var(--color-text-muted);
  font-size: 13px;
  margin-left: var(--space-2);
}

.header-center {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.sim-controls {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  background: rgba(13, 17, 23, 0.6);
  padding: 3px 6px;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(48, 54, 61, 0.6);
}

.btn-ctrl {
  background: #238636;
  color: #fff;
  border: none;
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-ctrl:hover {
  background: #2ea043;
}

.btn-start-replay {
  background: #238636;
  color: #fff;
  border: none;
  padding: 8px 18px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-start-replay:hover {
  background: #2ea043;
}

.btn-start-replay:disabled {
  opacity: 0.6;
  cursor: wait;
}

.speed-selector {
  display: flex;
  gap: 2px;
}

.btn-speed {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  padding: 2px 6px;
  font-size: 11px;
  border-radius: 2px;
  cursor: pointer;
}

.btn-speed.active {
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  font-weight: 700;
}

.clock-box {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.sim-time {
  font-size: 16px;
  font-weight: 700;
  color: #e2e8f0;
}

.btn-tool {
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
  border: 1px solid rgba(255, 255, 255, 0.15);
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-tool:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.3);
}

.btn-tool-warn {
  color: #f59e0b;
  border-color: rgba(245, 158, 11, 0.4);
  background: rgba(245, 158, 11, 0.1);
}

.btn-tool-warn:hover {
  background: rgba(245, 158, 11, 0.25);
  border-color: #f59e0b;
}

.btn-tool-cyan {
  color: #38bdf8;
  border-color: rgba(56, 189, 248, 0.4);
  background: rgba(56, 189, 248, 0.1);
}

.btn-tool-cyan:hover, .btn-tool-cyan.btn-active {
  background: rgba(56, 189, 248, 0.25);
  border-color: #38bdf8;
  color: #fff;
}

.btn-why-banner {
  background: rgba(56, 189, 248, 0.2);
  color: #7dd3fc;
  border: 1px solid #38bdf8;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-why-banner:hover {
  background: #38bdf8;
  color: #000;
}

.btn-phone-sim {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid #38bdf8;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 700;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-phone-sim:hover {
  background: #38bdf8;
  color: #000;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.conn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #6e7681;
}

.connection-status.connected .conn-dot {
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
}

.incident-banner {
  grid-area: banner;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid;
}

.banner-warning {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.25) 0%, rgba(30, 20, 25, 0.95) 100%);
  border-color: rgba(239, 68, 68, 0.7);
  box-shadow: 0 0 24px rgba(239, 68, 68, 0.25);
}

.banner-watch {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.2) 0%, rgba(30, 25, 20, 0.95) 100%);
  border-color: rgba(245, 158, 11, 0.6);
}

.banner-main {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.banner-badge {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.level-indicator {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 900;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  background: #ef4444;
  color: #fff;
  letter-spacing: 0.05em;
}

.badge-non-rain {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  background: rgba(245, 158, 11, 0.25);
  color: #f59e0b;
  border: 1px solid #f59e0b;
}

.incident-headline {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.incident-sub {
  font-size: 12px;
  color: #cbd5e1;
  margin-top: 2px;
}

.btn-ack-nav {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-ack-nav:hover {
  background: rgba(255, 255, 255, 0.2);
}

.station-strip-wrapper {
  grid-area: strip;
}

.console-main {
  grid-area: main;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}

.panel-title {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #94a3b8;
}

.bands-table-container {
  overflow-x: auto;
}

.bands-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.bands-table th {
  text-align: left;
  padding: 8px 12px;
  color: #64748b;
  border-bottom: 1px solid rgba(48, 54, 61, 0.6);
  font-weight: 600;
}

.bands-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(48, 54, 61, 0.3);
}

.site-name {
  font-weight: 600;
  color: #f1f5f9;
}

.text-arrival {
  color: #38bdf8;
  font-weight: 600;
}

.empty-bands {
  font-size: 12px;
  padding: var(--space-4);
  text-align: center;
}

.ack-preview {
  display: flex;
  gap: var(--space-4);
}

.ack-stat {
  display: flex;
  flex-direction: column;
  background: rgba(13, 17, 23, 0.5);
  border: 1px solid rgba(48, 54, 61, 0.5);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-sm);
  min-width: 120px;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #f8fafc;
}

.stat-num.has-unacked {
  color: #ef4444;
}

.stat-label {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.report-feed {
  grid-area: feed;
  display: flex;
  flex-direction: column;
}

.feed-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.feed-card {
  background: rgba(13, 17, 23, 0.7);
  border: 1px solid rgba(48, 54, 61, 0.8);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.feed-card.official {
  border-left: 3px solid #38bdf8;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.badge-trust {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 2px;
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
}

.card-time {
  font-size: 11px;
  color: #64748b;
}

.card-text {
  font-size: 14px;
  line-height: 1.4;
  color: #f8fafc;
}

.card-trans {
  font-size: 11px;
  font-style: italic;
  margin-top: 2px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #94a3b8;
  padding-top: var(--space-1);
  border-top: 1px solid rgba(48, 54, 61, 0.4);
}
</style>
