<template>
  <svg
    :width="size" :height="size" viewBox="0 0 24 24"
    fill="none" stroke="currentColor" :stroke-width="stroke"
    stroke-linecap="round" stroke-linejoin="round"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{ name: string; size?: number; stroke?: number }>(),
  { size: 16, stroke: 1.7 }
)

const ICONS: Record<string, string[]> = {
  grid: ['M3 3h7v7H3z', 'M14 3h7v7h-7z', 'M3 14h7v7H3z', 'M14 14h7v7h-7z'],
  detail: ['M3 4h18v16H3z', 'M3 10h18', 'M7 14h6', 'M7 17h9'],
  copy: ['M8 8h12v12H8z', 'M4 16V4h12'],
  image: ['M3 4h18v16H3z', 'M8.5 11a1.5 1.5 0 100-3 1.5 1.5 0 000 3z', 'M21 16l-5-5-9 9'],
  retouch: ['M15 4V2', 'M15 10V8', 'M11 6H9', 'M21 6h-2', 'M18 3l1.5 1.5', 'M18 9l1.5-1.5', 'M12 12L3 21'],
  poster: ['M3 4h18v13H3z', 'M8 21h8', 'M9 10l3-3 3 3'],
  shirt: ['M9 3l3 2 3-2 5 4-3 3v10H7V10L4 7z'],
  user: ['M12 11a4 4 0 100-8 4 4 0 000 8z', 'M4 21c0-4 3.6-6 8-6s8 2 8 6'],
  wear: ['M9 3h6l-1 4 4 14H6L10 7z'],
  swap: ['M4 8h12l-3-3', 'M20 16H8l3 3'],
  video: ['M3 6h13v12H3z', 'M16 10l5-3v10l-5-3'],
  cart: ['M3 5h2l2 11h11', 'M8 8h12l-2 7H9', 'M9 20a1 1 0 100-2 1 1 0 000 2z', 'M17 20a1 1 0 100-2 1 1 0 000 2z'],
  clock: ['M12 21a9 9 0 100-18 9 9 0 000 18z', 'M12 7v5l3 2'],
  globe: ['M12 21a9 9 0 100-18 9 9 0 000 18z', 'M3 12h18', 'M12 3c2.5 2.6 3.8 5.7 3.8 9S14.5 18.4 12 21c-2.5-2.6-3.8-5.7-3.8-9S9.5 5.6 12 3z'],
  monitor: ['M3 4h18v12H3z', 'M9 20h6', 'M12 16v4'],
  pencil: ['M4 20h4L20 8l-4-4L4 16z', 'M14 6l4 4'],
  plus: ['M12 5v14', 'M5 12h14'],
  close: ['M6 6l12 12', 'M18 6L6 18'],
  wand: ['M15 4V2', 'M15 10V8', 'M11 6H9', 'M21 6h-2', 'M18 3l1.5 1.5', 'M18 9l1.5-1.5', 'M12 12L3 21'],
  spark: ['M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z'],
  bolt: ['M13 2L4 14h6l-1 8 9-12h-6l1-8z'],
  check: ['M4 12l5 5L20 6'],
  'chevron-down': ['M6 9l6 6 6-6'],
  minus: ['M5 12h14'],
  play: ['M8 5l11 7-11 7z'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  eye: ['M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12z', 'M12 15a3 3 0 100-6 3 3 0 000 6z'],
  'eye-off': ['M3 3l18 18', 'M10.6 6.2A9.9 9.9 0 0112 6c6.4 0 10 6 10 6a17 17 0 01-3.1 3.6', 'M6.3 6.3C3.6 8 2 12 2 12s3.6 6 10 6a9.7 9.7 0 004.1-.9', 'M9.9 9.9a3 3 0 004.2 4.2'],
  mail: ['M3 5h18v14H3z', 'M3 6l9 7 9-7'],
  lock: ['M5 11h14v10H5z', 'M8 11V7a4 4 0 018 0v4'],
  shield: ['M12 2l8 3v6c0 5-3.4 8.4-8 11-4.6-2.6-8-6-8-11V5z', 'M9 12l2 2 4-4']
}

const paths = computed(() => ICONS[props.name] || ICONS.grid)
</script>
