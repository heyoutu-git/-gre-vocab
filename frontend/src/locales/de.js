// Deutsch
export default {
  app: { title: 'GRE-Wortschatz teilen' },
  nav: { home: 'Start', lessons: 'Lektionen', mine: 'Profil', guide: 'Aufnahme-Anleitung', admin: 'Administration', users: 'Benutzer', logout: 'Abmelden', theme: 'Design wechseln', lang: 'Sprache' },
  login: {
    title: 'Anmelden', register: 'Registrieren', username: 'Benutzername', password: 'Passwort', captcha: 'Captcha',
    phone: 'Telefon (erforderlich, für die Freigabe)', nickname: 'Spitzname (optional)',
    agree: 'Ich habe die ', terms: 'Datenschutzerklärung',
    btnLogin: 'Anmelden', btnRegister: 'Registrieren',
    needUsername: 'Bitte Benutzername und Passwort eingeben', needTerms: 'Bitte zuerst die Datenschutzerklärung akzeptieren', badPhone: 'Bitte eine gültige Telefonnummer eingeben',
    needCaptcha: 'Bitte Captcha eingeben', captchaRefresh: 'Zum Aktualisieren tippen', okLogin: 'Angemeldet',
    privacyText: 'Wir nutzen Kontodaten ausschließlich für den Lernservice und geben keine persönlichen Daten an Dritte weiter. Mit der Registrierung stimmen Sie dieser Verarbeitung zu.',
    okRegister: 'Registrierung erfolgreich. Anmeldung nach Freigabe durch den Administrator möglich.', reviewTip: 'Neue Konten benötigen die Freigabe durch den Administrator',
    devTip: 'Standard-Admin: admin / admin123 (nur Entwicklung)'
  },
  lesson: {
    back: '← Lektionsliste', search: 'Wort / Definition suchen', engine: 'Stimme', auto: 'Auto',
    hideKnown: 'Gelernte ausblenden', showAll: 'Alle anzeigen', hideShort: 'Aus', allShort: 'Alle',
    speakWords: '🔊 Wörter vorlesen', speakPage: '🔊 Seite vorlesen', wordsShort: '🔊 Wörter', pageShort: '🔊 Seite',
    autoScroll: 'Auto-Scroll', markDone: '✓ Lektion erledigt', done: '✓ Erledigt', doneTip: 'Fortschritt gespeichert', loopNone: 'Keine Schleife', loopLesson: 'Lektionsschleife', loopBook: 'Buchschleife',
    paused: '⏸ Pausiert', playing: '🔊 Wird gelesen', resume: '▶ Fortsetzen', pause: '⏸ Pause', restart: '↺ Neu starten', stop: '⏹ Stopp',
    readWord: 'Wort', readExample: 'Beispiel', known: '✓ Gelernt', markKnown: 'Als gelernt markieren', unfavorite: 'Favorit entfernen', favorite: 'Favorit',
    offlineVoice: 'Offline-Neuralstimme', serverVoice: 'Server-Neuralstimme', espeak: 'eSpeak (robotisch)', sysVoice: 'Systemstimme', tencentVoice: 'Tencent-Cloud-Stimme',
    kokoroTip: '⏳ Kokoro-Sprachpaket wird geladen {pct}% (~92 MB, nur einmal; Synthese dauert einige Sekunden)',
    noSupport: 'Sprachausgabe wird von diesem Browser nicht unterstützt. Bitte Chrome / Edge / Safari verwenden.',
    noSupportShort: 'Sprachausgabe nicht unterstützt. Bitte Chrome / Edge / Safari verwenden',
    noWords: 'Keine Wörter', noReading: 'Kein Lesetext'
  },
  lessons: { title: 'Lektionen', lessons: 'Lektionen', words: 'Wörter', continue: 'Fortsetzen', searchTitle: 'Lektionstitel suchen', upload: '📥 Mein Buch hochladen', uploadLogin: 'Zum Hochladen anmelden', progressTotal: 'Gesamt', progressLearned: 'Gelernt', progressRemain: 'Rest',
    pickBook: 'Buch auswählen', pickBookPh: 'Bitte Buch wählen', noLessons: 'Keine Lektionen in diesem Buch' },
  home: { title: 'Start', welcome: 'Willkommen zurück', start: 'Lernen starten →',
    mTitle: 'GRE-Wortschatz', mSub: 'Lektion für Lektion – zum Anhören antippen. Funktioniert auch offline.',
    statLessons: 'Lektionen', statWords: 'Wörter', statTts: 'Sprache', noBooks: 'Noch keine Bücher', tapStart: 'Zum Starten tippen' },
  mine: {
    title: 'Profil', profile: 'Profil', progress: 'Fortschritt', known: 'Gelernt', fav: 'Favoriten', doneLessons: 'Lektionen erledigt',
    engine: 'Stimm-Engine', voiceMode: 'Sprachsynthese-Modus', useDesktop: 'Desktop-Version', useDesktopTip: 'Zur vollständigen Desktop-Oberfläche wechseln',
    voiceFollow: 'Automatisch je Gerät (empfohlen)', voiceOffline: 'Offline zuerst (lädt Sprachpaket einmal)', voiceServer: 'Server zuerst (online nötig)',
    logout: 'Abmelden', login: 'Anmelden / Registrieren', roleAdmin: 'Administrator', roleStudent: 'Student',
    notLogin: 'Nicht angemeldet', loginToSync: 'Zum Synchronisieren anmelden',
    engineTitle: 'Stimm-Engine', voiceModeTitle: 'Kokoro-Synthese-Modus',
    tip: '🔊 Sprachausgabe mit Systemstimme / Offline-eSpeak / Tencent-Cloud-Neuralstimme / Kokoro-Neuronetz. Kokoro liefert hochwertige Offline-Aussprache für englische Wörter (beim ersten Mal wird das Modell geladen).'
  },
  mobile: { library: 'Bibliothek', study: 'Lernen', me: 'Profil', back: 'Zurück', words: 'Wörter vorlesen', page: 'Seite vorlesen', showZh: '中文', hideZh: '中文 ausblenden', speakAll: '🔊 Alles vorlesen', loading: 'Wird geladen', pendingTip: '{n} neue(r) Benutzer wartet auf Freigabe – zum Bearbeiten tippen' },
  users: {
    title: 'Benutzerverwaltung', allStatus: 'Alle Status', pending: 'Ausstehend', active: 'Aktiv', disabled: 'Gesperrt', rejected: 'Abgelehnt',
    search: 'Benutzername / Spitzname / Telefon suchen', id: 'ID', username: 'Benutzername', nickname: 'Spitzname', phone: 'Telefon',
    regAddr: 'Registrierungs-IP:Port', regTime: 'Registriert am', status: 'Status', action: 'Aktionen',
    approve: 'Freigeben', reject: 'Ablehnen', disable: 'Sperren', enable: 'Entsperren', confirm: 'Bestätigen', confirmMsg: '„{action}“ für Benutzer „{name}“ ausführen?', ok: 'Erledigt',
    noReg: '—', empty: 'Keine Benutzer'
  },
  common: { mobile: '📱 Mobil', desktop: '💻 Desktop' }
}
