<template>
  <div class="comparison-timeline card">
    <div class="timeline-header">
      <div class="header-title">
        <h3>26 August 2026 Disaster Timeline — Comparative Analysis</h3>
        <span class="badge badge-p">[P]</span>
      </div>
      <div class="delta-badge text-mono">
        ⚡ 22 MINUTES SAVED (Warning at 08:53 vs 09:15 Phone Call)
      </div>
    </div>

    <div class="tracks-container">
      <!-- Track 1: As Happened -->
      <div class="timeline-track track-as-happened">
        <div class="track-label">
          <span class="track-name text-mono">AS HAPPENED (Actual DHM Record)</span>
          <span class="badge badge-p">[P]</span>
        </div>
        <div class="events-rail">
          <div class="event-node node-danger" style="left: 10%">
            <div class="time-label text-mono">08:40</div>
            <div class="event-desc">Lende Khola outburst</div>
          </div>
          <div class="event-node node-muted" style="left: 25%">
            <div class="time-label text-mono">08:50</div>
            <div class="event-desc">RSW & SYP silent</div>
          </div>
          <div class="event-node node-amber" style="left: 50%">
            <div class="time-label text-mono">09:15</div>
            <div class="event-desc">📞 Phone call from Dhunche</div>
          </div>
          <div class="event-node node-danger" style="left: 70%">
            <div class="time-label text-mono">09:46</div>
            <div class="event-desc">Flood hits Betrawati</div>
          </div>
          <div class="event-node node-late" style="left: 95%">
            <div class="time-label text-mono">11:20</div>
            <div class="event-desc">Public SMS broadcast (Late)</div>
          </div>
        </div>
      </div>

      <!-- Track 2: JalDrishti -->
      <div class="timeline-track track-jaldrishti">
        <div class="track-label">
          <span class="track-name text-mono text-cyan">JALDRISHTI (Silent Cascade R5)</span>
          <span class="badge badge-d">[D]</span>
        </div>
        <div class="events-rail">
          <div class="event-node node-danger" style="left: 10%">
            <div class="time-label text-mono">08:40</div>
            <div class="event-desc">Lende Khola outburst</div>
          </div>
          <div class="event-node node-highlight" style="left: 28%">
            <div class="time-label text-mono text-cyan">08:53</div>
            <div class="event-desc">🚨 JalDrishti WARNING Fired (R5)</div>
          </div>
          <div class="event-node node-success" style="left: 35%">
            <div class="time-label text-mono">08:54</div>
            <div class="event-desc">✓ Gates Closed & Evac Started</div>
          </div>
          <div class="event-node node-ok" style="left: 70%">
            <div class="time-label text-mono">09:46</div>
            <div class="event-desc">Crest reaches Betrawati (Evacuated)</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// Comparison timeline static & reactive markers
</script>

<style scoped>
.comparison-timeline {
  padding: var(--space-4);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-top: var(--space-4);
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
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
  font-size: 15px;
  font-weight: 700;
}

.delta-badge {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid #38bdf8;
  padding: 3px 10px;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.tracks-container {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.timeline-track {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.track-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  font-weight: 700;
}

.text-cyan {
  color: #38bdf8;
}

.events-rail {
  position: relative;
  height: 64px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  margin-top: var(--space-1);
}

.events-rail::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 2px;
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-50%);
}

.event-node {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  white-space: nowrap;
}

.event-node::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-text-muted);
  border: 2px solid var(--color-bg);
  z-index: 2;
  margin-bottom: 2px;
}

.node-danger::before {
  background: #ef4444;
}

.node-amber::before {
  background: #f59e0b;
}

.node-success::before, .node-ok::before {
  background: #22c55e;
}

.node-highlight::before {
  background: #38bdf8;
  box-shadow: 0 0 10px #38bdf8;
  width: 14px;
  height: 14px;
}

.time-label {
  font-size: 11px;
  font-weight: 700;
}

.event-desc {
  font-size: 10px;
  color: var(--color-text-muted);
}
</style>
