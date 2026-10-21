import { ref, watch } from 'vue'

// Presentation mode = 18px root font for projectors (all sizes are rem). Remembered per browser.
const KEY = 'jaldrishti.presentation'

function readSaved(): boolean {
  try {
    return window.localStorage.getItem(KEY) === 'on'
  } catch {
    return false
  }
}

const enabled = ref<boolean>(readSaved())

function apply(on: boolean) {
  document.documentElement.dataset.presentation = on ? 'on' : 'off'
  try {
    window.localStorage.setItem(KEY, on ? 'on' : 'off')
  } catch {
    /* storage can be blocked; the toggle still works for this page */
  }
}

apply(enabled.value)
watch(enabled, apply)

export function usePresentationMode() {
  return {
    enabled,
    toggle: () => { enabled.value = !enabled.value },
  }
}
