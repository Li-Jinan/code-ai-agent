<template>
  <div id="userLoginPage">
    <section class="login-shell">
      <div class="login-copy">
        <div class="copy-badge">AI 应用生成平台</div>
        <h1>欢迎回来</h1>
        <p>登录后继续创建、预览和管理你的 AI 应用作品。</p>
        <div class="copy-points">
          <span>应用生成</span>
          <span>作品管理</span>
        </div>
      </div>

      <div class="login-card">
        <div class="login-card-header">
          <h2 class="title">{{ cardTitle }}</h2>
          <div class="desc">不写一行代码，生成完整应用</div>
        </div>

        <a-segmented
          v-model:value="loginMode"
          class="login-mode"
          :options="[
            { label: '账号密码', value: 'password' },
            { label: '邮箱验证码', value: 'emailCode' },
          ]"
        />

        <a-alert
          v-if="demoAccountEnabled"
          class="demo-account"
          type="info"
          show-icon
          message="在线体验账号"
        >
          <template #description>
            <span>账号：{{ demoAccountUsername }}　密码：{{ demoAccountPassword }}</span>
          </template>
          <template #action>
            <a-button size="small" type="link" @click="fillDemoAccount">一键填入</a-button>
          </template>
        </a-alert>

        <a-form
          :key="formKey"
          :model="loginFormModel"
          name="basic"
          autocomplete="off"
          @finish="handleSubmit"
        >
          <template v-if="loginMode === 'password'">
            <a-segmented
              v-model:value="passwordAction"
              class="password-action"
              :options="[
                { label: '登录', value: 'login' },
                { label: '注册', value: 'register' },
              ]"
            />

            <template v-if="passwordAction === 'login'">
              <a-form-item
                name="userAccount"
                :rules="[{ required: true, message: '请输入账号或邮箱' }]"
              >
                <a-input v-model:value="formState.userAccount" placeholder="请输入账号或邮箱" />
              </a-form-item>
              <a-form-item
                name="userPassword"
                :rules="[
                  { required: true, message: '请输入密码' },
                  { min: 8, message: '密码长度不能小于 8 位' },
                ]"
              >
                <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
              </a-form-item>
              <div class="mode-tip password-links">
                <a-button type="link" @click="passwordAction = 'register'">没有账号？注册账号</a-button>
                <a-button type="link" @click="openForgotPassword">忘记密码</a-button>
              </div>
            </template>

            <template v-else>
              <a-form-item
                name="userName"
                :rules="[{ required: true, message: '请输入昵称' }]"
              >
                <a-input v-model:value="registerForm.userName" placeholder="请输入昵称" />
              </a-form-item>
              <a-form-item
                name="userAccount"
                :rules="[
                  { required: true, message: '请输入账号' },
                  { min: 4, message: '账号长度不能小于 4 位' },
                ]"
              >
                <a-input v-model:value="registerForm.userAccount" placeholder="请输入账号" />
              </a-form-item>
              <a-form-item
                name="userEmail"
                :rules="[
                  { required: true, message: '请输入邮箱' },
                  { type: 'email', message: '邮箱格式不正确' },
                ]"
              >
                <a-input
                  v-model:value="registerForm.userEmail"
                  type="email"
                  autocomplete="email"
                  placeholder="请输入邮箱（用于忘记密码时重置密码）"
                />
              </a-form-item>
              <a-form-item
                name="userPassword"
                :rules="[
                  { required: true, message: '请输入密码' },
                  { min: 8, message: '密码长度不能小于 8 位' },
                ]"
              >
                <a-input-password v-model:value="registerForm.userPassword" placeholder="请输入密码" />
              </a-form-item>
              <a-form-item
                name="checkPassword"
                :rules="[
                  { required: true, message: '请再次输入密码' },
                  { min: 8, message: '密码长度不能小于 8 位' },
                ]"
              >
                <a-input-password v-model:value="registerForm.checkPassword" placeholder="请再次输入密码" />
              </a-form-item>
              <div class="mode-tip password-links">
                <span>已有账号？</span>
                <a-button type="link" @click="passwordAction = 'login'">返回登录</a-button>
              </div>
              <a-form-item class="agreement-item">
                <a-checkbox v-model:checked="agreementChecked">
                  我已阅读并同意
                  <a-button type="link" class="agreement-link" @click.prevent="showAgreement">
                    用户协议与隐私说明
                  </a-button>
                </a-checkbox>
              </a-form-item>
            </template>
          </template>

          <template v-else>
            <a-form-item
              name="userEmail"
              :rules="[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '邮箱格式不正确' },
              ]"
            >
              <a-input v-model:value="emailCodeForm.userEmail" placeholder="请输入邮箱" />
            </a-form-item>
            <a-form-item name="emailCode" :rules="[{ required: true, message: '请输入验证码' }]">
              <a-input v-model:value="emailCodeForm.emailCode" placeholder="请输入验证码">
                <template #addonAfter>
                  <a-button
                    type="link"
                    class="code-button"
                    :disabled="countdown > 0"
                    :loading="sendingCode"
                    @click.prevent="sendCode"
                  >
                    {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
                  </a-button>
                </template>
              </a-input>
            </a-form-item>
            <div class="mode-tip">
              <span>邮箱验证码可直接登录；邮箱未注册时会自动创建账号。</span>
            </div>
            <a-form-item class="agreement-item">
              <a-checkbox v-model:checked="agreementChecked">
                我已阅读并同意
                <a-button type="link" class="agreement-link" @click.prevent="showAgreement">
                  用户协议与隐私说明
                </a-button>
              </a-checkbox>
            </a-form-item>
          </template>

          <a-form-item>
            <a-button type="primary" html-type="submit" class="submit-button" :loading="submitLoading">
              {{ submitText }}
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </section>

    <a-modal
      v-model:open="forgotPasswordOpen"
      title="通过邮箱重置密码"
      centered
      :mask-closable="false"
      :confirm-loading="resetPasswordLoading"
      ok-text="确认重置"
      cancel-text="取消"
      @ok="handleResetPassword"
    >
      <a-form ref="forgotPasswordFormRef" layout="vertical" :model="forgotPasswordForm">
        <a-form-item
          label="绑定邮箱"
          name="userEmail"
          :rules="[
            { required: true, message: '请输入绑定邮箱' },
            { type: 'email', message: '邮箱格式不正确' },
          ]"
        >
          <a-input v-model:value="forgotPasswordForm.userEmail" placeholder="请输入绑定邮箱" />
        </a-form-item>
        <a-form-item
          label="验证码"
          name="emailCode"
          :rules="[{ required: true, message: '请输入验证码' }]"
        >
          <a-input v-model:value="forgotPasswordForm.emailCode" placeholder="请输入验证码">
            <template #addonAfter>
              <a-button
                type="link"
                class="code-button"
                :disabled="resetCountdown > 0"
                :loading="sendingResetCode"
                @click.prevent="sendResetPasswordCode"
              >
                {{ resetCountdown > 0 ? `${resetCountdown}s` : '发送验证码' }}
              </a-button>
            </template>
          </a-input>
        </a-form-item>
        <a-form-item
          label="新密码"
          name="newPassword"
          :rules="[
            { required: true, message: '请输入新密码' },
            { min: 8, message: '密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password v-model:value="forgotPasswordForm.newPassword" placeholder="请输入新密码" />
        </a-form-item>
        <a-form-item
          label="确认新密码"
          name="checkPassword"
          :rules="[
            { required: true, message: '请再次输入新密码' },
            { min: 8, message: '密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password
            v-model:value="forgotPasswordForm.checkPassword"
            placeholder="请再次输入新密码"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  resetPassword as resetUserPassword,
  sendEmailLoginCode,
  userEmailCodeLogin,
  userLogin,
  userRegister,
} from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'

const loginMode = ref<'password' | 'emailCode'>('password')
const passwordAction = ref<'login' | 'register'>('login')
const sendingCode = ref(false)
const submitLoading = ref(false)
const agreementChecked = ref(false)
const countdown = ref(0)
const forgotPasswordOpen = ref(false)
const demoAccountEnabled = import.meta.env.VITE_DEMO_ACCOUNT_ENABLED !== 'false'
const demoAccountUsername = import.meta.env.VITE_DEMO_ACCOUNT_USERNAME || 'demo'
const demoAccountPassword = import.meta.env.VITE_DEMO_ACCOUNT_PASSWORD || 'demo123456'

const fillDemoAccount = async () => {
  loginMode.value = 'password'
  passwordAction.value = 'login'
  await nextTick()
  Object.assign(formState, {
    userAccount: demoAccountUsername,
    userPassword: demoAccountPassword,
  })
}
const forgotPasswordFormRef = ref<FormInstance>()
const sendingResetCode = ref(false)
const resetPasswordLoading = ref(false)
const resetCountdown = ref(0)
let countdownTimer: number | undefined
let resetCountdownTimer: number | undefined

const formState = reactive<API.UserLoginRequest>({
  userAccount: demoAccountEnabled ? demoAccountUsername : '',
  userPassword: demoAccountEnabled ? demoAccountPassword : '',
})

const emailCodeForm = reactive<API.UserEmailLoginRequest>({
  userEmail: '',
  emailCode: '',
})

const registerForm = reactive<API.UserRegisterRequest>({
  userName: '',
  userAccount: '',
  userEmail: '',
  userPassword: '',
  checkPassword: '',
})

const forgotPasswordForm = reactive<API.UserPasswordResetRequest>({
  userEmail: '',
  emailCode: '',
  newPassword: '',
  checkPassword: '',
})

const loginFormModel = computed(() => {
  if (loginMode.value === 'emailCode') {
    return emailCodeForm
  }
  return passwordAction.value === 'login' ? formState : registerForm
})
const formKey = computed(() => `${loginMode.value}-${passwordAction.value}`)
const cardTitle = computed(() => {
  if (loginMode.value === 'emailCode') {
    return '邮箱登录 / 注册'
  }
  return passwordAction.value === 'login' ? '用户登录' : '创建账号'
})
const submitText = computed(() => {
  if (loginMode.value === 'emailCode') {
    return '登录 / 注册'
  }
  return passwordAction.value === 'login' ? '登录' : '注册并登录'
})

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

const showActionModal = (title: string, content: string, okText = '确认') => {
  Modal.confirm({
    title,
    content,
    centered: true,
    okText,
    cancelText: '取消',
    maskClosable: false,
  })
}

const showAgreement = () => {
  Modal.info({
    title: '用户协议与隐私说明',
    content:
      '你需要使用真实可访问的邮箱接收验证码。平台会保存账号、邮箱、应用作品和必要登录状态，用于提供登录、应用生成和个人主页功能。请勿上传违法、侵权或包含敏感信息的内容。',
    centered: true,
    okText: '我知道了',
  })
}

const finishLogin = async (loginUser: API.LoginUserVO) => {
  loginUserStore.setLoginUser(loginUser)
  message.success('登录成功')
  await router.push({
    path: '/',
    replace: true,
  })
  loginUserStore.fetchLoginUser().catch((error) => {
    console.error('刷新登录用户失败：', error)
  })
}

const startCountdown = () => {
  countdown.value = 60
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && countdownTimer) {
      window.clearInterval(countdownTimer)
      countdownTimer = undefined
    }
  }, 1000)
}

