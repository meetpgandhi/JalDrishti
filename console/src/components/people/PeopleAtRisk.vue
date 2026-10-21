<template>
  <div class="people">
    <header class="head">
      <div>
        <h1>People at risk</h1>
        <p class="sub">
          <span class="ctx" :data-ctx="ls.context.value">{{ contextLabel }}</span>
          How many people are in the path, how many we can warn in time, and how many lives each choice saves.
        </p>
      </div>
      <span v-if="ls.computing.value" class="busy">Calculating…</span>
    </header>

    <p class="disclaimer">
      Planning estimates from stated assumptions (A) and published loss-of-life research. They compare choices; they do not predict
      what happens to any person. Replace A values with surveyed numbers as you get them.
    </p>

    <div v-if="ls.loadError.value" class="error" role="alert">Could not load population data: {{ ls.loadError.value }}</div>

    <template v-else-if="primary">
      <!-- KPIs -->
      <section class="kpis" aria-label="Key figures">
        <div class="kpi">
          <span class="k-label">In the flood path</span>
          <span class="k-value">{{ people(primary.result.totals.peopleAtRisk.p50) }}</span>
          <span class="k-hint">{{ range(primary.result.totals.peopleAtRisk) }} people present at this hour</span>
        </div>
        <div class="kpi">
          <span class="k-label">Warned before the water arrives</span>
          <span class="k-value">{{ pct(primary.result.totals.warnedBeforeArrival) }}</span>
          <span class="k-hint">{{ pct(primary.result.totals.reachedSafety) }} reach safety · {{ primary.plan.label }}</span>
        </div>
        <div class="kpi danger">
          <span class="k-label">Expected deaths</span>
          <span class="k-value">{{ people(primary.result.totals.deaths.p50) }}</span>
          <span class="k-hint">90% range {{ range(primary.result.totals.deaths) }} · survival {{ pct(primary.result.totals.survivalRate, 1) }}</span>
        </div>
        <div class="kpi ok">
          <span class="k-label">Lives saved</span>
          <span class="k-value">{{ ls.livesSaved.value === null ? '—' : people(ls.livesSaved.value) }}</span>
          <span class="k-hint" v-if="baseline">compared with “{{ baseline.plan.label }}” ({{ people(baseline.result.totals.deaths.p50) }} deaths)</span>
        </div>
      </section>

      <div class="grid2">
        <!-- Plan comparison -->
        <section class="card">
          <h2>Warning choices compared</h2>
          <p class="card-sub">Median expected deaths, with the 90% range.</p>
          <ul class="bars">
            <li v-for="r in ls.rows.value" :key="r.plan.id" :data-role="r.role">
              <div class="bar-head">
                <span class="bar-label">{{ r.plan.label }}</span>
                <span class="bar-num">{{ people(r.result.totals.deaths.p50) }} <small>({{ range(r.result.totals.deaths) }})</small></span>
              </div>
              <div class="bar-track">
                <span class="bar-whisker" :style="whisker(r.result.totals.deaths)" />
                <span class="bar-fill" :style="{ width: barPct(r.result.totals.deaths.p50) }" />
              </div>
              <div class="bar-foot">
                {{ r.plan.decisionMin === null ? 'No warning' : `Decision ${sign(r.plan.decisionMin)} min after the wave passes Rasuwagadhi` }}
                · warned in time {{ pct(r.result.totals.warnedBeforeArrival) }} · survival {{ pct(r.result.totals.survivalRate, 1) }}
              </div>
            </li>
          </ul>
        </section>

        <!-- What-if -->
        <section class="card">
          <h2>What if…</h2>
          <p class="card-sub">Change the warning system and see the effect at once.</p>
          <label class="toggle"><input v-model="ls.whatIf.sirens" type="checkbox" /> Sirens at every riverside site</label>
          <label class="toggle"><input v-model="ls.whatIf.volunteers" type="checkbox" /> Trained volunteers going door to door</label>
          <label class="toggle"><input v-model="ls.whatIf.cellBroadcast" type="checkbox" /> Cell broadcast to every phone, foreign SIMs included</label>
          <template v-if="ls.context.value === 'planning'">
            <label class="toggle"><input v-model="ls.whatIf.upstream" type="checkbox" /> Upstream data from Tibet (warning 15 min before the wave reaches Nepal)</label>
            <label class="hour">
              <span>Time of day (Nepal): <strong class="mono">{{ String(ls.hourNpt.value).padStart(2, '0') }}:00</strong></span>
              <input v-model.number="ls.hourNpt.value" type="range" min="0" max="23" step="1" />
              <small>Who is present changes: schools 10:00–16:00, work shifts 07:00–19:00, travellers by day.</small>
            </label>
          </template>
        </section>
      </div>

      <!-- Every minute counts -->
      <section class="card">
        <h2>Every minute counts</h2>
        <p class="card-sub">
          Expected deaths if the same warning went out earlier or later. The shaded band is the 90% range.
          <template v-if="minuteCost !== null"> Around the marked decision, each 5-minute delay costs about <strong>{{ people(minuteCost) }}</strong> lives.</template>
        </p>
        <DelayCurveChart :points="ls.curve.value" :markers="ls.markers.value" />
      </section>

      <!-- Sites -->
      <section class="card flush">
        <div class="card-pad">
          <h2>Site by site — {{ primary.plan.label }}</h2>
          <p class="card-sub">Ordered from the border downstream. Arrival is when the water can reach the site.</p>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Site</th><th>Arrival</th><th>Can we warn in time?</th><th class="num">People</th>
                <th class="num">Warned in time</th><th class="num">Reach safety</th><th>Expected deaths</th><th class="num">Survival</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in primary.result.sites" :key="s.id">
                <td>
                  <div class="site">{{ s.name }}</div>
                  <div class="meta">km {{ s.riverKm }} · {{ severityLabel[s.severity] }}</div>
                </td>
                <td class="mono nw">{{ arrivalText(s.arrivalMin) }}</td>
                <td>
                  <span class="verdict" :data-v="verdict(s).tone" :title="verdict(s).help">{{ verdict(s).label }}</span>
                  <div class="meta" v-if="s.warningMarginMin !== null">{{ s.warningMarginMin }} min before the earliest arrival</div>
                </td>
                <td class="num" :title="groupSources(s.id)">{{ people(s.peopleAtRisk.p50) }}<div class="meta">{{ range(s.peopleAtRisk) }}</div></td>
                <td class="num">{{ pct(s.warnedBeforeArrival) }}</td>
                <td class="num">{{ pct(s.reachedSafety) }}</td>
                <td>
                  <div class="mini-track"><span class="mini-whisker" :style="whisker(s.deaths, siteMax)" /><span class="mini-fill" :style="{ width: barPct(s.deaths.p50, siteMax) }" /></div>
                  <div class="meta">{{ people(s.deaths.p50) }} ({{ range(s.deaths) }})</div>
                </td>
                <td class="num">{{ pct(s.survivalRate, 1) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Assumptions -->
      <details class="card assumptions">
        <summary><h2>Method, assumptions and sources</h2></summary>
        <p class="card-sub">
          Monte Carlo over {{ ls.params.draws }} draws (fixed seed). For each site: the warning reaches people channel by channel,
          some act and walk to safety; anyone still in the path when the water arrives dies at Graham's (1999) rate for floods with no warning.
          Sites are treated as independent.
        </p>
        <h3>Warning channels (A)</h3>
        <table class="small">
          <thead><tr><th>Channel</th><th class="num">Reaches residents</th><th class="num">Typical delay</th><th>Why</th></tr></thead>
          <tbody>
            <tr v-for="(c, id) in ls.params.channels" :key="id">
              <td>{{ c.label }}</td>
              <td class="num">{{ pct(c.reach.residents[1]) }}</td>
              <td class="num">{{ c.delayMin[1] }} min</td>
              <td class="note">{{ c.note }}</td>
            </tr>
          </tbody>
        </table>
        <h3>Behaviour (A)</h3>
        <p class="note">
          Site-specific message: {{ pct(ls.params.compliance.precise[1]) }} act, starting after about {{ ls.params.responseMin.precise[1] }} min.
          Generic message (as on 26 Aug): {{ pct(ls.params.compliance.vague[1]) }} act, after about {{ ls.params.responseMin.vague[1] }} min.
        </p>
        <h3>Death rate for people still in the path (P — Graham 1999, no warning)</h3>
        <p class="note">
          High severity (area swept clean) {{ pct(ls.params.fatality.high[1]) }} ({{ pct(ls.params.fatality.high[0]) }}–{{ pct(ls.params.fatality.high[2]) }});
          medium (homes destroyed) {{ pct(ls.params.fatality.medium[1]) }} ({{ pct(ls.params.fatality.medium[0]) }}–{{ pct(ls.params.fatality.medium[2]) }});
          low {{ pct(ls.params.fatality.low[1], 1) }} ({{ pct(ls.params.fatality.low[0]) }}–{{ pct(ls.params.fatality.low[2]) }}).
        </p>
        <h3>Population data</h3>
        <p class="note">{{ provenance.P }} groups published (P), {{ provenance.D }} derived (D), {{ provenance.A }} assumed (A). Hover a site's people count to see where each number comes from. The numbers live in <code>public/data/exposure.json</code>.</p>
        <h3>Sources</h3>
        <ul class="sources">
          <li v-for="src in ls.data.value?.sources ?? []" :key="src.id"><a :href="src.url" target="_blank" rel="noopener">{{ src.title }}</a></li>
        </ul>
      </details>
    </template>

    <div v-else class="loading">
      <span class="skeleton" style="height: 6rem" />
      <span class="skeleton" style="height: 16rem" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import DelayCurveChart from '@/components/people/DelayCurveChart.vue'
import { useLifeSafety } from '@/composables/useLifeSafety'
import { useConsoleStore } from '@/stores/console'
import type { Quantiles, Severity, SiteResult } from '@/lib/lifeSafety'
import { formatNpt } from '@/lib/time'

const ls = useLifeSafety()
onMounted(() => { void ls.load() })

const store = useConsoleStore()
const contextLabel = computed(() =>
  ls.context.value === 'incident'
    ? (store.mode === 'live' ? 'This incident · live' : 'This incident · training replay')
    : 'Planning · an outburst like 26 Aug',
)
const primary = computed(() => ls.primary.value)
const baseline = computed(() => ls.baseline.value)

const nf = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })
/** People are rounded so the numbers never look more precise than they are. */
function people(n: number): string {
  const r = n < 20 ? Math.round(n) : n < 200 ? Math.round(n / 5) * 5 : Math.round(n / 10) * 10
  return nf.format(r)
}
function range(q: Quantiles): string {
  return `${people(q.p05)}–${people(q.p95)}`
}
function pct(f: number, digits = 0): string {
  return `${(f * 100).toFixed(digits)}%`
}
function sign(n: number): string {
  return n > 0 ? `+${n}` : String(n)
}

