<template>
  <div ref="box" class="box">
  <svg :viewBox="`0 0 ${W} ${H}`" :width="W" :height="H" class="chart" role="img" :aria-label="summary">
    <title>{{ summary }}</title>
    <!-- grid + y axis -->
    <g v-for="t in yTicks" :key="'y' + t">
      <line :x1="PAD.l" :x2="W - PAD.r" :y1="y(t)" :y2="y(t)" class="grid" />
      <text :x="PAD.l - 8" :y="y(t) + 4" class="tick" text-anchor="end">{{ fmt(t) }}</text>
    </g>
    <!-- x axis -->
    <g v-for="t in xTicks" :key="'x' + t">
      <text :x="x(t)" :y="H - PAD.b + 18" class="tick" text-anchor="middle">{{ t > 0 ? '+' + t : t }}</text>
    </g>
    <text :x="(PAD.l + W - PAD.r) / 2" :y="H - 6" class="axis-label" text-anchor="middle">
      Minutes after the wave passes Rasuwagadhi when the warning goes out
    </text>
    <text :x="14" :y="(PAD.t + H - PAD.b) / 2" class="axis-label" text-anchor="middle"
      :transform="`rotate(-90 14 ${(PAD.t + H - PAD.b) / 2})`">Expected deaths</text>

    <!-- uncertainty band (5–95 %) and median -->
    <path v-if="points.length > 1" :d="bandPath" class="band" />
    <path v-if="points.length > 1" :d="linePath" class="line" />
    <circle v-for="p in points" :key="'p' + p.decisionMin" :cx="x(p.decisionMin)" :cy="y(p.deaths.p50)" r="3" class="dot" />

    <!-- markers -->
    <g v-for="(m, i) in visibleMarkers" :key="'m' + m.label">
      <line :x1="x(m.decisionMin)" :x2="x(m.decisionMin)" :y1="PAD.t" :y2="H - PAD.b" class="marker" />
      <text :x="x(m.decisionMin) + 4" :y="PAD.t + 12 + i * 14" class="marker-label">{{ m.label }}</text>
    </g>
  </svg>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { CurveMarker, CurvePoint } from '@/composables/useLifeSafety'

const props = defineProps<{ points: CurvePoint[]; markers: CurveMarker[] }>()

// The SVG is drawn at the container's real width so text stays at token sizes.
const box = ref<HTMLDivElement | null>(null)
const width = ref(720)
let observer: ResizeObserver | null = null
onMounted(() => {
  observer = new ResizeObserver(entries => { width.value = Math.max(320, Math.floor(entries[0].contentRect.width)) })
  if (box.value) observer.observe(box.value)
})
onUnmounted(() => observer?.disconnect())
const W = computed(() => width.value)
const H = 300
const PAD = { l: 64, r: 16, t: 16, b: 48 }
const nf = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })
const fmt = (n: number) => nf.format(n)

const xMin = computed(() => Math.min(...props.points.map(p => p.decisionMin), 0))
const xMax = computed(() => Math.max(...props.points.map(p => p.decisionMin), 1))
const yMax = computed(() => {
  const m = Math.max(1, ...props.points.map(p => p.deaths.p95))
  const mag = 10 ** Math.floor(Math.log10(m))
  return Math.ceil(m / mag) * mag
})
const x = (v: number) => PAD.l + ((v - xMin.value) / (xMax.value - xMin.value || 1)) * (W.value - PAD.l - PAD.r)
const y = (v: number) => H - PAD.b - (v / yMax.value) * (H - PAD.t - PAD.b)

const yTicks = computed(() => [0, 0.25, 0.5, 0.75, 1].map(f => Math.round(f * yMax.value)))
const xTicks = computed(() => {
  const span = xMax.value - xMin.value
  const step = span > 90 ? 20 : span > 45 ? 10 : 5
  const out: number[] = []
  for (let t = Math.ceil(xMin.value / step) * step; t <= xMax.value; t += step) out.push(t)
  return out
})
const linePath = computed(() => props.points.map((p, i) => `${i ? 'L' : 'M'}${x(p.decisionMin)},${y(p.deaths.p50)}`).join(' '))
const bandPath = computed(() => {
  const up = props.points.map((p, i) => `${i ? 'L' : 'M'}${x(p.decisionMin)},${y(p.deaths.p95)}`).join(' ')
  const down = [...props.points].reverse().map(p => `L${x(p.decisionMin)},${y(p.deaths.p05)}`).join(' ')
  return `${up} ${down} Z`
})
const visibleMarkers = computed(() => props.markers.filter(m => m.decisionMin >= xMin.value && m.decisionMin <= xMax.value))
const summary = computed(() => {
  if (props.points.length < 2) return 'No data yet'
  const first = props.points[0]
  const last = props.points[props.points.length - 1]
  return `Expected deaths rise from about ${fmt(first.deaths.p50)} with a warning at ${first.decisionMin} minutes to about ${fmt(last.deaths.p50)} at ${last.decisionMin} minutes.`
})
</script>

<style scoped>
.box { width: 100%; }
.chart { display: block; max-width: 100%; }
.grid { stroke: var(--border); stroke-width: 1; }
.tick { fill: var(--text-3); font-size: var(--fs-xs); font-family: var(--font-mono); }
.axis-label { fill: var(--text-2); font-size: var(--fs-xs); }
.band { fill: var(--danger-soft); stroke: none; }
.line { fill: none; stroke: var(--danger-solid); stroke-width: 2.5; }
.dot { fill: var(--danger-solid); }
.marker { stroke: var(--text-2); stroke-width: 1; stroke-dasharray: 4 3; }
.marker-label { fill: var(--text); font-size: var(--fs-xs); font-weight: 600; }
</style>
