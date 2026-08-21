# Feresa Slicer Privacy Policy / Политика конфиденциальности Feresa Slicer

Last updated: August 21, 2026

## English

Feresa Slicer is an open-source mobile 3D-printing slicer. This policy explains
what information the Android application processes and where it goes.

### Summary

- Feresa Slicer has no advertising or analytics SDKs.
- The Feresa Slicer project does not operate an application backend and does not
  sell personal data.
- Models and G-code are processed locally unless you explicitly send G-code to a
  printer that you configured.
- OrcaCloud sign-in and synchronization are optional. Local slicing works without
  an account.

### Information processed on the device

The app may process and store the following information:

- imported STL, OBJ, and 3MF model files and locally generated G-code;
- printer, filament, and process profiles;
- an optional OrcaCloud user identifier, email address, display name, refresh
  token, and synchronized profiles;
- an optional printer address, protocol, port, API key, and Basic Authentication
  username and password that you enter or import from a profile;
- language, theme, and other application preferences.

OrcaCloud refresh tokens, synchronized profiles, and manually saved printer
credentials are encrypted with keys held by Android Keystore. Application backup
is disabled. Temporary slicing files are stored in the app's private storage.

### Network communication

When you choose to sign in, the app opens your browser for Google or GitHub login
and communicates directly with the OrcaCloud authentication and profile services.
Feresa Slicer receives the account and profile information required to provide
optional profile synchronization. These exchanges are governed by the relevant
Google, GitHub, and OrcaCloud terms and privacy policies.

When you test a printer connection or send G-code, the app communicates directly
with the Moonraker or OctoPrint server address that you selected. Many local
printers use unencrypted HTTP. The app displays a warning before credentials are
saved for an HTTP connection; use HTTP only on a trusted local network.

The application does not upload model files or G-code to the Feresa Slicer
developers.

### Sharing and sale

Feresa Slicer does not sell user data and does not share it for advertising. Data
is exchanged only with services or devices that you explicitly select to provide
the requested feature: OrcaCloud and its chosen identity provider for optional
sign-in, and your printer server for connection testing or printing.

### Retention and deletion

Signing out removes the locally saved OrcaCloud session and cached profiles.
Deleting a manual printer connection removes the locally saved connection and its
credentials. Uninstalling the app removes its private application data. Feresa
Slicer cannot delete an OrcaCloud, Google, GitHub, Moonraker, or OctoPrint account;
those accounts and servers must be managed with their respective provider or
administrator.

### Children

Feresa Slicer is a technical tool for 3D-printing users and is not directed to
children.

### Open-source code and contact

Source code and issue tracking are available at
<https://github.com/AMVavilov/feresa-slicer>. Privacy questions can be submitted
through the repository's Issues section. The repository must be publicly
accessible before the Google Play release.

## Русский

Feresa Slicer — мобильный слайсер для 3D-печати с открытым исходным кодом. В этой
политике описано, какие данные обрабатывает Android-приложение и куда они
передаются.

### Кратко

- В Feresa Slicer нет рекламы и систем аналитики.
- Проект Feresa Slicer не использует собственный сервер приложения и не продаёт
  персональные данные.
- Модели и G-code обрабатываются локально, если пользователь сам не отправляет
  G-code на настроенный принтер.
- Вход и синхронизация OrcaCloud необязательны. Локальная нарезка работает без
  учётной записи.

### Данные, обрабатываемые на устройстве

Приложение может обрабатывать и хранить:

- импортированные модели STL, OBJ и 3MF и созданный локально G-code;
- профили принтера, филамента и процесса печати;
- при использовании OrcaCloud — идентификатор пользователя, адрес электронной
  почты, отображаемое имя, токен обновления и синхронизированные профили;
- при подключении принтера — его адрес, протокол, порт, API-ключ, логин и пароль
  Basic Authentication, введённые пользователем или полученные из профиля;
- выбранный язык, тему и другие настройки приложения.

Токен OrcaCloud, синхронизированные профили и сохранённые данные подключения к
принтеру зашифрованы ключами Android Keystore. Резервное копирование приложения
отключено. Временные файлы нарезки находятся в закрытом хранилище приложения.

### Сетевые соединения

При входе приложение открывает браузер для авторизации через Google или GitHub и
напрямую обращается к сервисам авторизации и профилей OrcaCloud. Feresa Slicer
получает данные учётной записи и профилей, необходимые для выбранной пользователем
синхронизации. Эти операции регулируются правилами конфиденциальности Google,
GitHub и OrcaCloud.

При проверке соединения или отправке G-code приложение напрямую обращается к
выбранному пользователем серверу Moonraker или OctoPrint. Многие локальные
принтеры используют незашифрованный HTTP. Приложение показывает предупреждение
перед сохранением учётных данных для HTTP; используйте его только в доверенной
локальной сети.

Приложение не загружает модели или G-code разработчикам Feresa Slicer.

### Передача и продажа

Feresa Slicer не продаёт данные и не передаёт их для рекламы. Данные передаются
только выбранным пользователем сервисам или устройствам для выполнения функции:
OrcaCloud и выбранному провайдеру входа — для необязательной авторизации, серверу
принтера — для проверки подключения или печати.

### Срок хранения и удаление

Выход из OrcaCloud удаляет сохранённую сессию и локальный кэш профилей. Удаление
ручного подключения удаляет его данные и учётные данные. Удаление приложения
очищает его закрытые данные. Feresa Slicer не может удалить учётные записи
OrcaCloud, Google, GitHub, Moonraker или OctoPrint — ими необходимо управлять у
соответствующего провайдера или администратора.

### Дети

Feresa Slicer — технический инструмент для пользователей 3D-принтеров и не
предназначен специально для детей.

### Исходный код и связь

Исходный код и раздел для обращений находятся по адресу
<https://github.com/AMVavilov/feresa-slicer>. Вопросы о конфиденциальности можно
оставить в разделе Issues. Перед публикацией в Google Play репозиторий должен быть
доступен публично.
