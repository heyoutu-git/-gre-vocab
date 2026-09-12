// English
export default {
  app: { title: 'GRE Vocabulary Sharing' },
  nav: { home: 'Home', lessons: 'Lessons', mine: 'Me', guide: 'Recording Guide', admin: 'Admin', users: 'Users', logout: 'Log out', theme: 'Toggle theme', lang: 'Language' },
  login: {
    title: 'Sign in', register: 'Sign up', username: 'Username', password: 'Password', captcha: 'Captcha',
    phone: 'Phone (required, for approval contact)', nickname: 'Nickname (optional)',
    agree: 'I have read and agree to the ', terms: 'Privacy Policy',
    btnLogin: 'Sign in', btnRegister: 'Sign up',
    needUsername: 'Please enter username and password', needTerms: 'Please agree to the Privacy Policy first', badPhone: 'Please enter a valid phone number',
    needCaptcha: 'Please enter the captcha', captchaRefresh: 'Tap to refresh', okLogin: 'Signed in',
    privacyText: 'We use your account information to provide the learning service and never share your personal data with third parties. By registering you agree to this processing.',
    okRegister: 'Registered. You can sign in after admin approval.', reviewTip: 'New accounts require admin approval before signing in',
    devTip: 'Default admin: admin / admin123 (dev only)'
  },
  lesson: {
    back: '← Lesson list', search: 'Search word / definition', engine: 'Voice', auto: 'Auto',
    hideKnown: 'Hide learned', showAll: 'Show all', hideShort: 'Hide', allShort: 'All',
    speakWords: '🔊 Read words', speakPage: '🔊 Read page', wordsShort: '🔊 Words', pageShort: '🔊 Page',
    autoScroll: 'Auto-scroll', markDone: '✓ Mark done', done: '✓ Done', doneTip: 'Progress saved', loopNone: 'No loop', loopLesson: 'Loop lesson', loopBook: 'Loop book',
    paused: '⏸ Paused', playing: '🔊 Playing', resume: '▶ Resume', pause: '⏸ Pause', restart: '↺ Restart', stop: '⏹ Stop',
    readWord: 'Word', readExample: 'Example', known: '✓ Learned', markKnown: 'Mark learned', unfavorite: 'Unfavorite', favorite: 'Favorite',
    offlineVoice: 'Offline neural voice', serverVoice: 'Server neural voice', espeak: 'eSpeak (robotic)', sysVoice: 'System voice', tencentVoice: 'Tencent cloud voice',
    kokoroTip: '⏳ Loading Kokoro offline voice pack {pct}% (~92MB, one time; synthesis takes seconds)',
    noSupport: 'SpeechSynthesis is not supported in this browser. Please use Chrome / Edge / Safari.',
    noSupportShort: 'Speech not supported. Please use Chrome / Edge / Safari',
    noWords: 'No words', noReading: 'No reading content'
  },
  lessons: { title: 'Lessons', lessons: 'Lessons', words: 'words', continue: 'Continue', searchTitle: 'Search lesson title', upload: '📥 Upload my book', uploadLogin: 'Sign in to upload', progressTotal: 'Total', progressLearned: 'Learned', progressRemain: 'Left',
    pickBook: 'Select a book', pickBookPh: 'Choose a book', noLessons: 'No lessons in this book' },
  home: { title: 'Home', welcome: 'Welcome back', start: 'Start learning →',
    mTitle: 'GRE Vocabulary', mSub: 'Lesson by lesson, tap to hear pronunciation. Works offline on mobile.',
    statLessons: 'Lessons', statWords: 'Words', statTts: 'Speech', noBooks: 'No books yet', tapStart: 'Tap to start' },
  mine: {
    title: 'Me', profile: 'Profile', progress: 'Progress', known: 'Learned', fav: 'Favorites', doneLessons: 'Lessons done',
    engine: 'Voice engine', voiceMode: 'Voice synthesis mode', useDesktop: 'Desktop version', useDesktopTip: 'Switch to full desktop UI',
    voiceFollow: 'Auto per device (recommended)', voiceOffline: 'Offline first (downloads voice pack once)', voiceServer: 'Server first (online required)',
    logout: 'Log out', login: 'Sign in / Sign up', roleAdmin: 'Admin', roleStudent: 'Student',
    notLogin: 'Not signed in', loginToSync: 'Sign in to sync progress',
    engineTitle: 'Voice engine', voiceModeTitle: 'Kokoro synthesis mode',
    tip: '🔊 Speech supports system voice / offline eSpeak / Tencent cloud neural voice / Kokoro neural network. Kokoro provides high-quality offline pronunciation for English words (first use downloads the model).'
  },
  mobile: { library: 'Library', study: 'Study', me: 'Me', back: 'Back', words: 'Read words', page: 'Read page', showZh: '中文', hideZh: 'Hide 中文', speakAll: '🔊 Read all', loading: 'Loading', pendingTip: '{n} new user(s) awaiting approval — tap to review' },
  users: {
    title: 'User Management', allStatus: 'All statuses', pending: 'Pending', active: 'Active', disabled: 'Disabled', rejected: 'Rejected',
    search: 'Search username / nickname / phone', id: 'ID', username: 'Username', nickname: 'Nickname', phone: 'Phone',
    regAddr: 'Register IP:Port', regTime: 'Registered at', status: 'Status', action: 'Actions',
    approve: 'Approve', reject: 'Reject', disable: 'Disable', enable: 'Enable', confirm: 'Confirm', confirmMsg: 'Apply "{action}" to user "{name}"?', ok: 'Done',
    noReg: '—', empty: 'No users'
  },
  common: { mobile: '📱 Mobile', desktop: '💻 Desktop' }
}
