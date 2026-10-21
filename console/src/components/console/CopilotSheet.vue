<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { Send } from '@lucide/vue'
import { useConsoleStore } from '@/stores/console'
import type { AskResponse } from '@/types/api'
import UiSheet from '@/components/ui/UiSheet.vue'
import UiEmptyState from '@/components/ui/UiEmptyState.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiBadge from '@/components/ui/UiBadge.vue'
import UiProvenance from '@/components/ui/UiProvenance.vue'

defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const store = useConsoleStore()

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  sources?: string[] | null
  tag?: AskResponse['tag']
  isError?: boolean
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isWaiting = ref(false)
const chatBodyRef = ref<HTMLElement | null>(null)

const suggestedQuestions = [
  'Which gauges are silent, and why did the warning fire?',
  'When will the wave reach Betrawati?',
  'Is this a non-rain flood?',
  'Which duty-holders have not confirmed?',
]

function scrollToBottom() {
  void nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

async function sendQuestion(q: string) {
  const query = q.trim()
  if (!query || isWaiting.value) return

  messages.value.push({
    id: `msg-${Date.now()}-u`,
    role: 'user',
    text: query,
  })
  inputText.value = ''
  isWaiting.value = true
  scrollToBottom()

  try {
    const res = await store.ask(query)
    messages.value.push({
      id: `msg-${Date.now()}-a`,
      role: 'assistant',
      text: res.answer,
      sources: res.sources,
      tag: res.tag,
    })
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    messages.value.push({
      id: `msg-${Date.now()}-e`,
      role: 'assistant',
      text: `Copilot is unavailable: ${msg}`,
      isError: true,
    })
  } finally {
    isWaiting.value = false
    scrollToBottom()
  }
}
</script>

<template>
  <UiSheet
    :open="open"
    :modal="false"
    width="26rem"
    title="Ask Copilot"
    subtitle="Gemini answers from read-only incident data. It never changes alert levels or sends messages."
    @close="emit('close')"
  >
    <UiEmptyState
      v-if="!store.runId"
      title="Start a run first"
      body="The copilot answers questions about the current active simulation run."
    />

    <div v-else class="copilot-container">
      <!-- Suggested questions -->
      <div class="suggested-row">
        <UiButton
          v-for="q in suggestedQuestions"
          :key="q"
          variant="secondary"
          size="sm"
          :disabled="isWaiting"
          @click="sendQuestion(q)"
        >
          {{ q }}
        </UiButton>
      </div>

      <!-- Messages list -->
      <div ref="chatBodyRef" class="chat-messages">
        <div
          v-for="m in messages"
          :key="m.id"
          class="message-wrapper"
          :class="`role-${m.role}`"
        >
          <span class="message-sender">{{ m.role === 'user' ? 'You' : 'Copilot' }}</span>
          <div
            class="message-bubble"
            :class="{ 'is-error': m.isError }"
          >
            <p class="message-text">{{ m.text }}</p>

            <!-- Sources & Tag -->
            <div
              v-if="m.sources?.length || m.tag"
              class="message-sources"
            >
              <div v-if="m.sources?.length" class="sources-list">
                <span class="sources-label">Sources:</span>
                <UiBadge
                  v-for="src in m.sources"
                  :key="src"
                  tone="neutral"
                >
                  {{ src }}
                </UiBadge>
              </div>
              <UiProvenance v-if="m.tag" :tag="m.tag" />
            </div>
          </div>
        </div>

        <!-- Thinking indicator -->
        <div v-if="isWaiting" class="message-wrapper role-assistant">
          <span class="message-sender">Copilot</span>
          <div class="message-bubble thinking-bubble">
            <span class="thinking-text">Thinking…</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <form class="chat-input-form" @submit.prevent="sendQuestion(inputText)">
        <input
          v-model="inputText"
          type="text"
          placeholder="Ask a question about this run…"
          :disabled="!store.run || isWaiting"
        />
        <UiButton
          variant="primary"
          :icon="Send"
          icon-only
          label="Send"
          :disabled="!store.run || !inputText.trim() || isWaiting"
          type="submit"
        />
      </form>
    </template>
  </UiSheet>
</template>

<style scoped>
.copilot-container {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  height: 100%;
}

.suggested-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  overflow-y: auto;
  flex: 1;
  padding-bottom: var(--sp-2);
}

.message-wrapper {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.role-user {
  align-items: flex-end;
}

.role-assistant {
  align-items: flex-start;
}

.message-sender {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.message-bubble {
  border-radius: var(--r-md);
  padding: var(--sp-2) var(--sp-3);
  line-height: var(--lh-normal);
  font-size: var(--fs-md);
  box-sizing: border-box;
}

.role-user .message-bubble {
  background: var(--accent-soft);
  color: var(--text);
  max-width: 85%;
}

.role-assistant .message-bubble {
  background: var(--surface-2);
  color: var(--text);
  border-left: 3px solid var(--ai);
  max-width: 95%;
}

.role-assistant .message-bubble.is-error {
  border-left-color: var(--danger);
  background: var(--danger-soft);
}

.message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-sources {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  padding-top: var(--sp-1);
  border-top: 1px solid var(--border);
  flex-wrap: wrap;
}

.sources-list {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.sources-label {
  font-size: var(--fs-xs);
  color: var(--text-3);
}

.thinking-bubble {
  color: var(--text-3);
  font-style: italic;
}

.chat-input-form {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
}
</style>
