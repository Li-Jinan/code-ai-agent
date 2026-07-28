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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { updateMyUser } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { getAvatarUrl } from '@/utils/avatar'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const savingProfile = ref(false)

const profileForm = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

const profileAvatar = computed(() => getAvatarUrl(loginUserStore.loginUser.userAvatar))
const displayUserName = computed(() => loginUserStore.loginUser.userName || '我')
const roleText = computed(() => (loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户'))

const syncProfileForm = () => {
  profileForm.userName = loginUserStore.loginUser.userName || ''
  profileForm.userAvatar = loginUserStore.loginUser.userAvatar || ''
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
  savingProfile.value = true
  try {
    const res = await updateMyUser({
      userName,
      userAvatar: getAvatarUrl(profileForm.userAvatar),
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

onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }
  syncProfileForm()
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
}
</style>
