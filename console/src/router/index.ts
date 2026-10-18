import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'console',
      // Operator console: station strip, incident panel, ack board
      component: () => import('@/views/ConsoleView.vue'),
    },
    {
      path: '/phone',
      name: 'phone-simulator',
      // Duty-holder phone inbox (scanned via QR code by judges)
      component: () => import('@/views/PhoneSimulatorView.vue'),
    },
    {
      path: '/phone/:recipientId',
      name: 'phone-recipient',
      component: () => import('@/views/PhoneSimulatorView.vue'),
      props: true,
    },
    {
      path: '/ack/:dispatchId',
      name: 'ack',
      // Signed ack link (expires 24h per spec Section 9)
      component: () => import('@/views/AckView.vue'),
      props: true,
    },
  ],
})

export default router
