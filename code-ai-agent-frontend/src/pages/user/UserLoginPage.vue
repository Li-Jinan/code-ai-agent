<template>
  <div id="userLoginPage">
    <h2 class="title">今安 AI 应用 - 用户登录</h2>
    <div class="desc">不写一行代码，生成完整应用</div>
    <a-segmented
      v-model:value="loginMode"
      class="login-mode"
      :options="[
        { label: '密码登录', value: 'password' },
        { label: '邮箱登录/注册', value: 'emailCode' },
      ]"
    />
    <a-form :model="loginFormModel" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item
        v-if="loginMode === 'password'"
        name="userAccount"
        :rules="[{ required: true, message: '请输入账号或邮箱' }]"
      >
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号或邮箱" />
      </a-form-item>
      <a-form-item
        v-if="loginMode === 'password'"
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item
        v-if="loginMode === 'emailCode'"
        name="userEmail"
        :rules="[
          { required: true, message: '请输入邮箱' },
          { type: 'email', message: '邮箱格式不正确' },
        ]"
      >
        <a-input v-model:value="emailCodeForm.userEmail" placeholder="请输入邮箱" />
      </a-form-item>
      <a-form-item
        v-if="loginMode === 'emailCode'"
        name="emailCode"
        :rules="[{ required: true, message: '请输入验证码' }]"
      >
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
      <div class="tips">
        密码登录需要先注册账号，邮箱验证码可直接登录或注册
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
      <a-form-item v-if="loginMode === 'emailCode'" class="agreement-item">
        <a-checkbox v-model:checked="agreementChecked">
          我已阅读并同意
          <a-button type="link" class="agreement-link" @click.prevent="showAgreement">
            用户协议与隐私说明
          </a-button>
        </a-checkbox>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%" :loading="submitLoading">
          {{ loginMode === 'emailCode' ? '登录 / 注册' : '登录' }}
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>
<script lang="ts" setup>
import { computed, onUnmounted, reactive, ref } from 'vue'
import { sendEmailLoginCode, userEmailCodeLogin, userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'

const loginMode = ref<'password' | 'emailCode'>('password')
const sendingCode = ref(false)
const submitLoading = ref(false)
const agreementChecked = ref(false)
const countdown = ref(0)
let countdownTimer: number | undefined

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const emailCodeForm = reactive<API.UserEmailLoginRequest>({
  userEmail: '',
  emailCode: '',
})

const loginFormModel = computed(() => (loginMode.value === 'password' ? formState : emailCodeForm))

const router = useRouter()
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
    const res = await sendEmailLoginCode({ userEmail, verifyScene: '登录或注册账号' })
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

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async () => {
  if (submitLoading.value) {
    return
  }
  if (loginMode.value === 'emailCode' && !agreementChecked.value) {
    showActionModal('请先同意用户协议', '邮箱验证码登录会在账号不存在时自动注册，请先阅读并同意用户协议与隐私说明。')
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

onUnmounted(() => {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
#userLoginPage {
  background: white;
  max-width: 720px;
  padding: 24px;
  margin: 24px auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
}

.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.login-mode {
  display: flex;
  width: 100%;
  padding: 4px;
  margin-bottom: 16px;
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
  padding: 0 12px;
  text-align: center;
  font-weight: 500;
}

.code-button {
  height: 22px;
  padding: 0;
}

.tips {
  text-align: right;
  color: #bbb;
  font-size: 13px;
  margin-bottom: 16px;
}

.agreement-item {
  margin-bottom: 16px;
}

.agreement-link {
  height: auto;
  padding: 0 2px;
}
</style>
