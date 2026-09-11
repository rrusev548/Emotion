# HANDOFF — Emotion Pet

Предаване към следваща агентска сесия. Прочети това първо.

## Къде сме в момента (едно изречение)

Потребителят иска **готово работещо APK на телефона си (Samsung)**. Минахме целия цикъл диагностика: от „Приложението не е инсталирано“ → `INSTALL_FAILED_VERIFICATION_FAILURE` (Play Protect блокира рискови разрешения) → сега `INSTALL_FAILED_ABORTED: User rejected installing unknown source package` (SAI няма разрешение да инсталира). **Файлът вече е изцяло изправен и приет от системата — остава потребителят да даде на SAI/„Моите файлове“ разрешение „Инсталиране на неизвестни приложения“ и да натисне Install.**

## Единственият активен файл (ползвай само него)

- **Пакет:** `com.emotion.pet2` (СЪВСЕМ нов, от v1.6 нататък — няма конфликт с нищо старо)
- **Версия:** `1.7`, `versionCode 8`, `minSdk 21`, `targetSdk 35`
- **Сваляне (директно, без пренасочване — препоръчан линк):**
  https://raw.githubusercontent.com/rrusev548/Emotion/arena/01a08ea3-emotion/artifacts/EmotionPet.apk
- **Резервен (GitHub release):**
  https://github.com/rrusev548/Emotion/releases/download/v1.7/EmotionPet.apk
- **За Google Play (AAB):** https://github.com/rrusev548/Emotion/releases/download/v1.7/EmotionPet.aab
- APK-то се commit-ва автоматично в `artifacts/EmotionPet.apk` на клона (оттам и raw линкът). Размер ~5 103 012 байта (~5,1 MB).

## Какво е премахнато (и защо) — важно да се знае

Play Protect отхвърляше приложението (`INSTALL_FAILED_VERIFICATION_FAILURE`) заради **рискови разрешения**. В `v1.7` са премахнати:
- 🪟 **Плаващ прозорец** (`OverlayService.kt`, `SYSTEM_ALERT_WINDOW`, special-use foreground service)
- ⚡ **Автостарт при рестарт** (`BootReceiver.kt`, `RECEIVE_BOOT_COMPLETED`)
- Съответните UI елементи (switch „Плаващ прозорец“ в менюто), `Prefs.overlayEnabled`, `MenuSheet.onOverlayToggle`, логиката в `MainActivity.onResume`.

Останали разрешения: **само `INTERNET` и `VIBRATE`** — безобидни, Play Protect вече НЕ блокира (потвърдено: кодът се смени от verification-failure към aborted).

**Ако в бъдеще върнеш overlay/boot функционалността** — тя НЕ беше проблем в кода, а е блокер за странична инсталация. Връщай я само през Google Play (там е позволено) или с ясно предупреждение, че sideload ще бъде блокиран от Play Protect.

## Хронология на диагностиката (какво вече сме изключили)

1. **„Приложението не е инсталирано“** → изключихме конфликт на подписи: сменихме пакета на `com.emotion.pet2` (v1.6+), единен release ключ за debug+release (v1.4+).
2. **`INSTALL_FAILED_VERIFICATION_FAILURE`** (показано от SAI) → причина Play Protect; премахнахме рисковите разрешения (v1.7).
3. **`INSTALL_FAILED_ABORTED: User rejected installing unknown source package`** → SAI няма дадено разрешение „Инсталиране на неизвестни приложения“. Това е **последната, чисто потребителска стъпка**.

**Вече проверено и потвърдено (не преповтаряй):**
- APK-то е валидно: `apksigner verify` → v1: true, v2: true; signer CN=Emotion Pet.
- Манифест: пакет `com.emotion.pet2`, `versionName 1.7`, `minSdk 21`, `targetSdk 35`, `launchable-activity com.emotion.pet.MainActivity`, БЕЗ `application-debuggable` и БЕЗ `testOnly`.
- CI зелен на клона и на тага `v1.7`.

## Следващи стъпки за новия агент

1. **Насочи потребителя към последната стъпка (30 сек):**
   - Настройки → Приложения → **SAI** → „Инсталиране на неизвестни приложения“ → **Разреши от този източник**.
   - После: SAI → Install APKs → `EmotionPet.apk` → Install.
   - Алтернатива без SAI: дай разрешението на **„Моите файлове“** и инсталирай оттам.
2. **Потвърди успешна инсталация** и попитай дали приложението **стартира**. Това е следващата възможна точка на проблем (предишният потребителски проблем „инсталира се, но крашва при старт“ още НЕ е потвърден като решен/наличен).
3. **Ако крашне при старт:** в `v1.1` беше добавен `CrashLog` (диалог „Копирай“ при следващо пускане) + `PetView` вече рисува в software слой (фикс за Samsung SIGSEGV при drawText на emoji). Накарай потребителя да копира/прати stack trace-а. Ако диалог не излиза, а крашът остава — искай `adb logcat` или Samsung „bug report“.
4. **Ако инсталацията пак блокира с друг код от SAI** — донеси кода, той е точната диагноза.

## Ключове / signing (да не се пипа без причина)

- `keystore/release.p12` (PKCS12) — използва се за **release И debug** (единен ключ). Парола/alias: `emotionpet`.
- `keystore/debug.keystore` — остава в репото, но вече НЕ се ползва (debug подписва с release.p12).
- Не сменяй ключа/пакета без координация — това е източникът на всички „не се инсталира“ болки.

## CI механика (важно)

- CI (`android.yml`): build → тестове → **верификация на APK** (apksigner + aapt2, резултат в `docs/apk-info.txt`) → commit-ва `artifacts/EmotionPet.apk` + снимки + `docs/apk-info.txt` в клона → публикува release при **tag `v*`** (или push в `main`).
- **Sandbox-ът НЯМА Android SDK/JDK** (и `apt` е без root). Всичко се валидира в CI; чети `docs/apk-info.txt` от репото (api.github.com е достъпен).
- **`gh workflow run` = 403** (токенът няма workflow scope) → пускай чрез push/tag, НЕ workflow_dispatch.
- **Мрежата блокира** `objects.githubusercontent.com`, `release-assets.githubusercontent.com`, `raw.githubusercontent.com`, `codeload` → не можеш да свалиш APK локално; разчитай на `gh api` (api.github.com).
- GitHub Pages не може да се пусне (403) — не разчитай на него.
- Всеки push към `arena/01a08ea3-emotion` генерира CI commit „[skip ci]“ — **винаги `git fetch` + rebase/merge преди нов push**, иначе push-ът отказва (non-fast-forward).
- Tag `v*` → release с `EmotionPet.apk` + `EmotionPet.aab`.

## Технически бележки / наследство

- Клон на сесията: **`arena/01a08ea3-emotion`** (само в него работи — не създавай/не push-вай други клонове).
- `main` е 1 commit напред спрямо базата (автоматичен `ci: обнови UI снимките` след merge на PR #2).
- PR #1 и PR #2 са merge-нати. Не ги преизползвай.
- В `values-en/strings.xml` вече са добавени overlay-ключовете (въпреки че overlay е премахнат, преводите стоят — безвредни).
- `docs/apk-info.txt` се обновява при всеки CI run — чети го, за да видиш текущия подпис/версия/разрешения на реално компилирания APK.

## Roadmap (README „Идеи за следващо“) — след като инсталацията е стабилна

- [x] Widget на home screen
- [ ] Разучаване на нови думи/команди („хайде навън“)
- [ ] AI стилизация на образа (синхрон на образа с любима снимка)
- [ ] (евентуално) връщане на overlay/boot — само през Google Play
