import http from './http'

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  captcha: () => http.get('/auth/captcha'),
  me: () => http.get('/auth/me')
}

// 公开课时节（学习端，公开可读）
export const lessonApi = {
  list: (bookId) => http.get('/lessons', { params: bookId ? { bookId } : {} }),
  get: (id) => http.get(`/lessons/${id}`),
  vocabularies: (id) => http.get(`/lessons/${id}/vocabularies`),
  passage: (id) => http.get(`/lessons/${id}/passage`),
  books: () => http.get('/lessons/books')
}

// 后台管理（需管理员权限）
export const adminApi = {
  // 书本
  listBooks: () => http.get('/admin/books'),
  saveBook: (data) => http.post('/admin/books', data),
  removeBook: (id) => http.delete(`/admin/books/${id}`),

  // 用户搜索（后台管理选择书本所有者）
  searchUsers: (keyword) => http.get('/admin/users/search', { params: { keyword } }),

  // 用户管理（注册审核/启停）
  listUsers: (keyword, status) => http.get('/admin/users', { params: { keyword: keyword || undefined, status } }),
  setUserStatus: (id, status) => http.put(`/admin/users/${id}/status`, null, { params: { status } }),

  // 课时
  listLessons: (bookId) => http.get('/admin/lessons', { params: bookId ? { bookId } : {} }),
  saveLesson: (data) => http.post('/admin/lessons', data),
  removeLesson: (id) => http.delete(`/admin/lessons/${id}`),
  assignLessonBook: (id, bookId) => http.post(`/admin/lessons/${id}/assign-book`, null, { params: { bookId } }),

  // 词汇
  saveVocab: (data) => http.post('/admin/vocabularies', data),
  removeVocab: (id) => http.delete(`/admin/vocabularies/${id}`),
  reassignVocab: (id, lessonId, sortNo) =>
    http.post(`/admin/vocabularies/${id}/reassign`, null, { params: { lessonId, sortNo } }),

  // PDF 识别 + 批量导入（OCR 兜底较慢，放宽到 10 分钟）
  parsePdf: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/admin/parse-pdf', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000
    })
  },
  importVocabularies: (batchSize, bookId, vocabularies) =>
    http.post('/admin/import', { batchSize, bookId, vocabularies }),
  importReading: (bookId, segments) =>
    http.post('/admin/import-reading', { bookId, segments }),

  // 阅读篇章人工对齐：读取 / 保存
  getAlignment: (lessonId) => http.get(`/admin/lessons/${lessonId}/alignment`),
  saveAlignment: (lessonId, rows) => http.post(`/admin/lessons/${lessonId}/alignment`, { rows }),

  // TTS 引擎开关 + 腾讯云账号
  listTtsEngines: () => http.get('/admin/tts/engines'),
  setTtsEngineEnabled: (id, enabled) => http.post(`/admin/tts/engines/${id}/enabled`, null, { params: { enabled } }),
  listTtsProviders: () => http.get('/admin/tts/providers'),
  saveTtsProvider: (data) => http.post('/admin/tts/providers', data),
  removeTtsProvider: (id) => http.delete(`/admin/tts/providers/${id}`),
  getTtsUsage: (provider) => http.get(`/admin/tts/usage/${provider}`),
  setTtsQuota: (provider, quota) => http.post(`/admin/tts/usage/${provider}/quota`, null, { params: { quota } })
}

// TTS（学习端）
export const ttsApi = {
  engines: () => http.get('/tts/engines'),
  tencentSynthesize: (text) => http.post('/tts/tencent/synthesize', { text }),
  // 服务器端合成整句可能 10~30 秒（推理 + 公网往返），必须单独放宽超时，
  // 否则被全局 15s 掐断后会静默回退浏览器端推理（触发 92MB 语音包下载）
  kokoroSynthesize: (text, speed, voice) => http.post('/tts/kokoro/synthesize', { text, speed, voice }, { timeout: 60000 })
}

// 书本（用户级：列表 / 计划 / 上传导入 / 设置 / 重切分 / 删除）
export const bookApi = {
  list: () => http.get('/books'),
  get: (id) => http.get(`/books/${id}`),
  plan: (id) => http.get(`/books/${id}/plan`),
  importBook: (file, { bookTitle, bookId, wordsPerLesson } = {}) => {
    const fd = new FormData()
    fd.append('file', file)
    if (bookTitle) fd.append('bookTitle', bookTitle)
    if (bookId) fd.append('bookId', bookId)
    if (wordsPerLesson != null) fd.append('wordsPerLesson', wordsPerLesson)
    // 扫描版/文本层损坏的 PDF 会走 OCR 兜底（可达数分钟），放宽到 10 分钟
    return http.post('/books/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000
    })
  },
  updatePlan: (id, data) => http.put(`/books/${id}`, data),
  resplit: (id, wordsPerLesson) => http.post(`/books/${id}/resplit`, null, { params: { wordsPerLesson } }),
  remove: (id) => http.delete(`/books/${id}`)
}

// 学习进度（课时完成标记，存服务端，电脑/手机跨端同步）
export const learningApi = {
  markProgress: (lessonId) => http.post('/learning/progress', { lessonId }),
  getProgress: (lessonId) => http.get('/learning/progress', { params: { lessonId } }),
  progressCount: () => http.get('/learning/progress/count'),
  // 自动进度：节流上报学习位置
  visit: (lessonId, wordIndex) => http.post('/learning/progress/visit', { lessonId, wordIndex }),
  // 最近学的未完成一课（供「继续学习」）
  resume: () => http.get('/learning/progress/resume'),
  // 单课时进度明细（含 lastWordIndex，供进入页面「继续上次」提示）
  progressDetail: (lessonId) => http.get('/learning/progress/detail', { params: { lessonId } })
}
