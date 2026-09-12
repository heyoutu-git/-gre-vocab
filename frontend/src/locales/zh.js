// 中文（默认）
export default {
  app: { title: 'GRE 词汇知识分享' },
  nav: { home: '首页', lessons: '课时目录', mine: '我的', guide: '录制指引', admin: '后台管理', users: '用户管理', logout: '退出', theme: '切换主题', lang: '语言 / Language' },
  login: {
    title: '登录', register: '注册', username: '用户名', password: '密码', captcha: '验证码',
    phone: '手机号（必填，用于审核联系）', nickname: '昵称（可选）',
    agree: '我已阅读并同意', terms: '《隐私条款》', btnLogin: '登录', btnRegister: '注册',
    needUsername: '请输入用户名和密码', needTerms: '请先同意隐私条款', badPhone: '请输入正确的手机号码',
    needCaptcha: '请输入验证码', captchaRefresh: '点击刷新', okLogin: '登录成功',
    privacyText: '我们使用账号信息提供学习服务，不会向第三方泄露您的个人数据。注册即表示同意我们按此方式处理您的信息。',
    okRegister: '注册成功，等待管理员审核通过后即可登录', reviewTip: '注册后需管理员审核通过才能登录',
    devTip: '默认管理员账号：admin / admin123（仅开发期）'
  },
  lesson: {
    back: '← 课时目录', search: '搜索单词 / 释义', engine: '发音引擎', auto: '自动',
    hideKnown: '隐藏已掌握', showAll: '显示全部', hideShort: '隐藏', allShort: '全部',
    speakWords: '🔊 跟读单词', speakPage: '🔊 跟读本页', wordsShort: '🔊 单词', pageShort: '🔊 本页',
    autoScroll: '自动滚屏', markDone: '✓ 完成本课', done: '✓ 已完成', doneTip: '已记录学习进度', loopNone: '不循环', loopLesson: '本课循环', loopBook: '本书循环',
    paused: '⏸ 已暂停', playing: '🔊 朗读中', resume: '▶ 继续', pause: '⏸ 暂停', restart: '↺ 重新开始', stop: '⏹ 停止',
    readWord: '读单词', readExample: '读例句', known: '✓ 已掌握', markKnown: '标记掌握', unfavorite: '取消收藏', favorite: '收藏',
    offlineVoice: '离线真人音', serverVoice: '服务器真人音', espeak: 'eSpeak(机械音)', sysVoice: '系统语音', tencentVoice: '腾讯云真人音',
    kokoroTip: '⏳ Kokoro 离线真人语音包加载中 {pct}%（首次约 92MB，仅一次；合成需数秒，请稍候）',
    noSupport: '当前浏览器不支持语音朗读（SpeechSynthesis），请使用 Chrome / Edge / Safari。',
    noSupportShort: '当前浏览器不支持语音朗读，请使用 Chrome / Edge / Safari',
    noWords: '无词汇', noReading: '暂无阅读内容'
  },
  lessons: { title: '课时目录', lessons: '课时', words: '词', continue: '继续学习', searchTitle: '搜索课时标题', upload: '📥 上传我的书本', uploadLogin: '登录后上传', progressTotal: '总词数', progressLearned: '已学', progressRemain: '剩余',
    pickBook: '选择书本', pickBookPh: '请选择书本', noLessons: '本书暂无课时' },
  home: { title: '首页', welcome: '欢迎回来', start: '开始学习 →',
    mTitle: 'GRE 词汇', mSub: '按课时拆分，点一下就能听发音。手机离线也能读。',
    statLessons: '课时', statWords: '词汇量', statTts: '语音朗读', noBooks: '暂无书本', tapStart: '点击开始学习' },
  mine: {
    title: '我的', profile: '个人中心', progress: '学习进度', known: '已掌握', fav: '收藏', doneLessons: '已完成课时',
    engine: '发音引擎', voiceMode: '语音合成方式', useDesktop: '使用电脑版', useDesktopTip: '切换到完整桌面界面',
    voiceFollow: '跟随设备（推荐）', voiceOffline: '离线优先（首次下载语音包）', voiceServer: '服务器优先（需在线）',
    logout: '退出登录', login: '登录 / 注册', roleAdmin: '管理员', roleStudent: '学生',
    notLogin: '未登录', loginToSync: '登录后同步学习进度',
    engineTitle: '发音引擎', voiceModeTitle: 'Kokoro 语音合成方式',
    tip: '🔊 朗读支持系统语音 / 离线 eSpeak / 腾讯云真人音 / Kokoro 神经网络。Kokoro 为中文之外的英文单词提供高质量离线发音（首次需下载模型）。'
  },
  mobile: { library: '书库', study: '学习', me: '我的', back: '返回', words: '跟读单词', page: '跟读本页', showZh: '显示中文', hideZh: '隐藏中文', speakAll: '🔊 跟读全文', loading: '加载中', pendingTip: '{n} 个新用户待审核，点击处理' },
  users: {
    title: '用户管理', allStatus: '全部状态', pending: '待审核', active: '正常', disabled: '已禁用', rejected: '已拒绝',
    search: '搜索用户名/昵称/手机号', id: 'ID', username: '用户名', nickname: '昵称', phone: '手机号',
    regAddr: '注册 IP:端口', regTime: '注册时间', status: '状态', action: '操作',
    approve: '通过审核', reject: '拒绝', disable: '禁用', enable: '启用', confirm: '确认', confirmMsg: '确认对用户「{name}」执行「{action}」？', ok: '操作成功',
    noReg: '—', empty: '暂无用户'
  },
  common: { mobile: '📱 手机版', desktop: '💻 电脑版' }
}