const startResetCountdown = () => {
  resetCountdown.value = 60
  resetCountdownTimer = window.setInterval(() => {
    resetCountdown.value -= 1
    if (resetCountdown.value <= 0 && resetCountdownTimer) {
      window.clearInterval(resetCountdownTimer)
      resetCountdownTimer = undefined
    }
  }, 1000)
}

const sendCode = async () => {
  const userEmail = emailCodeForm.userEmail?.trim()
  if (!agreementChecked.value) {
    showActionModal('请先同意用户协议', '邮箱验证码会用于登录或自动创建账号，请先阅读并同意用户协议与隐私说明。')
    return
  }
  if (!userEmail) {
    showActionModal('需要邮箱地址', '请输入邮箱后再发送验证码。')
    return
  }
  sendingCode.value = true
  try {
    const res = await sendEmailLoginCode({
      userEmail,
      verifyScene: '登录或注册账号',
      codePurpose: 'login',
    })
    if (res.data.code === 0) {
      message.success('验证码已发送，未注册邮箱会自动创建账号')
      startCountdown()
    } else {
      showActionModal('验证码发送失败', res.data.message || '发送失败，请稍后重试。', '重新填写')
    }
  } catch (error) {
    console.error('发送验证码失败：', error)
    showActionModal('验证码发送失败', '当前邮箱验证码暂时发不出去，请稍后重试或检查邮箱地址。', '重新填写')
  } finally {
    sendingCode.value = false
  }
}

