<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  addApp,
  listMyAppVoByPage,
  listGoodAppVoByPage,
  listPublicDeployedAppVoByPage,
} from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)
const isLoggedIn = computed(() => Boolean(loginUserStore.loginUser.id))
const RECENT_GENERATION_KEY = 'code-ai-agent:recent-generation'

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

// 社区公开作品数据
const publicApps = ref<API.AppVO[]>([])
const publicAppsPage = reactive({
  current: 1,
  pageSize: 8,
  total: 0,
})

interface RecentGeneration {
  appId: string
  prompt: string
  appName: string
  status: 'creating' | 'generating' | 'preview-ready'
  updatedAt: number
}

const recentGeneration = ref<RecentGeneration | null>(null)

const showcaseCases = [
  {
    title: '个人博客网站',
    type: '内容创作',
    description: '文章列表、Markdown 详情、分类标签、搜索和个人简介，适合沉淀技术文章和作品记录。',
    prompt:
      '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
    accent: 'blog',
    image: '/showcases/blog.png',
    tags: ['Markdown', '搜索', '评论'],
  },
  {
    title: '企业官网',
    type: '品牌展示',
    description: '公司介绍、产品服务、新闻资讯、客户案例和联系方式，适合包装项目与企业形象。',
    prompt:
      '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
    accent: 'business',
    image: '/showcases/business.png',
    tags: ['服务展示', '客户案例', '联系表单'],
  },
  {
    title: '在线商城',
    type: '交易体验',
    description: '商品展示、购物车、订单管理和用户评价，适合展示完整业务闭环和前端交互能力。',
    prompt:
      '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
    accent: 'shop',
    image: '/showcases/shop.png',
    tags: ['商品筛选', '购物车', '订单'],
  },
  {
    title: '作品展示网站',
    type: '个人作品集',
    description: '作品画廊、项目详情、个人简历和联系方式，适合设计师、摄影师或开发者作品集。',
    prompt:
      '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
    accent: 'portfolio',
    image: '/showcases/portfolio.png',
    tags: ['画廊', '项目详情', '简历'],
  },
]

type ShowcaseCase = (typeof showcaseCases)[number]

const showcaseDetailVisible = ref(false)
const selectedShowcase = ref<ShowcaseCase | null>(null)

const visibleMyApps = computed(() => {
  if (!recentGeneration.value) {
    return myApps.value
  }
  return myApps.value.filter((app) => String(app.id) !== recentGeneration.value?.appId)
})

const visiblePublicApps = computed(() => {
  if (!isLoggedIn.value) {
    return publicApps.value
  }
  return publicApps.value.filter((app) => app.userId !== loginUserStore.loginUser.id)
})

const shouldShowRecentGeneration = computed(() => {
  if (!isLoggedIn.value || !recentGeneration.value) {
    return false
  }
  const oneDay = 24 * 60 * 60 * 1000
  return Date.now() - recentGeneration.value.updatedAt < oneDay
})

const recommendationTitle = computed(() => (isLoggedIn.value ? '推荐生成案例' : '可生成案例'))

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

