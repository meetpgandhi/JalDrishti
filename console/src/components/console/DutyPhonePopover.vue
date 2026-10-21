<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import QRCode from 'qrcode'
import { Smartphone } from '@lucide/vue'
import UiButton from '@/components/ui/UiButton.vue'

const isOpen = ref(false)
const popoverRef = ref<HTMLElement | null>(null)
const qrDataUrl = ref('')
const copied = ref(false)
const phoneUrl = ref('')

onMounted(async () => {
  phoneUrl.value = `${window.location.origin}/phone`
  try {
    qrDataUrl.value = await QRCode.toDataURL(phoneUrl.value, { margin: 1, width: 384 })
  } catch {
    /* ignore QR generation error */
  }
})

function togglePopover() {
  isOpen.value = !isOpen.value
}

function closePopover() {
  isOpen.value = false
}

function handleDocumentClick(e: MouseEvent) {
  if (isOpen.value && popoverRef.value && !popoverRef.value.contains(e.target as Node)) {
    closePopover()
  }
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    closePopover()
  }
}

watch(isOpen, (open) => {
  if (open) {
    document.addEventListener('click', handleDocumentClick)
    document.addEventListener('keydown', handleKeyDown)
  } else {
    document.removeEventListener('click', handleDocumentClick)
    document.removeEventListener('keydown', handleKeyDown)
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleKeyDown)
})

async function copyLink() {
  try {
    await navigator.clipboard.writeText(phoneUrl.value)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    /* clipboard access failed */
  }
}
</script>

<template>
  <div ref="popoverRef" class="duty-phone-wrapper">
    <UiButton
      variant="onBrand"
      :icon="Smartphone"
      :pressed="isOpen"
      @click.stop="togglePopover"
    >
      Duty phone
    </UiButton>

    <div v-if="isOpen" class="duty-phone-popover" role="dialog" aria-label="Duty phone access">
      <h3 class="popover-title">Open the duty-holder phone</h3>
      <p class="popover-subtitle">Scan to pick a role and receive actions. Works in any phone browser.</p>

      <div class="qr-container">
        <img
          v-if="qrDataUrl"
          :src="qrDataUrl"
          alt="QR code for the duty phone page"
          class="qr-image"
        />
      </div>

      <div class="url-text mono">{{ phoneUrl }}</div>

      <div class="popover-actions">
        <UiButton
          variant="secondary"
          size="sm"
          @click="copyLink"
        >
          {{ copied ? 'Copied' : 'Copy link' }}
        </UiButton>
        <a
          :href="phoneUrl"
          target="_blank"
          rel="noopener"
          class="open-link"
        >
          Open
        </a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.duty-phone-wrapper {
  position: relative;
  display: inline-block;
}

.duty-phone-popover {
  position: absolute;
  top: calc(100% + var(--sp-2));
  right: 0;
  width: 18rem;
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-overlay);
  padding: var(--sp-4);
  z-index: 60;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  box-sizing: border-box;
}

.popover-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
  line-height: var(--lh-tight);
}

.popover-subtitle {
  font-size: var(--fs-sm);
  color: var(--text-2);
  line-height: var(--lh-normal);
}

.qr-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: var(--sp-2) 0;
}

.qr-image {
  width: 12rem;
  height: 12rem;
  border-radius: var(--r-sm);
  border: 1px solid var(--border);
}

.url-text {
  font-size: var(--fs-xs);
  color: var(--text-3);
  word-break: break-all;
  text-align: center;
  user-select: all;
}

.popover-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-2);
  gap: var(--sp-2);
}

.open-link {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--accent);
  padding: 0 var(--sp-2);
}
</style>