const allMax = computed(() => Math.max(1, ...ls.rows.value.map(r => r.result.totals.deaths.p95)))
const siteMax = computed(() => Math.max(1, ...(primary.value?.result.sites.map(s => s.deaths.p95) ?? [1])))
function barPct(v: number, max = allMax.value): string {
  return `${Math.min(100, (v / max) * 100)}%`
}
function whisker(q: Quantiles, max = allMax.value) {
  return { left: `${(q.p05 / max) * 100}%`, width: `${Math.max(0.5, ((q.p95 - q.p05) / max) * 100)}%` }
}

const severityLabel: Record<Severity, string> = { high: 'high severity', medium: 'medium severity', low: 'low severity' }

function arrivalText([e, l]: [number, number]): string {
  if (ls.context.value === 'incident' && ls.frontMs.value !== null) {
    const f = ls.frontMs.value
    return `${formatNpt(new Date(f + e * 60000).toISOString())}–${formatNpt(new Date(f + l * 60000).toISOString())}`
  }
  return `+${e}–${l} min`
}

function verdict(s: SiteResult): { label: string; tone: string; help: string } {
  if (s.warningMarginMin === null) return { label: 'No warning', tone: 'neutral', help: 'No warning in this choice.' }
  if (s.beyondReach) return { label: 'Beyond reach', tone: 'danger', help: 'The water can arrive before people can get out, even if they act at once. Only earlier detection upstream helps here.' }
  if (s.warningMarginMin < 30) return { label: 'Tight', tone: 'watch', help: 'Less than 30 minutes. Sirens and site-specific orders matter most here.' }
  return { label: 'Time to act', tone: 'ok', help: 'Enough time if the warning reaches people and they move.' }
}

