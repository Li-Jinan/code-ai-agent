<template>
  <div id="userProfilePage">
    <section class="profile-hero">
      <div class="profile-card">
        <div class="profile-main">
          <a-avatar :src="profileAvatar" :size="88" />
          <div class="profile-title">
            <h2>{{ displayUserName }}</h2>
            <p>{{ loginUserStore.loginUser.userProfile || '这个人正在认真打磨自己的 AI 应用作品。' }}</p>
          </div>
        </div>
        <a-tag :color="loginUserStore.loginUser.userRole === 'admin' ? 'green' : 'blue'">
          {{ roleText }}
        </a-tag>
      </div>
      <div class="profile-actions">
        <a-button type="primary" @click="goHomeCreate">创建新作品</a-button>
        <a-button @click="scrollToWorks">我的作品</a-button>
      </div>
    </section>

    <section class="profile-content">
      <div class="info-panel">
        <div class="section-title">个人信息</div>
        <a-descriptions :column="1" bordered size="middle">
          <a-descriptions-item label="昵称">{{ displayUserName }}</a-descriptions-item>
          <a-descriptions-item label="账号">
            {{ loginUserStore.loginUser.userAccount || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="邮箱">
            {{ loginUserStore.loginUser.userEmail || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="角色">{{ roleText }}</a-descriptions-item>
          <a-descriptions-item label="注册时间">
            {{ formatDate(loginUserStore.loginUser.createTime) }}
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <div class="edit-panel">
        <div class="section-title">编辑资料</div>
        <a-form layout="vertical" :model="profileForm" @finish="saveProfile">
          <a-form-item
            label="昵称"
            name="userName"
            :rules="[{ required: true, message: '请输入昵称' }]"
          >
            <a-input
              v-model:value="profileForm.userName"
              placeholder="给自己起个好记的名字"
              :maxlength="30"
            />
          </a-form-item>
          <a-form-item label="头像链接" name="userAvatar">
            <a-input
              v-model:value="profileForm.userAvatar"
              placeholder="粘贴图片 URL，留空则使用默认头像"
            />
          </a-form-item>
          <a-form-item
            label="邮箱"
            name="userEmail"
            :rules="[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '邮箱格式不正确' },
            ]"
          >
            <a-input v-model:value="profileForm.userEmail" placeholder="请输入邮箱" />
          </a-form-item>
          <a-form-item label="个人简介" name="userProfile">
            <a-textarea
              v-model:value="profileForm.userProfile"
              placeholder="一句话介绍你的方向、能力或当前作品"
              :rows="4"
              :maxlength="120"
              show-count
            />
          </a-form-item>
          <a-button type="primary" html-type="submit" :loading="savingProfile">保存资料</a-button>
        </a-form>
      </div>
    </section>

    <section id="profileWorks" class="profile-works">
      <div class="works-heading">
        <div>
          <div class="section-title">我的作品</div>
          <p>这里集中展示你创建和部署过的 AI 应用，方便继续编辑、部署或查看线上效果。</p>
        </div>
        <a-button type="link" class="refresh-button" @click="loadMyApps">刷新</a-button>
      </div>
      <div v-if="myApps.length > 0" class="works-grid">
        <AppCard
          v-for="app in myApps"
          :key="app.id"
          :app="app"
          @view-chat="viewChat"
          @view-work="viewWork"
        />
      </div>
      <a-empty
        v-else
        class="works-empty"
        description="还没有作品，回到首页输入一句需求就可以开始创建。"
      >
        <a-button type="primary" @click="goHomeCreate">去创建</a-button>
      </a-empty>
      <div v-if="myAppsPage.total > myAppsPage.pageSize" class="pagination-wrapper">
        <a-pagination
          v-model:current="myAppsPage.current"
          v-model:page-size="myAppsPage.pageSize"
          :total="myAppsPage.total"
          :show-size-changer="false"
          :show-total="(total: number) => `共 ${total} 个作品`"
          @change="loadMyApps"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { updateMyUser } from '@/api/userController.ts'
import { listMyAppVoByPage } from '@/api/appController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { getAvatarUrl } from '@/utils/avatar'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const savingProfile = ref(false)
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

const profileForm = reactive({
  userName: '',
  userAvatar: '',
  userEmail: '',
  userProfile: '',
})

const profileAvatar = computed(() => getAvatarUrl(loginUserStore.loginUser.userAvatar))
const displayUserName = computed(() => loginUserStore.loginUser.userName || '我')
const roleText = computed(() => (loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户'))

const syncProfileForm = () => {
  profileForm.userName = loginUserStore.loginUser.userName || ''
  profileForm.userAvatar = loginUserStore.loginUser.userAvatar || ''
  profileForm.userEmail = loginUserStore.loginUser.userEmail || ''
  profileForm.userProfile = loginUserStore.loginUser.userProfile || ''
}

const formatDate = (value?: string) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

const saveProfile = async () => {
  const userName = profileForm.userName.trim()
  if (!userName) {
    message.warning('昵称不能为空')
    return
  }
  const userEmail = profileForm.userEmail.trim()
  if (!userEmail) {
    message.warning('邮箱不能为空')
    return
  }
  savingProfile.value = true
  try {
    const res = await updateMyUser({
      userName,
      userAvatar: getAvatarUrl(profileForm.userAvatar),
      userEmail,
      userProfile: profileForm.userProfile.trim(),
    })
    if (res.data.code === 0 && res.data.data) {
      loginUserStore.setLoginUser(res.data.data)
      syncProfileForm()
      message.success('个人资料已更新')
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } catch (error) {
    console.error('保存个人资料失败：', error)
    message.error('保存失败，请稍后重试')
  } finally {
    savingProfile.value = false
  }
}

const loadMyApps = async () => {
  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'descend',
    })
    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.totalRow || 0
    }
  } catch (error) {
    console.error('加载我的作品失败：', error)
    message.error('加载我的作品失败')
  }
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    window.open(getDeployUrl(app.deployKey), '_blank')
    return
  }
  viewChat(app.id)
}

const goHomeCreate = () => {
  router.push('/')
}

const scrollToWorks = () => {
  document.querySelector('#profileWorks')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }
  syncProfileForm()
  loadMyApps()
  if (router.currentRoute.value.hash === '#profileWorks') {
    await nextTick()
    scrollToWorks()
  }
})
</script>

