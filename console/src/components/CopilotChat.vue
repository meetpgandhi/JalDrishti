<template>
  <div v-if="isOpen" class="copilot-drawer card">
    <div class="copilot-header">
      <div class="header-info">
        <div class="title-with-badge">
          <span class="bot-icon">🤖</span>
          <span class="title">JalDrishti Copilot</span>
          <span class="badge badge-d">[D] Grounded</span>
        </div>
        <div class="model-meta text-mono">
          Gemini 3.6 Flash · Asia-South1 · Grounded on Live Telemetry
        </div>
      </div>
      <button class="btn-close" @click="close" title="Close Copilot">✕</button>
    </div>

    <!-- Suggested Questions Chips -->
    <div class="quick-chips">
      <button
        v-for="(chip, idx) in suggestedChips"
        :key="idx"
        class="chip text-mono"
        :disabled="isLoading"
        @click="sendPrompt(chip)"
      >
        {{ chip }}
      </button>
    </div>

    <!-- Message History -->
    <div class="messages-container" ref="messagesRef">
      <div v-if="messages.length === 0" class="welcome-box text-mono">
        <p class="welcome-title">💡 Real-time Hydrological Copilot Ready</p>
        <p class="welcome-sub">
          Ask questions about silent gauges, Rule R5 corroboration, arrival band projections at downstream bridges, or duty holder acknowledgement status.
        </p>
      </div>

      <div
        v-for="(m, i) in messages"
        :key="i"
        class="message-bubble"
        :class="m.role === 'user' ? 'msg-user' : 'msg-bot'"
      >
        <div class="msg-header text-mono">
          <span class="sender-name">{{ m.role === 'user' ? 'Forecaster' : 'JalDrishti Copilot' }}</span>
          <span class="time">{{ m.time }}</span>
        </div>
        <div class="msg-text">
          {{ m.text }}
        </div>
        <div v-if="m.sources && m.sources.length > 0" class="msg-sources text-mono">
          <span class="source-label">Sources:</span>
          <span v-for="(s, sIdx) in m.sources" :key="sIdx" class="source-tag">
            {{ s }}
          </span>
        </div>
      </div>

      <div v-if="isLoading" class="loading-bubble text-mono">
        <span class="spinner"></span> Thinking and analyzing corridor telemetry...
      </div>
    </div>

    <!-- Input Box -->
    <div class="input-bar">
      <input
        v-model="inputQuery"
        type="text"
        placeholder="Ask about silent gauges, arrival windows, duty acks..."
        class="query-input"
        :disabled="isLoading"
        @keyup.enter="handleSend"
      />
      <button class="btn-send" :disabled="isLoading || !inputQuery.trim()" @click="handleSend">
        Send
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

const props = defineProps<{
  isOpen: boolean
  runId?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

interface Message {
  role: 'user' | 'assistant'
  text: string
  time: string
  sources?: string[]
}

const suggestedChips = [
  'Which gauges are silent and why did R5 fire?',
  'When will the flood wave reach Betrawati & Bidur?',
  'Is this a non-rain flood (Rule R6)?',
  'Who are the pending duty holders?'
]

const messages = ref<Message[]>([])
const inputQuery = ref('')
const isLoading = ref(false)
const messagesRef = ref<HTMLElement | null>(null)

function close() {
  emit('close')
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

function sendPrompt(prompt: string) {
  inputQuery.value = prompt
  handleSend()
}

async function handleSend() {
  const q = inputQuery.value.trim()
  if (!q || isLoading.value) return

  const now = new Date()
  const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })

  messages.value.push({
    role: 'user',
    text: q,
    time: timeStr
  })

  inputQuery.value = ''
  isLoading.value = true
  scrollToBottom()

  try {
    const res = await fetch('/api/v1/assistant/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        question: q,
        runId: props.runId || 'live'
      })
    })

    if (res.ok) {
      const data = await res.json()
      messages.value.push({
        role: 'assistant',
        text: data.answer || 'No response returned.',
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        sources: data.sources || ['Corridor Telemetry', 'Rule Engine R1-R11']
      })
    } else {
      messages.value.push({
        role: 'assistant',
        text: 'Assistant service temporarily unavailable. Telemetry streams remain normal.',
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      })
    }
  } catch (err) {
    messages.value.push({
      role: 'assistant',
      text: 'Error connecting to JalDrishti Copilot service. Please verify network or backend status.',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    })
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

watch(() => props.isOpen, (open) => {
  if (open) {
    scrollToBottom()
  }
})
</script>

<style scoped>
.copilot-drawer {
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 440px;
  height: 580px;
  max-width: calc(100vw - 48px);
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: #0d1117;
  border: 1px solid #38bdf8;
  border-radius: var(--radius-lg);
  box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.7), 0 0 20px rgba(56, 189, 248, 0.25);
  z-index: 999;
  overflow: hidden;
}

