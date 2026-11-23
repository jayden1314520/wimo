<template>
  <div class="app">
    <AppHeader />
    <div class="app__body">
      <NuxtPage />
    </div>
    <!-- 全局弹层：登录即注册 / 移动端左侧菜单 -->
    <AuthModal />
    <MobileNavDrawer />
  </div>
</template>
