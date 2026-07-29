<template>
  <div
    class="app-card"
    :class="{ 'app-card--featured': featured }"
    role="button"
    tabindex="0"
    @click="handleCardClick"
    @keydown.enter="handleCardClick"
  >
    <div class="app-preview">
      <img v-if="app.cover" :src="app.cover" :alt="app.appName" />
      <div v-else class="app-placeholder">
        <span>🤖</span>
      </div>
      <div class="app-overlay">
        <a-space>
          <a-button v-if="showChat" type="primary" @click.stop="handleViewChat">查看对话</a-button>
          <a-button v-if="app.deployKey" type="default" @click.stop="handleViewWork">查看作品</a-button>
        </a-space>
      </div>
    </div>
    <div class="app-info">
      <div class="app-info-left">
        <a-avatar :src="getAvatarUrl(app.user?.userAvatar)" :size="40">
          {{ app.user?.userName?.charAt(0) || 'U' }}
        </a-avatar>
      </div>
      <div class="app-info-right">
        <h3 class="app-title">{{ app.appName || '未命名应用' }}</h3>
        <p class="app-author">
          {{ app.user?.userName || (featured ? '官方' : '未知用户') }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getAvatarUrl } from '@/utils/avatar'

interface Props {
  app: API.AppVO
  featured?: boolean
  showChat?: boolean
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
  showChat: true,
})

const emit = defineEmits<Emits>()

const handleCardClick = () => {
  if (props.app.deployKey) {
    emit('view-work', props.app)
    return
  }
  emit('view-chat', props.app.id)
}

const handleViewChat = () => {
  emit('view-chat', props.app.id)
}

const handleViewWork = () => {
  emit('view-work', props.app)
}
</script>

<style scoped>
.app-card {
  background: rgba(255, 255, 255, 0.96);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(23, 58, 40, 0.1);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(47, 125, 75, 0.12);
  transition:
    transform 0.2s,
    box-shadow 0.2s,
    border-color 0.2s;
  cursor: pointer;
}

.app-card:hover {
  transform: translateY(-4px);
  border-color: rgba(47, 125, 75, 0.26);
  box-shadow: 0 18px 42px rgba(23, 58, 40, 0.16);
}

.app-preview {
  height: 180px;
  background: #edf6ef;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.app-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-placeholder {
  font-size: 48px;
  color: #8fb99a;
}

.app-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(23, 58, 40, 0.58);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.app-overlay :deep(.ant-btn-primary) {
  background: #2f7d4b;
  border-color: #2f7d4b;
}

.app-overlay :deep(.ant-btn-primary:hover) {
  background: #25673e;
  border-color: #25673e;
}

.app-card:hover .app-overlay {
  opacity: 1;
}

.app-info {
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-top: 1px solid rgba(47, 125, 75, 0.08);
}

.app-info-left {
  flex-shrink: 0;
}

.app-info-right {
  flex: 1;
  min-width: 0;
}

.app-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px;
  color: #173a28;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-author {
  font-size: 14px;
  color: #66786c;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
