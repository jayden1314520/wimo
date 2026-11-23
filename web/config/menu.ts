/**
 * 左侧功能菜单（PC 常驻侧栏 / 移动端抽屉 共用同一份配置）
 * label 为 i18n key，icon 对应 AppIcon 的图标名
 */
export interface MenuItem {
  key: string
  icon: string
  label: string
}

export interface MenuGroup {
  label: string
  items: MenuItem[]
}

export const sidebarGroups: MenuGroup[] = [
  {
    label: 'nav.groups.ecommerce',
    items: [
      { key: 'productSet', icon: 'grid', label: 'nav.items.productSet' },
      { key: 'productDetail', icon: 'detail', label: 'nav.items.productDetail' },
      { key: 'hotReplica', icon: 'copy', label: 'nav.items.hotReplica' },
      { key: 'scene', icon: 'image', label: 'nav.items.scene' },
      { key: 'retouch', icon: 'retouch', label: 'nav.items.retouch' },
      { key: 'poster', icon: 'poster', label: 'nav.items.poster' },
      { key: 'flatLay', icon: 'shirt', label: 'nav.items.flatLay' }
    ]
  },
  {
    label: 'nav.groups.model',
    items: [
      { key: 'modelTryon', icon: 'user', label: 'nav.items.modelTryon' },
      { key: 'anythingWear', icon: 'wear', label: 'nav.items.anythingWear' },
      { key: 'swapModel', icon: 'swap', label: 'nav.items.swapModel' }
    ]
  },
  {
    label: 'nav.groups.video',
    items: [
      { key: 'productVideo', icon: 'video', label: 'nav.items.productVideo' },
      { key: 'salesVideo', icon: 'cart', label: 'nav.items.salesVideo' },
      { key: 'storeVisit', icon: 'store', label: 'nav.items.storeVisit' }
    ]
  }
]