const openForgotPassword = () => {
  const account = formState.userAccount?.trim() || ''
  if (account.includes('@')) {
    forgotPasswordForm.userEmail = account
  }
  forgotPasswordOpen.value = true
}

const sendResetPasswordCode = async () => {
  const userEmail = forgotPasswordForm.userEmail?.trim()
  if (!userEmail) {
    message.warning('请先输入绑定邮箱')
    return
  }
  sendingResetCode.value = true
  try {
    const res = await sendEmailLoginCode({
      userEmail,
      verifyScene: '重置账号密码',
      codePurpose: 'resetPassword',
    })
    if (res.data.code === 0) {
      message.success('重置密码验证码已发送')
      startResetCountdown()
    } else {
      message.error(res.data.message || '验证码发送失败')
    }
  } catch (error) {
    console.error('发送重置密码验证码失败：', error)
    message.error('验证码发送失败，请稍后重试')
  } finally {
    sendingResetCode.value = false
  }
}

const handleResetPassword = async () => {
  try {
    await forgotPasswordFormRef.value?.validate()
  } catch {
    return
  }
  if (forgotPasswordForm.newPassword !== forgotPasswordForm.checkPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  resetPasswordLoading.value = true
  try {
    const res = await resetUserPassword({
      userEmail: forgotPasswordForm.userEmail?.trim(),
      emailCode: forgotPasswordForm.emailCode?.trim(),
      newPassword: forgotPasswordForm.newPassword,
      checkPassword: forgotPasswordForm.checkPassword,
    })
    if (res.data.code === 0) {
      formState.userAccount = forgotPasswordForm.userEmail?.trim()
      formState.userPassword = ''
      forgotPasswordOpen.value = false
      loginMode.value = 'password'
      passwordAction.value = 'login'
      forgotPasswordForm.emailCode = ''
      forgotPasswordForm.newPassword = ''
      forgotPasswordForm.checkPassword = ''
      message.success('密码已重置，请使用新密码登录')
    } else {
      message.error(res.data.message || '密码重置失败')
    }
  } catch (error) {
    console.error('重置密码失败：', error)
    message.error('密码重置失败，请检查验证码')
  } finally {
    resetPasswordLoading.value = false
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async () => {
  if (submitLoading.value) {
    return
  }
  const isRegistration =
    loginMode.value === 'emailCode' ||
    (loginMode.value === 'password' && passwordAction.value === 'register')
  if (isRegistration && !agreementChecked.value) {
    const content =
      loginMode.value === 'emailCode'
        ? '邮箱验证码登录会在账号不存在时自动注册，请先阅读并同意用户协议与隐私说明。'
        : '创建密码账号前，请先阅读并同意用户协议与隐私说明。'
    showActionModal('请先同意用户协议', content)
    return
  }
  if (loginMode.value === 'password' && passwordAction.value === 'register') {
    if (registerForm.userPassword !== registerForm.checkPassword) {
      showActionModal('两次密码不一致', '请重新确认注册密码。', '重新填写')
      return
    }
    submitLoading.value = true
    try {
      const registerRes = await userRegister({
        userName: registerForm.userName?.trim(),
        userAccount: registerForm.userAccount?.trim(),
        userEmail: registerForm.userEmail?.trim(),
        userPassword: registerForm.userPassword,
        checkPassword: registerForm.checkPassword,
      })
      if (registerRes.data.code !== 0) {
        showActionModal('注册失败', registerRes.data.message || '请检查注册信息。', '重新填写')
        return
      }
      const loginRes = await userLogin({
        userAccount: registerForm.userAccount?.trim(),
        userPassword: registerForm.userPassword,
      })
      if (loginRes.data.code === 0 && loginRes.data.data) {
        message.success('注册成功')
        await finishLogin(loginRes.data.data)
      } else {
        passwordAction.value = 'login'
        formState.userAccount = registerForm.userAccount?.trim()
        showActionModal('注册成功', '账号已创建，请使用密码登录。')
      }
    } catch (error) {
      console.error('注册失败：', error)
      showActionModal('注册失败', '注册请求没有成功完成，请稍后重试。', '重新填写')
    } finally {
      submitLoading.value = false
    }
    return
  }
  submitLoading.value = true
  try {
    const res =
      loginMode.value === 'password'
        ? await userLogin({
            userAccount: formState.userAccount?.trim(),
            userPassword: formState.userPassword,
          })
        : await userEmailCodeLogin({
            userEmail: emailCodeForm.userEmail?.trim(),
            emailCode: emailCodeForm.emailCode?.trim(),
          })
    // 登录成功，把登录态保存到全局状态中
    if (res.data.code === 0 && res.data.data) {
      await finishLogin(res.data.data)
    } else {
      showActionModal('登录失败', res.data.message || '登录失败，请检查输入。', '重新填写')
    }
  } catch (error) {
    console.error('登录失败：', error)
    showActionModal('登录失败', '登录请求没有成功完成，请稍后重试。', '重新填写')
  } finally {
    submitLoading.value = false
  }
}

onMounted(async () => {
  if (route.query.forgot !== '1') {
    return
  }
  if (!loginUserStore.loginUser.userEmail) {
    await loginUserStore.fetchLoginUser().catch(() => undefined)
  }
  forgotPasswordForm.userEmail = loginUserStore.loginUser.userEmail || ''
  forgotPasswordOpen.value = true
})

onUnmounted(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
  if (resetCountdownTimer) {
    window.clearInterval(resetCountdownTimer)
  }
})
</script>

<style scoped>
#userLoginPage {
  min-height: calc(100vh - 128px);
  padding: 56px 24px 72px;
  background:
    linear-gradient(135deg, rgba(47, 125, 75, 0.08), transparent 38%),
    linear-gradient(180deg, #f7fbf8 0%, #eef7f0 100%);
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(280px, 0.85fr) minmax(420px, 1.15fr);
  gap: 0;
  max-width: 1040px;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid rgba(47, 125, 75, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow:
    0 24px 70px rgba(23, 58, 40, 0.16),
    0 0 0 8px rgba(255, 255, 255, 0.52);
}

.login-copy {
  min-height: 520px;
  padding: 48px 44px;
  background:
    radial-gradient(circle at 18% 18%, rgba(255, 255, 255, 0.4), transparent 34%),
    linear-gradient(145deg, #1d5b38 0%, #2f7d4b 54%, #7eb88a 100%);
  color: #fff;
}

.copy-badge {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  font-size: 13px;
  margin-bottom: 80px;
}

.login-copy h1 {
  margin: 0 0 18px;
  font-size: 42px;
  line-height: 1.12;
  font-weight: 700;
  letter-spacing: 0;
}

.login-copy p {
  max-width: 310px;
  margin: 0;
  color: rgba(255, 255, 255, 0.84);
  line-height: 1.7;
}

.copy-points {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 72px;
}

.copy-points span {
  padding: 7px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
}

.login-card {
  padding: 48px 54px;
  background: #fff;
}

.login-card-header {
  margin-bottom: 24px;
}

.title {
  margin: 0 0 8px;
  color: #173a28;
  font-size: 30px;
  font-weight: 700;
  text-align: left;
}

.desc {
  color: #7b8b80;
  margin-bottom: 0;
}

.login-mode {
  display: flex;
  width: 100%;
  padding: 4px;
  margin-bottom: 26px;
  border: 1px solid rgba(47, 125, 75, 0.12);
  border-radius: 8px;
  background: #f4f7f5;
}

.login-mode :deep(.ant-segmented-group) {
  display: flex;
  width: 100%;
}

.login-mode :deep(.ant-segmented-item) {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.login-mode :deep(.ant-segmented-item-label) {
  width: 100%;
  min-height: 38px;
  line-height: 38px;
  padding: 0 12px;
  text-align: center;
  font-weight: 600;
}

.demo-account {
  margin: -10px 0 24px;
}

.demo-account :deep(.ant-alert-description) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.password-action {
  display: flex;
  width: 100%;
  padding: 3px;
  margin: -10px 0 20px;
  border-radius: 8px;
  background: #f7faf8;
}

.password-action :deep(.ant-segmented-group) {
  display: flex;
  width: 100%;
}

.password-action :deep(.ant-segmented-item) {
  flex: 1;
  text-align: center;
}

#userLoginPage :deep(.ant-input),
#userLoginPage :deep(.ant-input-affix-wrapper),
#userLoginPage :deep(.ant-input-group-addon) {
  border-color: rgba(47, 125, 75, 0.18);
}

#userLoginPage :deep(.ant-input),
#userLoginPage :deep(.ant-input-affix-wrapper) {
  min-height: 46px;
  border-radius: 8px;
  background: #fbfdfb;
}

#userLoginPage :deep(.ant-input-group .ant-input) {
  border-radius: 8px 0 0 8px;
}

#userLoginPage :deep(.ant-input-group-addon) {
  border-radius: 0 8px 8px 0;
  background: #f7faf8;
}

.code-button {
  height: 28px;
  padding: 0 4px;
  color: #2f7d4b;
  font-weight: 600;
}

.mode-tip {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-height: 38px;
  margin: -2px 0 18px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7faf8;
  color: #6d7d72;
  font-size: 13px;
}

.mode-tip a {
  color: #2f7d4b;
  font-weight: 600;
}

.password-links {
  justify-content: space-between;
}

.password-links :deep(.ant-btn-link) {
  height: auto;
  padding: 0;
  color: #2f7d4b;
  font-size: 13px;
  font-weight: 600;
}

.agreement-item {
  margin: 0 0 16px;
}

.agreement-link {
  height: auto;
  padding: 0 2px;
  color: #2f7d4b;
}

.submit-button {
  width: 100%;
  height: 46px;
  border-radius: 8px;
  background: #2f7d4b;
  border-color: #2f7d4b;
  font-weight: 600;
}

.submit-button:hover {
  background: #25673e;
  border-color: #25673e;
}

@media (max-width: 860px) {
  #userLoginPage {
    padding: 28px 16px 52px;
  }

  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-copy {
    min-height: auto;
    padding: 30px;
  }

  .copy-badge {
    margin-bottom: 32px;
  }

  .login-copy h1 {
    font-size: 32px;
  }

  .copy-points {
    margin-top: 28px;
  }

  .login-card {
    padding: 32px 24px;
  }
}

@media (max-width: 560px) {
  .mode-tip {
    align-items: flex-start;
    justify-content: flex-start;
    flex-direction: column;
  }
}
</style>
