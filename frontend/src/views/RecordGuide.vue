<template>
  <div class="guide">
    <h1>🎬 录制跟读视频（OBS 方案）</h1>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:18px"
      title="为什么用 OBS 而不是浏览器自带录制？"
      description="浏览器内置录屏（getDisplayMedia）需要 HTTPS 安全上下文，且无法捕获系统语音（Web Speech）朗读声。OBS Studio 是免费开源的桌面录屏工具，能同时录制屏幕画面与系统声音（含 GREStudy 的 TTS 朗读），产出即带语音的 MP4，最契合本场景。" />

    <div class="step">
      <div class="step-no">1</div>
      <div class="step-body">
        <h3>下载并安装 OBS Studio</h3>
        <p>前往官网 <a href="https://obsproject.com" target="_blank" rel="noopener">obsproject.com</a> 下载安装包（开源仓库：<a href="https://github.com/obsproject/obs-studio" target="_blank" rel="noopener">github.com/obsproject/obs-studio</a>）。安装后首次启动会弹出「自动配置向导」，选择「我只用于录制」即可。</p>
      </div>
    </div>

    <div class="step">
      <div class="step-no">2</div>
      <div class="step-body">
        <h3>添加「显示器捕获」源</h3>
        <p>在「来源」面板点击 <b>+ → 显示器捕获</b>，新建后选择显示 GREStudy 页面的那块屏幕。建议录制前把其他窗口最小化，只保留要录的课时 / 阅读页，避免录进无关内容。若只想录 GREStudy 一个窗口、不录整块桌面，请直接看下方专节👇。</p>
      </div>
    </div>

    <div class="step feature">
      <div class="step-no">★</div>
      <div class="step-body">
        <h3>只录 GREStudy 窗口（不录整个桌面）</h3>
        <p>不想把桌面其它内容录进去？用 <b>窗口捕获</b> 代替「显示器捕获」即可，它只框定 GREStudy 这一个窗口：</p>
        <ol class="ol">
          <li>在「来源」点 <b>+ → 窗口捕获</b>，新建一个来源。</li>
          <li>在弹出的窗口列表里选 GREStudy 所在的浏览器窗口（如 <b>Chrome / Edge — GREStudy</b>）。</li>
          <li>窗口捕获会<b>直接抓取该窗口画面</b>：即使它被别的窗口挡住，也只录 GREStudy，不会录到桌面其它内容。</li>
          <li>想更聚焦：在来源「属性」里勾选 <b>裁剪到边界</b> 或手动拖拽边缘裁掉多余区域；勾选「捕获鼠标光标」可决定是否录进鼠标。</li>
          <li>对照演示短片：<a href="/gre-vocab/obs-window-demo.html" target="_blank" rel="noopener">🎬 只看窗口怎么录（演示）</a>。</li>
        </ol>
        <el-alert type="warning" :closable="false" style="margin-top:8px"
          title="窗口捕获 vs 显示器捕获"
          description="窗口捕获只框定 GREStudy 一个窗口；显示器捕获录的是整块屏幕。录跟读视频推荐用「窗口捕获」，画面最干净。" />
      </div>
    </div>

    <div class="step">
      <div class="step-no">3</div>
      <div class="step-body">
        <h3>确认能录到声音（关键）</h3>
        <p>OBS 默认会在「混音器」里捕获 <b>桌面音频</b>（系统声音）。在 GREStudy 点一次「🔊 跟读本页」试听，若 OBS 混音器的「桌面音频」电平条随朗读跳动，说明声音已能被录进去。</p>
        <ul>
          <li>若电平条不动：检查「桌面音频」是否被静音；或在「来源」里加 <b>应用音频输出捕获</b>，单独选 Chrome / Edge 浏览器。</li>
          <li>只想录人声、不要背景杂音：可在「混音器 → 齿轮 → 高级音频属性」把对应音源调成「仅监听」，或加「噪声抑制 / 噪声门限」滤镜。</li>
        </ul>
        <el-alert type="warning" :closable="false" style="margin-top:8px"
          title="提示：tencent / meSpeak / 系统语音都能被 OBS 录到"
          description="无论 GREStudy 用的是腾讯云 TTS、meSpeak 还是系统语音，只要声音从系统扬声器或耳机出来，OBS 都能录进去——这是 OBS 相对浏览器录制的最大优势。" />
      </div>
    </div>

    <div class="step">
      <div class="step-no">4</div>
      <div class="step-body">
        <h3>设置输出为 MP4</h3>
        <p>打开 <b>设置 → 输出 → 录制</b>：录制格式选 <b>mp4</b>（兼容性最好），视频编码器选 <b>硬件（H.264 / HEVC）</b> 更省 CPU。录制路径默认在「视频」文件夹，可在「录像路径」里修改。</p>
      </div>
    </div>

    <div class="step">
      <div class="step-no">5</div>
      <div class="step-body">
        <h3>开始录制</h3>
        <p>在 GREStudy 打开放置要录的课时或阅读页，点 <b>🔊 跟读本页 / 跟读全文</b> 开始朗读；同时在 OBS 点右下角 <b>开始录制</b>。用「窗口捕获」时，跟着高亮句自动滚屏一起录即可，画面只含 GREStudy。</p>
      </div>
    </div>

    <div class="step">
      <div class="step-no">6</div>
      <div class="step-body">
        <h3>停止并找到文件</h3>
        <p>录完后，先在 GREStudy 点 <b>⏹ 停止</b> 结束朗读，再在 OBS 点 <b>停止录制</b>。文件可在 OBS 菜单 <b>文件 → 显示录像</b> 直接打开所在文件夹。后续可上传网盘或导入剪映做二次剪辑。</p>
      </div>
    </div>

    <el-divider />
    <p class="demo-link">想看实际效果？<a href="/gre-vocab/obs-window-demo.html" target="_blank" rel="noopener">打开「仅录 GREStudy 窗口」演示短片 →</a></p>

    <el-alert type="success" :closable="false" show-icon style="margin-top:18px"
      title="完成"
      description="至此你已得到一段只含 GREStudy 跟读画面、并带语音的 MP4 视频，可分享或存档。" />
  </div>
</template>

<script setup>
// 纯静态指引页，无需逻辑
</script>

<style scoped>
.guide { max-width: 880px; margin: 0 auto; padding: 8px 4px 40px; }
.guide h1 { font-size: 22px; margin: 4px 0 18px; color: var(--gre-text); }
.step { display: flex; gap: 14px; padding: 14px 0; border-bottom: 1px dashed var(--gre-border); }
.step-no { flex: 0 0 30px; height: 30px; border-radius: 50%; background: var(--gre-primary); color: #fff; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.step.feature .step-no { background: #e6a23c; }
.step-body { flex: 1; }
.step-body h3 { margin: 2px 0 8px; font-size: 16px; color: var(--gre-text); }
.step-body p { margin: 0 0 8px; line-height: 1.7; color: var(--gre-text-soft); }
.step-body ul { margin: 0 0 8px; padding-left: 20px; color: var(--gre-text-soft); line-height: 1.7; }
.step-body ol.ol { margin: 0 0 8px; padding-left: 22px; color: var(--gre-text-soft); line-height: 1.8; }
.step-body a { color: var(--gre-primary); }
.demo-link { text-align: center; font-size: 15px; color: var(--gre-text-soft); }
.demo-link a { color: var(--gre-primary); font-weight: 600; }
</style>
