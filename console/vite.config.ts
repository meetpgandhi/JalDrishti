import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

const backendTarget = process.env.VITE_BACKEND_URL || 'https://jaldrishti-core-282398392020.asia-south1.run.app'

/**
 * Dev-only bridge for endpoints the backend doesn't expose yet.
 *
 * `/api/v1/messages` and `/api/v1/cap-messages` (see console/src/types/api.ts) have no
 * server-side implementation — no controller in jd-adapter-rest/jd-adapter-channels maps
 * them, and the `message`/`cap_message` tables don't carry a run_id or recipient column
 * that would let a real endpoint answer `?runId=` the way the console expects. Until that
 * backend work lands, reconstruct a best-effort message log here from data the real API
 * does provide, purely so the Messages tab has something to show in local dev. This does
 * NOT run against the deployed app — Cloud Run still returns 404/empty for these paths.
 */
function devSimulationBridgePlugin() {
  return {
    name: 'dev-simulation-bridge',
    configureServer(server: any) {
      server.middlewares.use(async (req: any, res: any, next: any) => {
        if (!req.url) return next()

        if (req.url.startsWith('/api/v1/messages')) {
          try {
            const parsedUrl = new URL(req.url, 'http://localhost')
            const runId = parsedUrl.searchParams.get('runId') || 'live'
            const stateRes = await fetch(`${backendTarget}/api/v1/corridors/bhote-trishuli-narayani/state?runId=${encodeURIComponent(runId)}`)
            if (stateRes.ok) {
              const state = await stateRes.json()
              if (state.openIncident?.id) {
                const [actionsRes, holdersRes] = await Promise.all([
                  fetch(`${backendTarget}/api/v1/incidents/${state.openIncident.id}/actions`),
                  fetch(`${backendTarget}/api/v1/phone/duty-holders`),
                ])
                if (actionsRes.ok && holdersRes.ok) {
                  const actions = await actionsRes.json()
                  const holders = await holdersRes.json()
                  const holderMap = new Map(holders.map((h: any) => [h.id, h]))

                  const messages = actions.map((a: any) => {
                    const h: any = holderMap.get(a.dutyHolderId) || {}
                    return {
                      messageId: a.id,
                      actionId: a.id,
                      incidentId: a.incidentId,
                      sentAt: a.sentAt,
                      kind: 'ACTION',
                      channel: 'WEB_PHONE_SIM',
                      language: h.language || 'ne',
                      text: `[${a.actionCode}] Urgent action order for ${h.displayName || a.dutyHolderId}: ${a.actionCode}`,
                      recipientId: a.dutyHolderId,
                      recipientName: h.displayName || null,
                      recipientActorType: h.actorType || null,
                      recipientSiteId: h.siteId || null,
                      principalId: null,
                      principalName: null,
                      actionCode: a.actionCode,
                      deadlineAt: a.deadlineAt,
                      actionState: a.state,
                      escalationStep: a.escalationStep || 0,
                      ackedAt: a.ackedAt,
                    }
                  })
                  res.setHeader('Content-Type', 'application/json')
                  return res.end(JSON.stringify(messages))
                }
              }
            }
          } catch {
            // Fall through to empty array
          }

          res.setHeader('Content-Type', 'application/json')
          return res.end('[]')
        }

        if (req.url.startsWith('/api/v1/cap-messages')) {
          // No backend endpoint exists for this either; nothing to reconstruct it from
          // client-visible data, so just report an empty log instead of a 404.
          res.setHeader('Content-Type', 'application/json')
          return res.end('[]')
        }

        next()
      })
    },
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), devSimulationBridgePlugin()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      // Proxy API calls to the Spring Boot backend (local :8080 or deployed Cloud Run)
      '/api': {
        target: backendTarget,
        changeOrigin: true,
        secure: false,
      },
      '/internal': {
        target: backendTarget,
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    // Output to jd-boot's static resources so it can be served by Spring Boot
    outDir: '../jd-boot/src/main/resources/static',
    emptyOutDir: true,
  },
})