<style scoped>
#userProfilePage {
  min-height: calc(100vh - 64px);
  padding: 32px 24px 56px;
  background:
    linear-gradient(135deg, rgba(47, 125, 75, 0.08), rgba(75, 124, 172, 0.1)),
    #f7faf8;
}

.profile-hero,
.profile-content {
  max-width: 1080px;
  margin: 0 auto;
}

.profile-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 28px;
  background: #fff;
  border: 1px solid rgba(47, 125, 75, 0.12);
  border-radius: 8px;
  box-shadow: 0 14px 34px rgba(23, 58, 40, 0.08);
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  max-width: 1080px;
  margin: 16px auto 0;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.profile-title {
  min-width: 0;
}

.profile-title h2 {
  margin: 0 0 8px;
  color: #173a28;
  font-size: 26px;
  font-weight: 700;
}

.profile-title p {
  margin: 0;
  color: #66786c;
  line-height: 1.7;
  word-break: break-word;
}

.profile-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 0.85fr);
  gap: 20px;
  margin-top: 20px;
}

.info-panel,
.edit-panel {
  padding: 24px;
  background: #fff;
  border: 1px solid rgba(47, 125, 75, 0.1);
  border-radius: 8px;
}

.profile-works {
  max-width: 1080px;
  margin: 20px auto 0;
  padding: 24px;
  background: #fff;
  border: 1px solid rgba(47, 125, 75, 0.1);
  border-radius: 8px;
}

.works-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.works-heading .section-title {
  margin-bottom: 8px;
}

.works-heading p {
  margin: 0;
  color: #66786c;
  line-height: 1.7;
}

.refresh-button {
  color: #2f7d4b;
  font-weight: 600;
}

.works-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.works-empty {
  padding: 34px 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.section-title {
  margin-bottom: 18px;
  color: #173a28;
  font-size: 18px;
  font-weight: 700;
}

:deep(.ant-btn-primary) {
  background: #2f7d4b;
  border-color: #2f7d4b;
}

:deep(.ant-btn-primary:hover) {
  background: #25673e;
  border-color: #25673e;
}

@media (max-width: 768px) {
  #userProfilePage {
    padding: 20px 14px 40px;
  }

  .profile-card,
  .profile-main {
    align-items: flex-start;
  }

  .profile-card,
  .profile-content {
    grid-template-columns: 1fr;
  }

  .profile-card {
    flex-direction: column;
  }

  .profile-actions,
  .works-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .works-grid {
    grid-template-columns: 1fr;
  }
}
</style>
