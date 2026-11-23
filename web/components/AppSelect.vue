<template>
  <div ref="root" class="app-select" :class="{ 'is-open': open }">
    <button
      type="button" class="app-select__trigger"
      :aria-expanded="open" :aria-label="label || undefined"
      @click="toggle"
    >
      <span class="app-select__value">{{ currentLabel || placeholder || '\u00a0' }}</span>
      <AppIcon name="chevron-down" :size="14" class="app-select__caret" />
    </button>

    <Transition name="dropdown">
      <div
        v-if="open" class="app-select__menu"
        :class="{ 'app-select__menu--up': dropUp }"
        role="listbox"
      >
        <button
          v-for="o in options" :key="o.value" type="button"
          class="app-select__option"
          :class="{ 'is-selected': o.value === modelValue }"
          role="option" :aria-selected="o.value === modelValue"
          @click="choose(o.value)"
        >
          <span>{{ o.label }}</span>
          <AppIcon v-if="o.value === modelValue" name="check" :size="13" />
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

interface Option { value: string | number; label: string }

const props = withDefaults(defineProps<{
  modelValue: string | number
  options: Option[]
  label?: string
  placeholder?: string
}>(), { label: '', placeholder: '' })

const emit = defineEmits<{ 'update:modelValue': [value: string | number] }>()

const open = ref(false)
const dropUp = ref(false)
const root = ref<HTMLElement | null>(null)

const currentLabel = computed(() =>
  props.options.find(o => o.value === props.modelValue)?.label ?? ''
)

function toggle() {
  if (!open.value && root.value) {
    // 靠近视口底部时向上展开，避免被表单滚动容器裁掉
    const rect = root.value.getBoundingClientRect()
    dropUp.value = window.innerHeight - rect.bottom < 224
  }
  open.value = !open.value
}

function choose(v: string | number) {
  emit('update:modelValue', v)
  open.value = false
}

function onOutside(e: MouseEvent) {
  if (root.value && !root.value.contains(e.target as Node)) open.value = false
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('click', onOutside)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onOutside)
  document.removeEventListener('keydown', onKey)
})
</script>
