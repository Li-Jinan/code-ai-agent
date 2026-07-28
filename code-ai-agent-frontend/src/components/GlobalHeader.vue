<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">今安 AI 应用</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown :trigger="['click']" placement="bottomRight">
              <a-space class="user-trigger">
                <a-avatar :src="userAvatarSrc" />
                {{ displayUserName }}
              </a-space>
              <template #overlay>
                <div class="user-dropdown-panel">
                  <div class="user-summary">
                    <a-avatar :src="userAvatarSrc" :size="48" />
                    <div class="user-summary-content">
                      <div class="user-summary-name">{{ displayUserName }}</div>
                      <div class="user-summary-account">{{ loginUserStore.loginUser.userAccount }}</div>
                      <div v-if="loginUserStore.loginUser.userEmail" class="user-summary-account">
                        {{ loginUserStore.loginUser.userEmail }}
                      </div>
                      <a-tag :color="loginUserStore.loginUser.userRole === 'admin' ? 'green' : 'blue'">
                        {{ roleText }}
                      </a-tag>
                    </div>
                  </div>
                  <div v-if="loginUserStore.loginUser.userProfile" class="user-profile-text">
                    {{ loginUserStore.loginUser.userProfile }}
                  </div>
                  <a-divider class="user-dropdown-divider" />
                  <a-button type="text" block class="dropdown-action" @click="goProfilePage">
                    <template #icon>
                      <UserOutlined />
                    </template>
                    个人主页
                  </a-button>
                  <a-button type="text" block class="dropdown-action" @click="openProfileModal">
                    <template #icon>
                      <EditOutlined />
                    </template>
                    编辑个人资料
                  </a-button>
                  <a-button type="text" block class="dropdown-action" @click="doLogout">
                    <template #icon>
                      <LogoutOutlined />
                    </template>
                    退出登录
                  </a-button>
                </div>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
    <a-modal
      v-model:open="profileModalVisible"
      title="个人资料"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="savingProfile"
      @ok="saveProfile"
    >
      <div class="profile-preview">
        <a-avatar :src="getAvatarUrl(profileForm.userAvatar)" :size="64" />
        <div>
          <div class="profile-preview-name">{{ profileForm.userName || displayUserName }}</div>
          <div class="profile-preview-account">{{ loginUserStore.loginUser.userAccount }}</div>
        </div>
      </div>
      <a-form layout="vertical" class="profile-form">
        <a-form-item label="昵称">
          <a-input
            v-model:value="profileForm.userName"
            placeholder="给自己起个好记的名字"
            :maxlength="30"
          />
        </a-form-item>
        <a-form-item label="头像链接">
          <a-input
            v-model:value="profileForm.userAvatar"
            placeholder="粘贴图片 URL，留空则使用默认头像"
          />
        </a-form-item>
        <a-form-item label="个人简介">
          <a-textarea
            v-model:value="profileForm.userProfile"
            placeholder="写一句简短介绍"
            :rows="3"
            :maxlength="120"
            show-count
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message, Modal } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { updateMyUser, userLogout } from '@/api/userController.ts'
import { LogoutOutlined, HomeOutlined, EditOutlined, UserOutlined } from '@ant-design/icons-vue'
import { getAvatarUrl } from '@/utils/avatar'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const userAvatarSrc = computed(() => getAvatarUrl(loginUserStore.loginUser.userAvatar))
const displayUserName = computed(() => loginUserStore.loginUser.userName || '我')
const roleText = computed(() => (loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户'))
const profileModalVisible = ref(false)
const savingProfile = ref(false)
const profileForm = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
router.afterEach((to, from, next) => {
  selectedKeys.value = [to.path]
})

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
  {
    key: 'others',
    label: h('a', { href: 'https://github.com/Li-Jinan', target: '_blank' }, 'GitHub'),
    title: 'GitHub',
  },
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 退出登录
const doLogout = async () => {
  Modal.confirm({
    title: '确认退出登录？',
    content: '退出后需要重新登录才能继续创建、编辑和管理你的应用。',
    centered: true,
    okText: '退出登录',
    cancelText: '取消',
    okButtonProps: {
      danger: true,
    },
    async onOk() {
      const res = await userLogout()
      if (res.data.code === 0) {
        loginUserStore.setLoginUser({
          userName: '未登录',
        })
        message.success('退出登录成功')
        await router.push('/user/login')
      } else {
        throw new Error(res.data.message || '退出登录失败')
      }
    },
  })
}

const openProfileModal = () => {
  profileForm.userName = loginUserStore.loginUser.userName || ''
  profileForm.userAvatar = loginUserStore.loginUser.userAvatar || ''
  profileForm.userProfile = loginUserStore.loginUser.userProfile || ''
  profileModalVisible.value = true
}

const goProfilePage = async () => {
  await router.push('/user/profile')
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
      profileModalVisible.value = false
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
</script>

<style scoped>
.header {
  background: #fff;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 48px;
  width: 48px;
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: #17633b;
}

.ant-menu-horizontal {
  border-bottom: none !important;
}

:deep(.ant-menu-light.ant-menu-horizontal > .ant-menu-item-selected) {
  color: #2f7d4b;
}

:deep(.ant-menu-light.ant-menu-horizontal > .ant-menu-item-selected::after),
:deep(.ant-menu-light.ant-menu-horizontal > .ant-menu-item:hover::after) {
  border-bottom-color: #2f7d4b;
}

:deep(.ant-menu-light.ant-menu-horizontal > .ant-menu-item:hover) {
  color: #2f7d4b;
}

.user-trigger {
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-trigger:hover {
  background: #f5f7fb;
}

.user-dropdown-panel {
  width: 260px;
  padding: 14px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.user-summary {
  display: flex;
  gap: 12px;
  text-align: left;
}

.user-summary-content {
  min-width: 0;
}

.user-summary-name {
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
}

.user-summary-account,
.profile-preview-account {
  margin: 2px 0 6px;
  color: #6b7280;
  font-size: 12px;
}

.user-profile-text {
  margin-top: 12px;
  color: #4b5563;
  line-height: 1.5;
  text-align: left;
  word-break: break-word;
}

.user-dropdown-divider {
  margin: 12px 0;
}

.dropdown-action {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  height: 36px;
}

.profile-preview {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.profile-preview-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.profile-form {
  margin-top: 8px;
}
</style>
