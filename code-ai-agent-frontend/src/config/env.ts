/**
 * 环境变量配置
 */
import {CodeGenTypeEnum} from "@/utils/codeGenTypes.ts";

// AI 生成站点使用独立来源，生产环境通过 VITE_DEPLOY_DOMAIN 显式配置。
// 兼容曾经下发过的 http://host:8080 配置：HTTPS 主站不能继续把用户带到
// 明文且通常不对公网开放的 8080 端口。
const resolveDeployDomain = () => {
  const configuredDomain =
    window.__APP_RUNTIME_CONFIG__?.deployBaseUrl ||
    import.meta.env.VITE_DEPLOY_DOMAIN ||
    window.location.origin

  try {
    const deployUrl = new URL(configuredDomain, window.location.origin)
    if (window.location.protocol === 'https:' && deployUrl.protocol === 'http:') {
      deployUrl.protocol = 'https:'
      if (deployUrl.port === '8080') {
        deployUrl.port = ''
      }
    }
    return deployUrl.toString().replace(/\/$/, '')
  } catch {
    return configuredDomain.replace(/\/$/, '')
  }
}

export const DEPLOY_DOMAIN = resolveDeployDomain()

// API 基础地址
export const API_BASE_URL =
  window.__APP_RUNTIME_CONFIG__?.apiBaseUrl ||
  import.meta.env.VITE_API_BASE_URL ||
  'http://localhost:8123/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${encodeURIComponent(deployKey)}/`
}

// 后端部署接口也会返回完整 URL。统一经过同一层兼容处理，避免旧的
// http://host:8080 配置再次通过部署成功弹窗暴露给用户。
export const normalizeDeployUrl = (deployUrl: string) => {
  try {
    const url = new URL(deployUrl, DEPLOY_DOMAIN)
    if (window.location.protocol === 'https:' && url.protocol === 'http:') {
      url.protocol = 'https:'
      if (url.port === '8080') {
        url.port = ''
      }
    }
    return url.toString()
  } catch {
    return deployUrl
  }
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
