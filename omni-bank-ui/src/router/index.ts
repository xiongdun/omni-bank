import { createRouter, createWebHistory } from 'vue-router'
import Workbench from '../views/Workbench.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/workbench' },
    {
      path: '/workbench',
      name: 'workbench',
      component: Workbench,
      meta: { title: '尽调工作台' }
    }
  ]
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? '尽调工作台'} · omni-bank`
})

export default router
