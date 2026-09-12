// Français
export default {
  app: { title: 'Partage de vocabulaire GRE' },
  nav: { home: 'Accueil', lessons: 'Leçons', mine: 'Profil', guide: 'Guide d\'enregistrement', admin: 'Administration', users: 'Utilisateurs', logout: 'Déconnexion', theme: 'Changer le thème', lang: 'Langue' },
  login: {
    title: 'Connexion', register: 'Inscription', username: 'Nom d\'utilisateur', password: 'Mot de passe', captcha: 'Captcha',
    phone: 'Téléphone (obligatoire, pour la validation)', nickname: 'Pseudo (facultatif)',
    agree: 'J\'ai lu et j\'accepte la ', terms: 'Politique de confidentialité',
    btnLogin: 'Se connecter', btnRegister: 'S\'inscrire',
    needUsername: 'Veuillez saisir le nom d\'utilisateur et le mot de passe', needTerms: 'Veuillez d\'abord accepter la politique de confidentialité', badPhone: 'Veuillez saisir un numéro de téléphone valide',
    needCaptcha: 'Veuillez saisir le captcha', captchaRefresh: 'Touchez pour actualiser', okLogin: 'Connecté',
    privacyText: 'Nous utilisons les informations de compte uniquement pour le service d\'apprentissage et ne les partageons jamais avec des tiers. En vous inscrivant, vous acceptez ce traitement.',
    okRegister: 'Inscription réussie. Vous pourrez vous connecter après validation par l\'administrateur.', reviewTip: 'Les nouveaux comptes nécessitent la validation de l\'administrateur',
    devTip: 'Administrateur par défaut : admin / admin123 (dev uniquement)'
  },
  lesson: {
    back: '← Liste des leçons', search: 'Rechercher mot / définition', engine: 'Voix', auto: 'Auto',
    hideKnown: 'Masquer appris', showAll: 'Tout afficher', hideShort: 'Masquer', allShort: 'Tout',
    speakWords: '🔊 Lire les mots', speakPage: '🔊 Lire la page', wordsShort: '🔊 Mots', pageShort: '🔊 Page',
    autoScroll: 'Défilement auto', markDone: '✓ Leçon terminée', done: '✓ Terminée', doneTip: 'Progression enregistrée', loopNone: 'Pas de boucle', loopLesson: 'Boucle leçon', loopBook: 'Boucle livre',
    paused: '⏸ En pause', playing: '🔊 Lecture', resume: '▶ Reprendre', pause: '⏸ Pause', restart: '↺ Recommencer', stop: '⏹ Arrêter',
    readWord: 'Mot', readExample: 'Exemple', known: '✓ Appris', markKnown: 'Marquer appris', unfavorite: 'Retirer des favoris', favorite: 'Favori',
    offlineVoice: 'Voix neuronale hors ligne', serverVoice: 'Voix neuronale serveur', espeak: 'eSpeak (robotique)', sysVoice: 'Voix système', tencentVoice: 'Voix Tencent Cloud',
    kokoroTip: '⏳ Chargement du pack vocal Kokoro {pct}% (~92 Mo, une seule fois ; la synthèse prend quelques secondes)',
    noSupport: 'La synthèse vocale n\'est pas prise en charge par ce navigateur. Utilisez Chrome / Edge / Safari.',
    noSupportShort: 'Synthèse vocale non prise en charge. Utilisez Chrome / Edge / Safari',
    noWords: 'Aucun mot', noReading: 'Aucun texte de lecture'
  },
  lessons: { title: 'Leçons', lessons: 'Leçons', words: 'mots', continue: 'Continuer', searchTitle: 'Rechercher un titre de leçon', upload: '📥 Téléverser mon livre', uploadLogin: 'Connectez-vous pour téléverser', progressTotal: 'Total', progressLearned: 'Appris', progressRemain: 'Restant',
    pickBook: 'Choisir un livre', pickBookPh: 'Veuillez choisir un livre', noLessons: 'Aucune leçon dans ce livre' },
  home: { title: 'Accueil', welcome: 'Bon retour', start: 'Commencer →',
    mTitle: 'Vocabulaire GRE', mSub: 'Leçon par leçon, touchez pour écouter. Fonctionne hors ligne sur mobile.',
    statLessons: 'Leçons', statWords: 'Mots', statTts: 'Voix', noBooks: 'Aucun livre', tapStart: 'Touchez pour commencer' },
  mine: {
    title: 'Profil', profile: 'Profil', progress: 'Progression', known: 'Appris', fav: 'Favoris', doneLessons: 'Leçons terminées',
    engine: 'Moteur vocal', voiceMode: 'Mode de synthèse vocale', useDesktop: 'Version bureau', useDesktopTip: 'Passer à l\'interface bureau complète',
    voiceFollow: 'Auto selon l\'appareil (recommandé)', voiceOffline: 'Hors ligne d\'abord (télécharge le pack vocal)', voiceServer: 'Serveur d\'abord (en ligne requis)',
    logout: 'Déconnexion', login: 'Connexion / Inscription', roleAdmin: 'Administrateur', roleStudent: 'Étudiant',
    notLogin: 'Non connecté', loginToSync: 'Connectez-vous pour synchroniser',
    engineTitle: 'Moteur vocal', voiceModeTitle: 'Mode de synthèse Kokoro',
    tip: '🔊 Voix système / eSpeak hors ligne / voix neuronale Tencent Cloud / réseau de neurones Kokoro. Kokoro fournit une prononciation hors ligne de haute qualité pour les mots anglais (le modèle est téléchargé au premier usage).'
  },
  mobile: { library: 'Bibliothèque', study: 'Étude', me: 'Profil', back: 'Retour', words: 'Lire les mots', page: 'Lire la page', showZh: '中文', hideZh: 'Masquer 中文', speakAll: '🔊 Tout lire', loading: 'Chargement', pendingTip: '{n} nouvel(s) utilisateur(s) en attente – touchez pour traiter' },
  users: {
    title: 'Gestion des utilisateurs', allStatus: 'Tous les statuts', pending: 'En attente', active: 'Actif', disabled: 'Désactivé', rejected: 'Refusé',
    search: 'Rechercher pseudo / téléphone', id: 'ID', username: 'Nom d\'utilisateur', nickname: 'Pseudo', phone: 'Téléphone',
    regAddr: 'IP:port d\'inscription', regTime: 'Date d\'inscription', status: 'Statut', action: 'Actions',
    approve: 'Approuver', reject: 'Refuser', disable: 'Désactiver', enable: 'Activer', confirm: 'Confirmer', confirmMsg: 'Appliquer « {action} » à l\'utilisateur « {name} » ?', ok: 'Effectué',
    noReg: '—', empty: 'Aucun utilisateur'
  },
  common: { mobile: '📱 Mobile', desktop: '💻 Bureau' }
}
