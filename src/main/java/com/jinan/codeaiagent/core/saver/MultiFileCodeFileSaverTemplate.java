package com.jinan.codeaiagent.core.saver;

import cn.hutool.core.util.StrUtil;
import com.jinan.codeaiagent.ai.model.MultiFileCodeResult;
import com.jinan.codeaiagent.exception.BusinessException;
import com.jinan.codeaiagent.exception.ErrorCode;
import com.jinan.codeaiagent.model.enums.CodeGenTypeEnum;

/**
 * 多文件代码保存器
 *
 * @author jinan
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        String cssCode = StrUtil.blankToDefault(result.getCssCode(), buildFallbackCss());
        String jsCode = StrUtil.blankToDefault(result.getJsCode(), buildFallbackJs());
        // 保存 HTML 文件
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        // 保存 CSS 文件
        writeToFile(baseDirPath, "style.css", cssCode);
        // 保存 JavaScript 文件
        writeToFile(baseDirPath, "script.js", jsCode);
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        // 至少要有 HTML 代码，CSS 和 JS 可以为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }

    private String buildFallbackCss() {
        return """
                *, *::before, *::after {
                  box-sizing: border-box;
                }

                html {
                  scroll-behavior: smooth;
                }

                body {
                  margin: 0;
                  color: #18231f;
                  background:
                    radial-gradient(circle at top left, rgba(87, 129, 100, 0.16), transparent 32rem),
                    linear-gradient(180deg, #f7f5ef 0%, #eef4ee 100%);
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                  line-height: 1.65;
                }

                a {
                  color: inherit;
                  text-decoration: none;
                }

                img {
                  display: block;
                  max-width: 100%;
                  object-fit: cover;
                }

                .container {
                  width: min(1120px, calc(100% - 40px));
                  margin: 0 auto;
                }

                .site-header {
                  position: sticky;
                  top: 0;
                  z-index: 20;
                  border-bottom: 1px solid rgba(25, 49, 38, 0.08);
                  background: rgba(255, 255, 255, 0.82);
                  backdrop-filter: blur(18px);
                }

                .header-inner {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  min-height: 76px;
                  gap: 24px;
                }

                .logo {
                  margin: 0;
                  font-size: clamp(1.35rem, 2vw, 2rem);
                  font-weight: 800;
                  letter-spacing: 0;
                }

                .main-nav ul {
                  display: flex;
                  align-items: center;
                  gap: 8px;
                  margin: 0;
                  padding: 0;
                  list-style: none;
                }

                .main-nav a,
                .btn,
                button {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  min-height: 40px;
                  padding: 0 18px;
                  border: 0;
                  border-radius: 8px;
                  background: rgba(46, 95, 65, 0.08);
                  color: #244a32;
                  font-weight: 700;
                  cursor: pointer;
                  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
                }

                .main-nav a:hover,
                .main-nav a.active,
                .btn,
                button:hover {
                  background: #2f6d47;
                  color: #fff;
                  box-shadow: 0 10px 22px rgba(47, 109, 71, 0.18);
                  transform: translateY(-1px);
                }

                .menu-toggle {
                  display: none;
                }

                section {
                  padding: 84px 0;
                }

                .hero-section {
                  padding-top: 96px;
                }

                .hero-grid,
                .resume-grid,
                .contact-layout,
                .project-card {
                  display: grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  gap: 36px;
                  align-items: center;
                }

                .hero-text h2,
                .section-title,
                .contact-info h2,
                .resume-left h2 {
                  margin: 0 0 18px;
                  font-size: clamp(2.2rem, 5vw, 4.8rem);
                  line-height: 1.05;
                  letter-spacing: 0;
                }

                .hero-text p,
                .project-info p,
                .resume-intro,
                .contact-info p {
                  margin: 0 0 24px;
                  color: #59685f;
                  font-size: 1.05rem;
                }

                .hero-image,
                .project-media,
                .resume-right,
                .contact-form,
                .mini-item {
                  overflow: hidden;
                  border: 1px solid rgba(25, 49, 38, 0.1);
                  border-radius: 8px;
                  background: rgba(255, 255, 255, 0.78);
                  box-shadow: 0 22px 55px rgba(34, 58, 44, 0.12);
                }

                .hero-image img,
                .project-media img,
                .resume-right img {
                  width: 100%;
                  height: min(58vw, 520px);
                }

                .section-title {
                  font-size: clamp(1.8rem, 3vw, 3rem);
                }

                .filter-bar {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 10px;
                  margin: 22px 0 30px;
                }

                .gallery-grid,
                .project-mini-list {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
                  gap: 20px;
                }

                .gallery-grid > *,
                .project-mini-list > *,
                .project-info,
                .resume-left,
                .contact-info {
                  padding: 28px;
                  border: 1px solid rgba(25, 49, 38, 0.1);
                  border-radius: 8px;
                  background: rgba(255, 255, 255, 0.78);
                  box-shadow: 0 16px 38px rgba(34, 58, 44, 0.08);
                }

                ul {
                  padding-left: 1.2rem;
                }

                input,
                textarea {
                  width: 100%;
                  margin-bottom: 14px;
                  padding: 13px 14px;
                  border: 1px solid rgba(25, 49, 38, 0.16);
                  border-radius: 8px;
                  background: rgba(255, 255, 255, 0.92);
                  color: #18231f;
                  font: inherit;
                }

                .site-footer {
                  padding: 28px 0;
                  border-top: 1px solid rgba(25, 49, 38, 0.08);
                  background: rgba(255, 255, 255, 0.7);
                }

                .footer-inner {
                  display: flex;
                  justify-content: space-between;
                  gap: 20px;
                }

                @media (max-width: 760px) {
                  .hero-grid,
                  .resume-grid,
                  .contact-layout,
                  .project-card {
                    grid-template-columns: 1fr;
                  }

                  .main-nav {
                    display: none;
                  }

                  .menu-toggle {
                    display: inline-flex;
                  }

                  section {
                    padding: 52px 0;
                  }

                  .footer-inner {
                    flex-direction: column;
                  }
                }
                """;
    }

    private String buildFallbackJs() {
        return """
                document.querySelectorAll('a[href^="#"]').forEach((link) => {
                  link.addEventListener('click', (event) => {
                    const target = document.querySelector(link.getAttribute('href'));
                    if (target) {
                      event.preventDefault();
                      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    }
                  });
                });
                """;
    }
}