const minuteCost = computed(() => {
  const pts = ls.curve.value
  const mark = ls.markers.value.find(m => m.label === 'Warning issued' || m.label === 'Now' || m.label === 'JalDrishti')
  if (pts.length < 2 || !mark) return null
  const i = pts.findIndex(p => p.decisionMin >= mark.decisionMin)
  if (i < 0 || i >= pts.length - 1) return null
  const a = pts[i]
  const b = pts[i + 1]
  return Math.max(0, ((b.deaths.p50 - a.deaths.p50) / (b.decisionMin - a.decisionMin)) * 5)
})

function groupSources(siteId: string): string {
  const site = ls.data.value?.sites.find(s => s.id === siteId)
  return (site?.groups ?? []).map(g => `${g.kind}: ${g.low}–${g.high} (${g.tag}) — ${g.source}`).join('\n')
}

const provenance = computed(() => {
  const c = { P: 0, D: 0, A: 0 }
  for (const s of ls.data.value?.sites ?? []) for (const g of s.groups) c[g.tag]++
  return c
})
</script>

<style scoped>
.people { display: grid; gap: var(--sp-4); }
.head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--sp-4); }
.head h1 { font-size: var(--fs-xl); }
.sub { color: var(--text-2); margin-top: var(--sp-1); }
.ctx {
  display: inline-block; margin-right: var(--sp-2); padding: 0 var(--sp-2); border-radius: var(--r-pill); font-size: var(--fs-xs);
  font-weight: 600; border: 1px solid var(--info-line); background: var(--info-soft); color: var(--info);
}
.ctx[data-ctx='incident'] { border-color: var(--danger-line); background: var(--danger-soft); color: var(--danger); }
.busy { font-size: var(--fs-sm); color: var(--text-3); }
.disclaimer { font-size: var(--fs-sm); color: var(--text-2); background: var(--surface-1); border: 1px solid var(--border); border-left: 4px solid var(--watch-solid); border-radius: var(--r-sm); padding: var(--sp-2) var(--sp-3); }
.error { color: var(--danger); background: var(--danger-soft); border: 1px solid var(--danger-line); border-radius: var(--r-sm); padding: var(--sp-3); }

