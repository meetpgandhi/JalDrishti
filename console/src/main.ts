import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/tokens.css'
import './assets/base.css'
import { usePresentationMode } from './composables/usePresentationMode'

usePresentationMode() // applies the saved presentation setting before the first render

createApp(App).use(createPinia()).use(router).mount('#app')