const useShowcaseCase = (item: ShowcaseCase) => {
  setPrompt(item.prompt)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const openShowcaseDetail = (item: ShowcaseCase) => {
  selectedShowcase.value = item
  showcaseDetailVisible.value = true
}

const createFromShowcase = () => {
  if (!selectedShowcase.value) {
    return
  }
  useShowcaseCase(selectedShowcase.value)
  showcaseDetailVisible.value = false
}

// 优化提示词功能已移除

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!isLoggedIn.value) {
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
      saveRecentGeneration({
        appId,
        prompt: userPrompt.value.trim(),
        appName: userPrompt.value.trim().slice(0, 18) || '新建应用',
        status: 'creating',
        updatedAt: Date.now(),
      })
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

const saveRecentGeneration = (record: RecentGeneration) => {
  recentGeneration.value = record
  localStorage.setItem(RECENT_GENERATION_KEY, JSON.stringify(record))
}

const loadRecentGeneration = () => {
  const raw = localStorage.getItem(RECENT_GENERATION_KEY)
  if (!raw) {
    recentGeneration.value = null
    return
  }
  try {
    recentGeneration.value = JSON.parse(raw)
  } catch (error) {
    console.error('读取最近生成记录失败：', error)
    localStorage.removeItem(RECENT_GENERATION_KEY)
    recentGeneration.value = null
  }
}

const continueRecentGeneration = () => {
  if (recentGeneration.value?.appId) {
    router.push(`/app/chat/${recentGeneration.value.appId}`)
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!isLoggedIn.value) {
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

// 加载社区公开作品
const loadPublicApps = async () => {
  try {
    const res = await listPublicDeployedAppVoByPage({
      pageNum: publicAppsPage.current,
      pageSize: publicAppsPage.pageSize,
      sortField: 'deployedTime',
      sortOrder: 'descend',
    })

    if (res.data.code === 0 && res.data.data) {
      publicApps.value = res.data.data.records || []
      publicAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载社区作品失败：', error)
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
  loadRecentGeneration()
  loadMyApps()
  loadFeaturedApps()
  loadPublicApps()

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
      <div v-if="isLoggedIn" class="section">
        <div class="section-heading-row">
          <h2 class="section-title">我的作品</h2>
          <a-button type="link" class="section-action-button" @click="loadMyApps">刷新作品</a-button>
        </div>
        <div v-if="shouldShowRecentGeneration" class="recent-generation-card">
          <div class="recent-generation-info">
            <div class="recent-generation-kicker">最近创建</div>
            <h3>{{ recentGeneration?.appName }}</h3>
            <p>{{ recentGeneration?.prompt }}</p>
            <div class="recent-generation-status">
              <span class="status-dot"></span>
              <span>作品可能仍在生成中，回来后可以继续查看进度或恢复生成。</span>
            </div>
          </div>
          <a-button type="primary" @click="continueRecentGeneration">继续查看</a-button>
        </div>
        <div class="app-grid">
          <AppCard
            v-for="app in visibleMyApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <a-empty
          v-if="myApps.length === 0"
          class="empty-my-apps"
          description="还没有作品。可以先从下方推荐案例挑一个模板开始。"
        />
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

      <div class="section showcase-section">
        <div class="section-heading-row">
          <h2 class="section-title">{{ recommendationTitle }}</h2>
          <RouterLink v-if="!isLoggedIn" class="section-action" to="/user/login">
            登录后开始创建
          </RouterLink>
          <span v-else class="section-action">选择模板快速开始</span>
        </div>
        <div class="showcase-grid">
          <button
            v-for="item in showcaseCases"
            :key="item.title"
            type="button"
            class="showcase-card"
            @click="openShowcaseDetail(item)"
          >
            <div class="showcase-preview" :class="`showcase-preview--${item.accent}`">
              <img :src="item.image" :alt="`${item.title}真实生成截图`" loading="lazy" />
            </div>
            <div class="showcase-content">
              <div class="showcase-meta">{{ item.type }}</div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
              <div class="showcase-tags">
                <span v-for="tag in item.tags" :key="tag">{{ tag }}</span>
              </div>
              <div class="showcase-card-actions">
                <a-button type="link" @click.stop="openShowcaseDetail(item)">查看示例</a-button>
                <a-button type="primary" size="small" @click.stop="useShowcaseCase(item)">
                  使用模板
                </a-button>
              </div>
            </div>
          </button>
        </div>
      </div>

      <div v-if="visiblePublicApps.length > 0" class="section">
        <div class="section-heading-row">
          <h2 class="section-title">社区作品</h2>
          <span class="section-action">看看其他用户已经部署的效果</span>
        </div>
        <div class="featured-grid">
          <AppCard
            v-for="app in visiblePublicApps"
            :key="app.id"
            :app="app"
            :show-chat="false"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="publicAppsPage.current"
            v-model:page-size="publicAppsPage.pageSize"
            :total="publicAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个公开作品`"
            @change="loadPublicApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div v-if="featuredApps.length > 0" class="section">
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

    <a-modal
      v-model:open="showcaseDetailVisible"
      :title="selectedShowcase?.title"
      width="760px"
      centered
      ok-text="使用这个模板"
      cancel-text="先看看"
      @ok="createFromShowcase"
    >
      <div v-if="selectedShowcase" class="showcase-modal">
        <div class="showcase-modal-preview">
          <img :src="selectedShowcase.image" :alt="`${selectedShowcase.title}真实生成截图`" />
        </div>
        <div class="showcase-modal-info">
          <div class="showcase-meta">{{ selectedShowcase.type }}</div>
          <p>{{ selectedShowcase.description }}</p>
          <div class="showcase-tags">
            <span v-for="tag in selectedShowcase.tags" :key="tag">{{ tag }}</span>
          </div>
          <a-alert
            class="showcase-modal-tip"
            type="info"
            show-icon
            message="这是通过平台 AI 生成接口真实生成的页面截图，点击“使用这个模板”后会把提示词填入首页输入框。"
          />
        </div>
      </div>
    </a-modal>
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

.section-heading-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 28px;
}

.section-title {
  font-size: 30px;
  font-weight: 600;
  margin: 0 0 28px;
  color: #173a28;
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-heading-row .section-title {
  margin-bottom: 0;
}

.section-title::before {
  content: '';
  width: 6px;
  height: 28px;
  border-radius: 999px;
  background: #2f7d4b;
}

.section-action {
  color: #2f7d4b;
  font-weight: 600;
}

.section-action-button {
  color: #2f7d4b;
  font-weight: 600;
}

.recent-generation-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
  padding: 22px 24px;
  border: 1px solid rgba(47, 125, 75, 0.16);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(47, 125, 75, 0.08), rgba(255, 255, 255, 0.94) 48%),
    rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 34px rgba(23, 58, 40, 0.1);
}

.recent-generation-info {
  min-width: 0;
}

.recent-generation-kicker {
  margin-bottom: 6px;
  color: #2f7d4b;
  font-size: 13px;
  font-weight: 700;
}

.recent-generation-info h3 {
  margin: 0 0 8px;
  color: #173a28;
  font-size: 20px;
}

.recent-generation-info p {
  display: -webkit-box;
  margin: 0 0 10px;
  color: #617267;
  line-height: 1.6;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.recent-generation-status {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4d6758;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #2f7d4b;
  box-shadow: 0 0 0 6px rgba(47, 125, 75, 0.12);
}

.recent-generation-card :deep(.ant-btn-primary) {
  flex-shrink: 0;
  background: #2f7d4b;
  border-color: #2f7d4b;
}

.empty-my-apps {
  margin: 18px 0 8px;
  padding: 28px;
  border: 1px dashed rgba(47, 125, 75, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
}

.showcase-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.showcase-card {
  appearance: none;
  border: 1px solid rgba(47, 125, 75, 0.14);
  background: rgba(255, 255, 255, 0.94);
  border-radius: 8px;
  padding: 0;
  overflow: hidden;
  text-align: left;
  box-shadow: 0 12px 30px rgba(23, 58, 40, 0.1);
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s,
    border-color 0.2s;
}

.showcase-card:hover {
  transform: translateY(-4px);
  border-color: rgba(47, 125, 75, 0.32);
  box-shadow: 0 18px 40px rgba(23, 58, 40, 0.16);
}

.showcase-preview {
  height: 178px;
  padding: 14px;
  background:
    radial-gradient(circle at 20% 18%, rgba(47, 125, 75, 0.18), transparent 30%),
    linear-gradient(135deg, #e7f4ea 0%, #f8fbf8 100%);
}

.showcase-preview img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 8px;
  object-fit: cover;
  object-position: top center;
  background: #fff;
  border: 1px solid rgba(23, 58, 40, 0.08);
  box-shadow: 0 10px 24px rgba(23, 58, 40, 0.1);
}

.showcase-preview--business {
  background:
    radial-gradient(circle at 82% 18%, rgba(55, 109, 171, 0.18), transparent 32%),
    linear-gradient(135deg, #e7eef6 0%, #f8fbfc 100%);
}

.showcase-preview--shop {
  background:
    radial-gradient(circle at 80% 20%, rgba(178, 124, 49, 0.18), transparent 32%),
    linear-gradient(135deg, #f3eee3 0%, #fbfaf5 100%);
}

.showcase-preview--portfolio {
  background:
    radial-gradient(circle at 24% 18%, rgba(119, 86, 160, 0.18), transparent 32%),
    linear-gradient(135deg, #eeeaf5 0%, #fbf9fc 100%);
}

.preview-browser {
  height: 22px;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  border-radius: 8px 8px 0 0;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(23, 58, 40, 0.08);
  border-bottom: none;
}

.preview-browser span {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #7eb88a;
}

.preview-body {
  height: 126px;
  padding: 12px;
  border-radius: 0 0 8px 8px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(23, 58, 40, 0.08);
  overflow: hidden;
}

.preview-nav,
.preview-shop-top,
.preview-portfolio-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.preview-nav span,
.preview-shop-top strong,
.preview-portfolio-hero strong,
.preview-business-hero strong {
  color: #173a28;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
}

.preview-nav i,
.preview-portfolio-hero span {
  width: 42px;
  height: 8px;
  border-radius: 999px;
  background: rgba(47, 125, 75, 0.2);
}

.preview-blog-hero {
  min-height: 48px;
  padding: 10px;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(47, 125, 75, 0.18), transparent 64%),
    #f5fbf6;
  margin-bottom: 9px;
}

.preview-blog-hero strong,
.preview-blog-hero small {
  display: block;
}

.preview-blog-hero strong {
  color: #24583a;
  font-size: 15px;
  line-height: 1.2;
}

.preview-blog-hero small {
  color: #6a7b70;
  font-size: 10px;
  margin-top: 5px;
}

.preview-posts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
}

.preview-posts b {
  height: 22px;
  border-radius: 6px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.24), transparent),
    rgba(47, 125, 75, 0.16);
}

.preview-business-hero {
  min-height: 44px;
  padding: 10px;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(49, 104, 166, 0.2), transparent 68%),
    #f5f9fc;
  margin-bottom: 9px;
}

.preview-business-hero strong {
  display: block;
  color: #244765;
}

.preview-business-hero span {
  display: block;
  width: 62%;
  height: 7px;
  margin-top: 9px;
  border-radius: 999px;
  background: rgba(49, 104, 166, 0.22);
}

.preview-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
  margin-bottom: 8px;
}

.preview-metrics b {
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: #eef4fa;
  color: #315b83;
  font-size: 11px;
}

.preview-service-row {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 7px;
}

.preview-service-row i {
  height: 22px;
  border-radius: 6px;
  background: rgba(49, 104, 166, 0.14);
}

.preview-shop-top {
  padding: 9px 10px;
  border-radius: 8px;
  background: #fff9ef;
}

.preview-shop-top strong {
  color: #6a4b1e;
}

.preview-shop-top span {
  padding: 3px 7px;
  border-radius: 999px;
  background: #f1dfbd;
  color: #745324;
  font-size: 11px;
  font-weight: 700;
}

.preview-products {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
}

.preview-products b {
  height: 62px;
  padding: 7px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(143, 100, 39, 0.11);
}

.preview-products i,
.preview-products em {
  display: block;
  border-radius: 5px;
}

.preview-products i {
  height: 29px;
  background:
    linear-gradient(135deg, rgba(178, 124, 49, 0.32), transparent),
    #f1e3c8;
}

.preview-products em {
  width: 70%;
  height: 7px;
  margin-top: 8px;
  background: rgba(132, 91, 35, 0.22);
}

.preview-portfolio-hero {
  padding: 9px 10px;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(119, 86, 160, 0.16), transparent 72%),
    #faf7fc;
}

.preview-portfolio-hero strong {
  color: #4b3b67;
}

.preview-gallery {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 1fr;
  grid-auto-rows: 25px;
  gap: 7px;
}

.preview-gallery b {
  border-radius: 7px;
  background:
    linear-gradient(135deg, rgba(119, 86, 160, 0.28), transparent 70%),
    #ebe4f0;
}

.preview-gallery b:first-child {
  grid-row: span 2;
}

.preview-gallery b:nth-child(4) {
  grid-column: span 2;
}

.showcase-content {
  padding: 16px;
}

.showcase-meta {
  color: #2f7d4b;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}

.showcase-content h3 {
  margin: 0 0 8px;
  color: #173a28;
  font-size: 18px;
}

.showcase-content p {
  min-height: 66px;
  margin: 0 0 14px;
  color: #617267;
  line-height: 1.55;
}

.showcase-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.showcase-tags span {
  padding: 4px 8px;
  border-radius: 6px;
  background: #f2f7f3;
  color: #31533e;
  font-size: 12px;
}

.showcase-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 16px;
}

.showcase-card-actions :deep(.ant-btn-link) {
  padding: 0;
  color: #2f7d4b;
}

.showcase-card-actions :deep(.ant-btn-primary) {
  background: #2f7d4b;
  border-color: #2f7d4b;
}

.showcase-modal {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(240px, 0.75fr);
  gap: 22px;
  align-items: stretch;
}

.showcase-modal-preview {
  min-height: 300px;
  padding: 0;
  border-radius: 8px;
  border: 1px solid rgba(47, 125, 75, 0.12);
  overflow: hidden;
  background: #fff;
}

.showcase-modal-preview img {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 360px;
  object-fit: cover;
  object-position: top center;
}

.modal-browser {
  height: 36px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  border-radius: 8px 8px 0 0;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(23, 58, 40, 0.08);
  border-bottom: none;
}

.modal-browser span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #7eb88a;
}

.modal-browser strong {
  margin-left: 8px;
  color: #31533e;
  font-size: 13px;
}

.modal-preview-body {
  min-height: 230px;
  display: grid;
  grid-template-columns: 86px 1fr;
  gap: 18px;
  padding: 20px;
  border-radius: 0 0 8px 8px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(23, 58, 40, 0.08);
}

.modal-preview-body aside {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modal-preview-body aside i {
  height: 34px;
  border-radius: 8px;
  background: rgba(47, 125, 75, 0.14);
}

.modal-preview-body main {
  display: flex;
  flex-direction: column;
}

.modal-hero-line {
  height: 36px;
  width: 70%;
  border-radius: 999px;
  background: rgba(47, 125, 75, 0.22);
  margin: 12px 0 18px;
}

.modal-copy-line {
  height: 12px;
  width: 92%;
  border-radius: 999px;
  background: rgba(47, 125, 75, 0.14);
  margin-bottom: 12px;
}

.modal-copy-line--short {
  width: 58%;
}

.modal-card-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: auto;
}

.modal-card-row b {
  height: 74px;
  border-radius: 8px;
  background: rgba(47, 125, 75, 0.16);
}

.showcase-modal-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.showcase-modal-info p {
  color: #516459;
  line-height: 1.7;
  margin: 8px 0 16px;
}

.showcase-modal-tip {
  margin-top: auto;
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
  .featured-grid,
  .showcase-grid {
    grid-template-columns: 1fr;
  }

  .section-heading-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .recent-generation-card {
    align-items: stretch;
    flex-direction: column;
  }

  .recent-generation-card :deep(.ant-btn-primary) {
    width: 100%;
  }

  .quick-actions {
    justify-content: center;
  }
}
</style>
