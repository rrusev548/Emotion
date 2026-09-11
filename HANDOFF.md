# HANDOFF — Emotion Pet

Предаване към следваща агентска сесия. Прочети това първо.

## Текущо състояние

- **Клон на тази сесия:** `arena/01a08ea3-emotion` (разклонен от merge commit-а на PR #2). Тук е направен fix + нов release `v1.1`.
- **PR #2** (`claude/emotion-pet-continuation-dojdfy`) вече е **MERGED** в `main`. **PR #1** (`arena/01a08c73-emotion`) също е merge-нат — не го пипай/reuse-вай.
- **`main`** е 1 commit напред спрямо базата на този клон (автоматичен `ci: обнови UI снимките` след merge-а).
- **Release `v1.3`** (таг `v1.3` → commit `0d6f518`): https://github.com/rrusev548/Emotion/releases/download/v1.3/EmotionPet.apk — **препоръчан** (release, подписан с keystore/release.p12, не-debuggable, ~5.1 MB). Има и debug: `.../v1.3/EmotionPet-debug.apk`. От `v1.2` нататък пакетът е `com.emotion.pet` (без `.debug` суфикс). От `v1.3`: `minSdk 21` (вместо 24) + release signing (не-debuggable).
- **Release `v1.2`** (таг `v1.2` → commit `90a116c`): първият с нов пакет `com.emotion.pet` (без суфикс).
- **Release `v1.1`** (таг `v1.1` → commit `22bffbc`): стар пакет `com.emotion.pet.debug` (остарял).
- **Public APK** (`latest-build`): https://github.com/rrusev548/Emotion/releases/download/latest-build/EmotionPet-debug.apk — обновява се само чрез `workflow_dispatch` на `android.yml` или push към `main`/tag. Push към `arena/*` клон НЕ го обновява; затова се използва таг `v*` (той създава отделен release).
- Локалната среда (тази sandbox) **няма Android SDK/JDK/emulator** (и `apt-get` е блокиран — няма root) — само git/gh/curl. Валидацията минава изцяло през GitHub Actions CI. `gh workflow run` дава 403 (токенът няма `workflow` scope) → НЕ може да се пуска workflow_dispatch; използвай push/tag.
- Мрежата в sandbox-а **не стига** до `objects.githubusercontent.com` / `release-assets.githubusercontent.com` (файловите хостове на GitHub) → APK не може да се свали/провери локално; достъпни са само api.github.com и github.com.

## Какво е направено в предишната сесия (PR #2, по ред)

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

## ⚠️ Текущ проблем: crash при стартиране (Samsung) — статус след тази сесия

**Инсталационната грешка „Приложението не е инсталирано“ е РЕШЕНА** — фиксът на debug keystore-а подейства: потребителят потвърди, че вече се инсталира.

**Нов проблем:** приложението се инсталира, но **крашва при стартиране** на Samsung (One UI) телефон.

**Направено тази сесия (когато стана ясно, че крашва при старт):**
1. **`PetView.kt`** — добавен `setLayerType(View.LAYER_TYPE_SOFTWARE, null)` в `init`. Хипотеза: Samsung крашва нативно (SIGSEGV в Skia), когато emoji се рисува с `canvas.drawText` върху hardware-accelerated Canvas (любимецът се рисува с `drawText` всяка рамка). Software слой е документираният fix за точно този клас крашове.
2. **`CrashLog.kt` (нов)** — `Thread.setDefaultUncaughtExceptionHandler` записва Java изключенията в `filesDir/crash_log.txt`; `MainActivity.maybeReportCrash()` показва диалог с първите редове + бутон „Копирай“ при следващо пускане. Така потребителят може да прати точния stack trace без adb. (Нативните SIGSEGV не минават през този handler.)
3. **`values-en/strings.xml`** — добавени 5 липсващи overlay ключа (`setting_overlay`, `overlay_perm_needed`, `overlay_channel_name`, `overlay_notif_title`, `overlay_notif_text`). Преди това менюто/overlay-я биха хвърлили `Resources.NotFoundException` на **английско** устройство.
4. **`app/build.gradle.kts`** — `versionCode` 1→2, `versionName` "1.0"→"1.1" (гладък ъпгрейд със същия ключ).

Всичко е push-нато в `arena/01a08ea3-emotion` + тагове `v1.1`, `v1.2`, `v1.3` (release с APK). CI зелен на клона и на таговете.

**⚠️ Уточнение от потребителя (след v1.1/v1.2):** той ВСЕ ОЩЕ получава „Приложението не е инсталирано“ при инсталация (снимка на „Package installer“). Тъй като v1.2+ е със СЪВСЕМ нов пакет `com.emotion.pet`, конфликт на подписи вече НЕ е причина — значи проблемът е в устройството/свалянето, НЕ в APK-то. Действия, които са предприети в отговор (все още без потвърждение от потребителя):
- v1.3: release-signed APK (не-debuggable) + `minSdk 21` (за да се изключат „стар Android“ и „debuggable блокиран“ хипотезите).
- Дадени инструкции: (1) сваляне през **Chrome/Samsung Internet, НЕ през вградения браузър на чата** (там 302-редиректът към objects.githubusercontent.com може да къса файла), (2) проверка на размера в My Files, (3) изключване на **Auto Blocker** (Samsung) и Play Protect, (4) инсталация през **SAI (Split APKs Installer)** от Play Store, за да се види ТОЧНИЯ код на грешката (INSTALL_FAILED_OLDER_SDK / UPDATE_INCOMPATIBLE / PARSE_FAILED_... / INSUFFICIENT_STORAGE / USER_RESTRICTED).

**Следващи стъпки за новия агент/сесия (ако v1.1 пак крашва):**
1. Поискай от потребителя **текста от crash диалога** („Копирай“ → праща го в чата). Той показва точния Java stack trace.
2. Ако диалог НЕ се появява, а крашът остава → почти сигурно е **нативен** crash (SIGSEGV). Тогава: накарай потребителя да пусне **bug report** (Settings → Developer options → „Take bug report“) или `adb logcat` от компютър; потърси `FATAL EXCEPTION` / `SIGSEGV` / `libskia`/`libhwui` в лога.
3. Други кандидати за native crash при нужда: `saveLayerAlpha` върху голям canvas (в `drawBubble`), `BitmapShader` с огромна снимка за фон (`loadWallpaper` decode-ва на пълна резолюция без downsample).

## Известни ограничения

- Няма Android SDK/emulator в тази sandbox среда — само CI компилация + Robolectric unit тестове. Overlay/widget/splash функционалността **никога не е верифицирана на реално устройство** от агента, само от потребителя (който засега не е потвърдил успешна инсталация с новите функции).
- Мрежата в sandbox-а не стига до GitHub Actions artifact storage (Azure blob) — screenshots се вземат само през git commit-натите `docs/screenshots/`, не през artifact download.
- Публичният "latest-build" release се обновява **само** ако workflow-ът се пусне през `workflow_dispatch` (или push към `main`/tag) — обикновен push към continuation branch-а НЕ го пипа (виж `.github/workflows/android.yml`, стъпка "Publish latest build release"). След всеки push, който искаш веднага достъпен за потребителя без merge в main, пусни ръчно `workflow_dispatch` на `android.yml` за branch-а.

## Roadmap (README.md, "Идеи за следващо")

- [x] Widget на home screen
- [x] Overlay режим
- [ ] Разучаване на нови думи/команди („хайде навън")
- [ ] Синхронизация на образа с любима снимка чрез AI стилизация
