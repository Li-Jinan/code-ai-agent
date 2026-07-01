<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 优化提示词功能已移除

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 格式化时间函数已移除，不再需要显示创建时间

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  // 鼠标跟随光效
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 网站标题和描述 -->
      <div class="hero-section">
        <h1 class="hero-title">今安 AI 应用开发</h1>
        <p class="hero-description">一句话轻松创建网站应用</p>
      </div>

      <!-- 用户提示词输入框 -->
      <div class="input-section">
        <a-textarea
          v-model:value="userPrompt"
          placeholder="帮我创建个人博客网站"
          :rows="4"
          :maxlength="1000"
          class="prompt-input"
        />
        <div class="input-actions">
          <a-button type="primary" size="large" @click="createApp" :loading="creating">
            <template #icon>
              <span>↑</span>
            </template>
          </a-button>
        </div>
      </div>

      <!-- 快捷按钮 -->
      <div class="quick-actions">
        <a-button
          type="default"
          @click="
            setPrompt(
              '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
            )
          "
          >个人博客网站</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
            )
          "
          >企业官网</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
            )
          "
          >在线商城</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
            )
          "
          >作品展示网站</a-button
        >
      </div>

      <!-- 我的作品 -->
      <div class="section">
        <h2 class="section-title">我的作品</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <h2 class="section-title">精选案例</h2>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f7fbf8 0%, #eef7f0 38%, #e7f0ea 100%),
    radial-gradient(circle at 18% 22%, rgba(47, 125, 75, 0.1) 0%, transparent 34%),
    radial-gradient(circle at 82% 16%, rgba(126, 184, 138, 0.13) 0%, transparent 36%),
    radial-gradient(circle at 50% 88%, rgba(34, 94, 61, 0.08) 0%, transparent 44%);
  position: relative;
  overflow: hidden;
}

/* 标准化浅网格背景 */
#homePage::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(rgba(47, 125, 75, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(47, 125, 75, 0.045) 1px, transparent 1px),
    linear-gradient(rgba(23, 70, 48, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(23, 70, 48, 0.035) 1px, transparent 1px);
  background-size:
    100px 100px,
    100px 100px,
    20px 20px,
    20px 20px;
  pointer-events: none;
}

/* 柔和绿色光效 */
#homePage::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(
      600px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
      rgba(47, 125, 75, 0.08) 0%,
      rgba(126, 184, 138, 0.06) 42%,
      transparent 80%
    ),
    linear-gradient(45deg, transparent 30%, rgba(47, 125, 75, 0.035) 50%, transparent 70%);
  pointer-events: none;
  opacity: 0.85;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
}

/* 移除居中光束效果 */

/* 英雄区域 */
.hero-section {
  text-align: center;
  padding: 72px 0 48px;
  margin-bottom: 20px;
  color: #173a28;
  position: relative;
  overflow: hidden;
}

.hero-section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(ellipse 780px 320px at center, rgba(126, 184, 138, 0.18) 0%, transparent 72%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.46), transparent 74%);
}

.hero-title {
  font-size: 54px;
  font-weight: 700;
  margin: 0 0 18px;
  line-height: 1.2;
  color: #17633b;
  background: linear-gradient(135deg, #174b32 0%, #238455 48%, #6aa874 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 0;
  position: relative;
  z-index: 2;
}

.hero-description {
  font-size: 18px;
  margin: 0;
  color: #66786c;
  position: relative;
  z-index: 2;
}

/* 输入区域 */
.input-section {
  position: relative;
  margin: 0 auto 24px;
  max-width: 800px;
}

.prompt-input {
  border-radius: 14px;
  border: 1px solid rgba(47, 125, 75, 0.18);
  font-size: 16px;
  padding: 20px 60px 20px 20px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(16px);
  box-shadow: 0 16px 40px rgba(23, 58, 40, 0.1);
}

.prompt-input:focus {
  border-color: #2f7d4b;
  background: #fff;
  box-shadow: 0 18px 44px rgba(47, 125, 75, 0.16);
}

.input-section :deep(.ant-btn-primary) {
  background: #2f7d4b;
  border-color: #2f7d4b;
  box-shadow: 0 8px 20px rgba(47, 125, 75, 0.24);
}

.input-section :deep(.ant-btn-primary:hover) {
  background: #25673e;
  border-color: #25673e;
}

.input-actions {
  position: absolute;
  bottom: 12px;
  right: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 快捷按钮 */
.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 58px;
  flex-wrap: wrap;
}

.quick-actions .ant-btn {
  border-radius: 999px;
  padding: 8px 20px;
  height: auto;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(47, 125, 75, 0.18);
  color: #31533e;
  backdrop-filter: blur(12px);
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
}

.quick-actions .ant-btn:hover {
  background: #f4fbf5;
  border-color: rgba(47, 125, 75, 0.45);
  color: #25673e;
  box-shadow: 0 8px 22px rgba(47, 125, 75, 0.12);
}

/* 区域标题 */
.section {
  margin-bottom: 60px;
}

.section-title {
  font-size: 30px;
  font-weight: 600;
  margin-bottom: 28px;
  color: #173a28;
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-title::before {
  content: '';
  width: 6px;
  height: 28px;
  border-radius: 999px;
  background: #2f7d4b;
}

/* 我的作品网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 精选案例网格 */
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

.pagination-wrapper :deep(.ant-pagination-item-active) {
  border-color: #2f7d4b;
}

.pagination-wrapper :deep(.ant-pagination-item-active a),
.pagination-wrapper :deep(.ant-pagination-item:hover a) {
  color: #2f7d4b;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-description {
    font-size: 16px;
  }

  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    justify-content: center;
  }
}
</style>
