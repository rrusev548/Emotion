# HANDOFF — Emotion Pet

Предаване към следваща агентска сесия. Прочети това първо.

## Текущо състояние

- **PR:** [#2](https://github.com/rrusev548/Emotion/pull/2) — `claude/emotion-pet-continuation-dojdfy` → `main`. Open, draft: не, `mergeable_state: clean`, 14 commit-а, CI зелено на всеки push.
- **PR #1** (клон `arena/01a08c73-emotion`) вече е merge-нат в `main` преди тази сесия — не го пипай/reuse-вай.
- **Public APK:** https://github.com/rrusev548/Emotion/releases/download/latest-build/EmotionPet-debug.apk — обновява се само чрез `workflow_dispatch` на `android.yml` (виж по-долу защо push сам по себе си не го обновява).
- Локалната среда (тази sandbox) **няма Android SDK/emulator** — само JDK + `keytool`. Валидацията минава изцяло през GitHub Actions CI (unit тестове + `assembleDebug`). Никога не е тествано на реално устройство от мен — само от потребителя.

## Какво е направено тази сесия (по ред)

1. **Оправен счупен build** — `LayoutInflateTest` ползваше ръчен `ContextThemeWrapper` без AppCompat factory → Material компоненти гърмяха при inflate. Вече взима `layoutInflater` от реална `Robolectric.buildActivity(MainActivity::class.java)`.
2. **CI поправка** — "Commit UI screenshots" стъпката в `.github/workflows/android.yml` проверяваше твърдо `github.ref == 'refs/heads/arena/01a08c73-emotion'` (стар, вече merge-нат клон) → никой нов continuation branch не получаваше обновени `docs/screenshots`. Вече е `startsWith(github.ref, 'refs/heads/')`.
3. **Стабилност**: премахнати deprecated `kotlinOptions`/`AlertDialog.setView` overload-и; по-разбираеми AI грешки (timeout/UnknownHost/ConnectException) в `AiClient.kt`.
4. **Haptics.kt** — вибрация при досег с любимеца/действие от менюто (VIBRATE permission вече се ползва).
5. **`PetWidgetProvider`** — widget на началния екран (аватар + статове), тап отваря приложението.
6. **`OverlayService`** — плаващ балон над другите приложения (SYSTEM_ALERT_WINDOW, foreground service `specialUse`), тап отваря апп, влачене мести балона, задържане (500ms, само ако НЕ е местен) го спира. `BootReceiver` го рестартира след рестарт на телефона, ако е бил включен и разрешението е дадено.
7. **Presets/AiClient** — добавени **Gemini** (официалният OpenAI-съвместим ендпойнт на Google, `gemini-3.8-flash`) и **Claude** (нативен Anthropic Messages API — различен request/response формат, разклонено в `AiClient.chat()`/`test()` през нов `providerId` параметър) до вече съществуващите OpenAI/Groq/OpenRouter/Custom.
8. **Фиксиран debug keystore** (`keystore/debug.keystore`, чекнат в repo-то, alias `androiddebugkey`/парола `android`) — преди това всеки CI runner генерираше нов случаен debug ключ и APK-та от различни build-ове взаимно се отхвърляха при инсталация ("Приложението не е инсталирано"). Виж build.gradle.kts `signingConfigs { getByName("debug") {...} }`.
9. **Splash screen** (`androidx.core:core-splashscreen`, `Theme.EmotionPet.Starting`) + **еднократна подкана за закачане на иконата на началния екран** (`ShortcutManager.requestPinShortcut`, API 26+) при първо пускане.
10. Два self-review прохода (виж git log за "self-review находки") хванаха и оправиха: drag-vs-hold бъг в overlay-я, подвеждащо "включено" състояние на overlay switch-а при липсващо разрешение, липсващо тестово покритие за новите layout-и, потенциален risk с installSplashScreen под Robolectric (обвито в `runCatching`).

Всичко по-горе е push-нато, CI зелено на всеки етап.

## ⚠️ Нерешен проблем: "Приложението не е инсталирано" на телефона на потребителя

Потребителят **все още** получава инсталационна грешка **след** фикса на keystore-а (commit `bbf29c4` и по-нови) и след многократни инструкции да деинсталира старата версия първо. Не е потвърдено дали:

- (а) наистина е направил деинсталиране на старата версия преди последния опит (най-вероятната причина — повтарящ се пропуск в разговора),
- (б) APK файлът се е свалил коректно (частично/повредено сваляне),
- (в) устройството има скрит втори профил/Secure Folder с друг инстанс на приложението, невидим в основния Settings → Apps,
- (г) "Install unknown apps" разрешението липсва за конкретното приложение, през което той отваря файла (Files vs Chrome vs друг браузър — разрешението е per-app-source, не глобално),
- (д) нещо специфично за устройството/Android версията му (модел непознат от нас — Samsung One UI по скрийншотите).

**Следващи стъпки за новия агент/сесия:**
1. Поискай **точен нов скрийншот** на грешката (работи добре досега — предишен скрийншот директно разкри Play Protect блока).
2. Потвърди изрично: "деинсталира ли старата версия преди да пробваш пак?" — не приемай мълчаливо "не работи" без да върнеш този въпрос.
3. Ако наистина е чисто устройство (никаква стара версия): провери размера на свалени файл (~6.0-6.1 MB), провери "Install unknown apps" разрешение за точното приложение, през което отваря APK-а.
4. Ако нищо от горното не помогне — обмисли `adb` инструкции (ако потребителят може да свърже телефона към компютър) за по-детайлна диагностика (`adb install -r EmotionPet-debug.apk` дава точен грешка код вместо генеричния UI диалог).

## Известни ограничения

- Няма Android SDK/emulator в тази sandbox среда — само CI компилация + Robolectric unit тестове. Overlay/widget/splash функционалността **никога не е верифицирана на реално устройство** от агента, само от потребителя (който засега не е потвърдил успешна инсталация с новите функции).
- Мрежата в sandbox-а не стига до GitHub Actions artifact storage (Azure blob) — screenshots се вземат само през git commit-натите `docs/screenshots/`, не през artifact download.
- Публичният "latest-build" release се обновява **само** ако workflow-ът се пусне през `workflow_dispatch` (или push към `main`/tag) — обикновен push към continuation branch-а НЕ го пипа (виж `.github/workflows/android.yml`, стъпка "Publish latest build release"). След всеки push, който искаш веднага достъпен за потребителя без merge в main, пусни ръчно `workflow_dispatch` на `android.yml` за branch-а.

## Roadmap (README.md, "Идеи за следващо")

- [x] Widget на home screen
- [x] Overlay режим
- [ ] Разучаване на нови думи/команди („хайде навън")
- [ ] Синхронизация на образа с любима снимка чрез AI стилизация