.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--sp-4); }
.kpi { background: var(--surface-1); border: 1px solid var(--border); border-radius: var(--r-md); box-shadow: var(--shadow-card); padding: var(--sp-4); display: grid; gap: 2px; border-top: 4px solid var(--info); }
.kpi.danger { border-top-color: var(--danger-solid); }
.kpi.ok { border-top-color: var(--ok); }
.k-label { font-size: var(--fs-xs); font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-3); }
.k-value { font-size: var(--fs-2xl); font-weight: 600; font-variant-numeric: tabular-nums; line-height: var(--lh-tight); }
.kpi.danger .k-value { color: var(--danger); }
.kpi.ok .k-value { color: var(--ok); }
.k-hint { font-size: var(--fs-sm); color: var(--text-2); }

.grid2 { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: var(--sp-4); }
.card { background: var(--surface-1); border: 1px solid var(--border); border-radius: var(--r-md); box-shadow: var(--shadow-card); padding: var(--sp-4); min-width: 0; }
.card.flush { padding: 0; }
.card-pad { padding: var(--sp-4) var(--sp-4) var(--sp-2); }
.card h2 { font-size: var(--fs-lg); font-weight: 600; }
.card h3 { font-size: var(--fs-xs); font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-3); margin: var(--sp-4) 0 var(--sp-2); }
.card-sub { font-size: var(--fs-sm); color: var(--text-2); margin: 2px 0 var(--sp-3); }

