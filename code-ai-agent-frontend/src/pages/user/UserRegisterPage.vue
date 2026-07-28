<template>
  <div id="userRegisterPage">
    <h2 class="title">今安 AI 应用 - 用户注册</h2>
    <div class="desc">不写一行代码，生成完整应用</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item name="userName">
        <a-input v-model:value="formState.userName" placeholder="请输入昵称（可选）" :maxlength="30" />
      </a-form-item>
      <a-form-item
        name="userEmail"
        :rules="[
          { required: true, message: '请输入邮箱' },
          { type: 'email', message: '邮箱格式不正确' },
        ]"
      >
        <a-input v-model:value="formState.userEmail" placeholder="请输入邮箱" />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请确认密码' },
          { min: 8, message: '密码不能小于 8 位' },
          { validator: validateCheckPassword },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请确认密码" />
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item class="agreement-item">
        <a-checkbox v-model:checked="agreementChecked">
          我已阅读并同意
          <a-button type="link" class="agreement-link" @click.prevent="showAgreement">
            用户协议与隐私说明
          </a-button>
        </a-checkbox>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%" :loading="submitLoading">
          注册
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message, Modal } from 'ant-design-vue'
import { reactive, ref } from 'vue'

const router = useRouter()
const submitLoading = ref(false)
const agreementChecked = ref(false)

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userName: '',
  userEmail: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const showAgreement = () => {
  Modal.info({
    title: '用户协议与隐私说明',
    content:
      '你需要提供真实可访问的邮箱完成账号创建。平台会保存账号、邮箱、应用作品和必要登录状态，用于提供登录、应用生成和个人主页功能。请勿上传违法、侵权或包含敏感信息的内容。',
    centered: true,
    okText: '我知道了',
  })
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  if (!agreementChecked.value) {
    Modal.confirm({
      title: '请先同意用户协议',
      content: '注册账号前需要阅读并同意用户协议与隐私说明。',
      centered: true,
      okText: '确认',
      cancelText: '取消',
    })
    return
  }
  submitLoading.value = true
  try {
    const res = await userRegister(values)
    // 注册成功，跳转到登录页面
    if (res.data.code === 0) {
      message.success('注册成功')
      await router.push({
        path: '/user/login',
        replace: true,
      })
    } else {
      Modal.confirm({
        title: '注册失败',
        content: res.data.message || '注册失败，请检查输入。',
        centered: true,
        okText: '重新填写',
        cancelText: '取消',
      })
    }
  } catch (error) {
    console.error('注册失败：', error)
    Modal.confirm({
      title: '注册失败',
      content: '注册请求没有成功完成，请稍后重试。',
      centered: true,
      okText: '重新填写',
      cancelText: '取消',
    })
  } finally {
    submitLoading.value = false
  }
}
</script>

<style scoped>
#userRegisterPage {
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

.tips {
  margin-bottom: 16px;
  color: #bbb;
  font-size: 13px;
  text-align: right;
}

.agreement-item {
  margin-bottom: 16px;
}

.agreement-link {
  height: auto;
  padding: 0 2px;
}
</style>
