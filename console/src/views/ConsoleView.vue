<script setup lang="ts">
import { ref, toRef, onMounted, watch } from 'vue'
import { useConsoleStore, type ForecasterAction } from '@/stores/console'
import { useSSE } from '@/composables/useSSE'
import { LEVELS } from '@/lib/labels'
import TopBar from '@/components/console/TopBar.vue'
import SimBar from '@/components/console/SimBar.vue'
import StatusHeadline from '@/components/console/StatusHeadline.vue'
import RiverStrip from '@/components/console/RiverStrip.vue'
import ActionBoard from '@/components/console/ActionBoard.vue'
import LeadTimeTimeline from '@/components/console/LeadTimeTimeline.vue'
import EvidenceTrail from '@/components/console/EvidenceTrail.vue'
import FieldReports from '@/components/console/FieldReports.vue'
import PeopleAtRisk from '@/components/people/PeopleAtRisk.vue'
import MessagesLog from '@/components/messages/MessagesLog.vue'
import ScenarioSheet from '@/components/console/ScenarioSheet.vue'
import InjectSheet from '@/components/console/InjectSheet.vue'
import CopilotSheet from '@/components/console/CopilotSheet.vue'
import WhySheet from '@/components/console/WhySheet.vue'
import ReplayReportModal from '@/components/console/ReplayReportModal.vue'
import ForecasterActionDialog from '@/components/console/ForecasterActionDialog.vue'

const store = useConsoleStore()

const { isConnected } = useSSE(toRef(store, 'runId'), {
  onReading: store.onReading,
  onStationState: store.onStationState,
  onClock: store.onClock,
  onIncident: store.onIncidentChanged,
  onEvidence: store.onIncidentChanged,
  onBands: store.onBands,
})

onMounted(() => {
  void store.init()
})

const view = ref<'operations' | 'people' | 'messages'>('operations')

const scenarioOpen = ref(false)
const injectOpen = ref(false)
const reportOpen = ref(false)
const copilotOpen = ref(false)

const why = ref<{ incidentId: string; seq: number | null } | null>(null)
const pendingAction = ref<ForecasterAction | null>(null)

const announcement = ref('')
const flash = ref(false)

function openWhyForIncident() {
  if (store.incident) {
    why.value = { incidentId: store.incident.id, seq: null }
  }
}

watch(
  () => store.level,
  (lvl, prev) => {
    if (store.runId && lvl !== prev) {
      announcement.value = `Corridor level is now ${LEVELS[lvl].label}`
      flash.value = true
      setTimeout(() => {
        flash.value = false
      }, 1800)
    }
  },
)
</script>

<template>
  <div class="console">
    <TopBar
      :connected="isConnected"
      :copilot-open="copilotOpen"
      :view="view"
      @navigate="view = $event"
      @toggle-copilot="copilotOpen = !copilotOpen"
    />
    <SimBar
      v-if="store.mode === 'training'"
      @open-scenario="scenarioOpen = true"
      @open-inject="injectOpen = true"
      @open-report="reportOpen = true"
    />
    <main v-if="view === 'people'" class="people-wrap">
      <PeopleAtRisk />
    </main>
    <main v-else-if="view === 'messages'" class="messages-wrap">
      <MessagesLog />
    </main>
    <main v-else class="console-grid">
      <StatusHeadline
        class="area-status"
        :flash="flash"
        @why="openWhyForIncident"
        @act="pendingAction = $event"
      />
      <RiverStrip class="area-river" />
      <ActionBoard class="area-actions" />
      <LeadTimeTimeline class="area-timeline" />
      <div class="area-rail">
        <EvidenceTrail @open="why = $event" />
        <FieldReports />
      </div>
    </main>

    <ScenarioSheet :open="scenarioOpen" @close="scenarioOpen = false" />
    <InjectSheet :open="injectOpen" @close="injectOpen = false" />
    <CopilotSheet :open="copilotOpen" @close="copilotOpen = false" />
    <WhySheet
      :open="why !== null"
      :incident-id="why?.incidentId ?? null"
      :seq="why?.seq ?? null"
      @close="why = null"
    />
    <ReplayReportModal :open="reportOpen" @close="reportOpen = false" />
    <ForecasterActionDialog :kind="pendingAction" @close="pendingAction = null" />

    <div class="sr-only" aria-live="polite">{{ announcement }}</div>
  </div>
</template>

<style scoped>
.console {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.people-wrap,
.messages-wrap {
  padding: var(--sp-4) var(--sp-6) var(--sp-8);
  max-width: 1680px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.console-grid {
  display: grid;
  gap: var(--sp-4);
  padding: var(--sp-4) var(--sp-6) var(--sp-8);
  max-width: 1680px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr) var(--rail-w);
  grid-template-areas:
    "status   status"
    "river    river"
    "actions  rail"
    "timeline rail";
  align-items: start;
}

.area-status   { grid-area: status; }
.area-river    { grid-area: river; }
.area-actions  { grid-area: actions; }
.area-timeline { grid-area: timeline; }
.area-rail     { grid-area: rail; display: flex; flex-direction: column; gap: var(--sp-4); min-width: 0; }

@media (max-width: 1279px) {
  .console-grid {
    grid-template-columns: minmax(0, 1fr) 320px;
  }
}

@media (max-width: 1023px) {
  .console-grid {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: "status" "river" "actions" "timeline" "rail";
    padding: var(--sp-3);
  }
}
</style>