.bars { list-style: none; display: grid; gap: var(--sp-4); }
.bar-head { display: flex; justify-content: space-between; gap: var(--sp-3); font-size: var(--fs-md); }
.bar-label { font-weight: 600; }
.bar-num { font-variant-numeric: tabular-nums; font-weight: 600; }
.bar-num small { font-weight: 400; color: var(--text-3); font-size: var(--fs-xs); }
.bar-track, .mini-track { position: relative; height: 12px; border-radius: var(--r-pill); background: var(--surface-3); margin: var(--sp-1) 0; overflow: hidden; }
.mini-track { height: 8px; min-width: 7rem; }
.bar-fill, .mini-fill { position: absolute; left: 0; top: 0; bottom: 0; border-radius: var(--r-pill); background: var(--danger-solid); }
.bar-whisker, .mini-whisker { position: absolute; top: 0; bottom: 0; background: var(--danger-line); border-radius: var(--r-pill); }
li[data-role='primary'] .bar-fill { background: var(--accent-solid); }
li[data-role='primary'] .bar-whisker { background: var(--info-line); }
li[data-role='other'] .bar-fill { background: var(--text-3); }
li[data-role='other'] .bar-whisker { background: var(--border-strong); }
.bar-foot { font-size: var(--fs-xs); color: var(--text-3); }

.toggle { display: flex; gap: var(--sp-2); align-items: flex-start; font-size: var(--fs-md); color: var(--text); padding: var(--sp-2) 0; border-bottom: 1px solid var(--border); cursor: pointer; }
.toggle input { margin-top: 3px; accent-color: var(--accent-solid); }
.hour { display: grid; gap: var(--sp-1); padding-top: var(--sp-3); font-size: var(--fs-md); color: var(--text); }
.hour input { accent-color: var(--accent-solid); }
.hour small { color: var(--text-3); font-size: var(--fs-xs); }

.table-wrap { overflow-x: auto; }
table { min-width: 860px; }
th.num, td.num { text-align: right; font-variant-numeric: tabular-nums; }
.nw { white-space: nowrap; }
.site { font-weight: 600; }
.meta { font-size: var(--fs-xs); color: var(--text-3); }
.verdict { display: inline-block; padding: 0 var(--sp-2); border-radius: var(--r-pill); font-size: var(--fs-xs); font-weight: 600; border: 1px solid var(--border); background: var(--surface-3); color: var(--text-2); cursor: help; }
.verdict[data-v='danger'] { background: var(--danger-soft); color: var(--danger); border-color: var(--danger-line); }
.verdict[data-v='watch'] { background: var(--watch-soft); color: var(--watch); border-color: var(--watch-line); }
.verdict[data-v='ok'] { background: var(--ok-soft); color: var(--ok); border-color: var(--ok-line); }

.assumptions summary { cursor: pointer; list-style: revert; }
.assumptions summary h2 { display: inline; }
table.small { min-width: 0; }
.note { font-size: var(--fs-sm); color: var(--text-2); }
.sources { font-size: var(--fs-sm); padding-left: var(--sp-5); display: grid; gap: 2px; }
.loading { display: grid; gap: var(--sp-4); }
.loading .skeleton { display: block; }

@media (max-width: 1023px) {
  .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .grid2 { grid-template-columns: minmax(0, 1fr); }
}
</style>
