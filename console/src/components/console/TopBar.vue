<script setup lang="ts">
import { Waves, Sparkles, Presentation, Activity, Users, MessageSquareText } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import { usePresentationMode } from '@/composables/usePresentationMode'
import UiButton from '@/components/ui/UiButton.vue'
import DutyPhonePopover from '@/components/console/DutyPhonePopover.vue'

defineProps<{
  connected: boolean
  copilotOpen: boolean
  view: 'operations' | 'people' | 'messages'
}>()

defineEmits<{
  (e: 'navigate', view: 'operations' | 'people' | 'messages'): void
  (e: 'toggle-copilot'): void
}>()

const store = useConsoleStore()
const { enabled, toggle } = usePresentationMode()
</script>

<template>
  <header class="top-bar">
    <div class="top-bar-left">
      <div class="brand-logo">
        <Waves :size="18" aria-hidden="true" class="waves-icon" />
      </div>
      <span class="brand-title">JalDrishti</span>
      <span v-if="store.corridorName" class="corridor-name">{{ store.corridorName }}</span>
    </div>

    <nav class="views-nav" aria-label="Views">
      <button
        type="button"
        class="nav-tab"
        :class="{ 'is-active': view === 'operations' }"
        :aria-current="view === 'operations' ? 'page' : undefined"
        @click="$emit('navigate', 'operations')"
      >
        <Activity :size="16" aria-hidden="true" />
        <span>Operations</span>
      </button>
      <button
        type="button"
        class="nav-tab"
        :class="{ 'is-active': view === 'people' }"
        :aria-current="view === 'people' ? 'page' : undefined"
        @click="$emit('navigate', 'people')"
      >
        <Users :size="16" aria-hidden="true" />
        <span>People at risk</span>
      </button>
      <button
        type="button"
        class="nav-tab"
        :class="{ 'is-active': view === 'messages' }"
        :aria-current="view === 'messages' ? 'page' : undefined"
        @click="$emit('navigate', 'messages')"
      >
        <MessageSquareText :size="16" aria-hidden="true" />
        <span>Messages</span>
      </button>
    </nav>

    <div class="top-bar-right">
      <div class="mode-switch" role="group" aria-label="Application mode">
        <button
          type="button"
          class="mode-btn"
          :class="{ 'is-active': store.mode === 'live' }"
          :aria-pressed="store.mode === 'live'"
          @click="store.goLive()"
        >
          Live
        </button>
        <button
          type="button"
          class="mode-btn"
          :class="{ 'is-active': store.mode === 'training' }"
          :aria-pressed="store.mode === 'training'"
          @click="store.enterTraining()"
        >
          Training
        </button>
      </div>

      <div class="live-indicator">
        <span class="live-dot" :data-state="connected ? 'on' : 'off'" />
        <span class="live-text">
          <template v-if="store.mode === 'live'">
            {{ connected ? 'Live' : 'Reconnecting…' }}
          </template>
          <template v-else>
            Training
          </template>
        </span>
      </div>

      <DutyPhonePopover />

      <UiButton
        variant="onBrand"
        :icon="Sparkles"
        :pressed="copilotOpen"
        @click="$emit('toggle-copilot')"
      >
        Ask Copilot
      </UiButton>

      <UiButton
        variant="onBrand"
        :icon="Presentation"
        icon-only
        label="Presentation mode"
        :pressed="enabled"
        @click="toggle"
      />
    </div>
  </header>
</template>

<style scoped>
.top-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  height: var(--topbar-h);
  padding: 0 var(--sp-6);
  background: var(--brand-bar);
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
}

.top-bar-left {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-width: 0;
}

.brand-logo {
  width: 2rem;
  height: 2rem;
  border-radius: var(--r-sm);
  background: var(--brand-mark);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.waves-icon {
  color: var(--text-on-brand);
}

.brand-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--text-on-brand);
  letter-spacing: -0.01em;
}

.corridor-name {
  font-size: var(--fs-sm);
  color: var(--text-on-brand-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 1279px) {
  .corridor-name {
    display: none;
  }
}

.views-nav {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.nav-tab {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  height: 2.25rem;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-md);
  font-weight: 500;
  color: var(--text-on-brand-2);
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background-color 120ms, color 120ms;
}

.nav-tab:hover {
  background: var(--brand-bar-hover);
  color: var(--text-on-brand);
}

.nav-tab.is-active {
  background: var(--brand-bar-hover);
  color: var(--text-on-brand);
  border-bottom: 2px solid var(--brand-mark);
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-shrink: 0;
}

.mode-switch {
  display: flex;
  align-items: center;
  background: var(--brand-bar-hover);
  border: 1px solid var(--brand-bar-line);
  border-radius: var(--r-sm);
  padding: 2px;
  gap: 2px;
}

.mode-btn {
  height: 1.875rem;
  padding: 0 var(--sp-3);
  border-radius: 4px;
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-on-brand-2);
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background-color 120ms, color 120ms;
}

.mode-btn:hover {
  color: var(--text-on-brand);
}

.mode-btn.is-active {
  background: var(--brand-bar);
  color: var(--text-on-brand);
  font-weight: 600;
}

.live-indicator {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.live-text {
  font-size: var(--fs-sm);
  color: var(--text-on-brand-2);
}
</style>