.copilot-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba(22, 27, 34, 0.95);
  border-bottom: 1px solid var(--color-border);
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bot-icon {
  font-size: 16px;
}

.title {
  font-weight: 700;
  font-size: 14px;
  color: #f0f6fc;
}

.model-meta {
  font-size: 10px;
  color: var(--color-text-muted);
}

.btn-close {
  background: transparent;
  border: none;
  color: var(--color-text-muted);
  font-size: 16px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.btn-close:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 14px;
  background: rgba(13, 17, 23, 0.6);
  border-bottom: 1px solid rgba(48, 54, 61, 0.5);
}

.chip {
  background: rgba(56, 189, 248, 0.1);
  color: #7dd3fc;
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 12px;
  padding: 3px 8px;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
}

.chip:hover:not(:disabled) {
  background: rgba(56, 189, 248, 0.25);
  border-color: #38bdf8;
  color: #fff;
}

.messages-container {
  flex: 1;
  padding: 14px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.welcome-box {
  background: rgba(22, 27, 34, 0.6);
  border: 1px dashed rgba(56, 189, 248, 0.3);
  border-radius: 8px;
  padding: 12px;
  font-size: 11px;
}

.welcome-title {
  color: #38bdf8;
  font-weight: 600;
  margin-bottom: 4px;
}

.welcome-sub {
  color: var(--color-text-muted);
  line-height: 1.4;
}

.message-bubble {
  max-width: 90%;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.45;
}

.msg-user {
  align-self: flex-end;
  background: #1f6feb;
  color: #ffffff;
  border-bottom-right-radius: 2px;
}

.msg-bot {
  align-self: flex-start;
  background: #161b22;
  border: 1px solid var(--color-border);
  color: #c9d1d9;
  border-bottom-left-radius: 2px;
}

.msg-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 10px;
  margin-bottom: 4px;
  opacity: 0.8;
}

.sender-name {
  font-weight: 600;
}

.time {
  font-size: 9px;
}

.msg-text {
  word-break: break-word;
}

.msg-sources {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  font-size: 9px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.source-label {
  color: #8b949e;
}

.source-tag {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  padding: 1px 4px;
  border-radius: 3px;
}

.loading-bubble {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #8b949e;
  padding: 8px 12px;
  background: #161b22;
  border-radius: 8px;
  align-self: flex-start;
}

.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(56, 189, 248, 0.3);
  border-top-color: #38bdf8;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.input-bar {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  background: rgba(22, 27, 34, 0.95);
  border-top: 1px solid var(--color-border);
}

.query-input {
  flex: 1;
  background: #0d1117;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 8px 12px;
  color: #f0f6fc;
  font-size: 12px;
  outline: none;
  transition: border-color 0.15s ease;
}

.query-input:focus {
  border-color: #38bdf8;
}

.btn-send {
  background: #238636;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-send:hover:not(:disabled) {
  background: #2ea043;
}

.btn-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
