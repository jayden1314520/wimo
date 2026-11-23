<script setup lang="ts">
const localePath = useLocalePath()
// 根路径 /：按浏览器语言重定向到 /zh 或 /en 下的电商表单页
await navigateTo(localePath('/ecommerce-form'), { redirectCode: 302, replace: true })
</script>

<template>
  <div />
</template>
