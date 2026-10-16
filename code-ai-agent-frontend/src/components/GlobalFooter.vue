<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="copyright">
        <a
          href="https://github.com/Li-Jinan"
          target="_blank"
          rel="noopener noreferrer"
          class="author-link"
        >
          今安 AI 应用开发平台
        </a>
      </p>
      <p class="filing">
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="filing-link"
        >
          豫ICP备2026047753号
        </a>
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  text-align: center;
  padding: 20px;
  margin-top: 40px;
  border-top: 1px solid rgba(102, 126, 234, 0.1);
}

.copyright {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.filing {
  margin: 8px 0 0;
  font-size: 13px;
}

.filing-link {
  color: #888;
}

.filing-link:hover {
  color: #667eea;
}
</style>
