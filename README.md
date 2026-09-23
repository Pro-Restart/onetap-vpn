# OneTap VPN — статус после разбора и исправлений

## Что было исправлено в этой версии

### 1. LinkParser — баг с распознаванием ссылок
В предыдущей сборке `detect()` был написан как цепочка `if/else if`, где при
УДАЧНОМ совпадении почти всех протоколов (vless/vmess/trojan/ss/ssr/hysteria*/
tuic/wg/amnezia) код по ошибке прыгал туда же, куда и при полном
несовпадении — то есть возвращал `null`. Реально работала только ветка
`tt://`. Найдено дизассемблированием байткода `classes6.dex` (метод
`LinkParser.detect`) — см. историю анализа.

Исправлено: переписано через `when`, где каждая ветка сразу возвращает
готовый профиль — структурно невозможно перепутать местами.

### 2. Подключение не устанавливалось
Причина: `libtrusttunnel_android.so` не была подключена как нативная
библиотека в проект, а `TrustTunnelEngine.connect()` был заглушкой,
бросающей `IllegalStateException` сразу при вызове (подтверждено —
байткод метода начинается с `new-instance IllegalStateException`).
Кроме того, ни в одном месте кода не вызывался
`VpnService.Builder().establish()` — то есть даже при рабочем движке
TUN-интерфейс не создавался.

Исправлено:
- `libtrusttunnel_android.so` (arm64-v8a) добавлена в `jniLibs/`
- Написан класс `com.adguard.trusttunnel.VpnClient` с точными сигнатурами
  методов, извлечёнными из `classes.dex` оригинального TrustTunnel-приложения
  (см. `vpn/trusttunnel/TrustTunnelNative.kt`)
- `TrustTunnelEngine` теперь реально создаёт TUN через
  `VpnService.Builder().establish()` и передаёт дескриптор в нативный движок

## Статус по протоколам после исправлений

| Протокол | Статус |
|---|---|
| **TrustTunnel** | Должен заработать — полная реализация на основе реверс-инжиниринга. Требует проверки на реальном сервере (адресация TUN — `10.64.0.2/32`, full-tunnel маршрут — **предположение**, не вычитано из оригинального кода, см. комментарии в `TrustTunnelEngine.kt`) |
| **AmneziaWG** | Работает для конфигов в виде текста `[Interface]/[Peer]`. Компактные ссылки `wg://`/`amnezia://` — **не реализовано**, требуют отдельного реверса формата кодирования |
| **Xray/sing-box** | Не реализовано — честная заглушка с понятной ошибкой вместо тихого зависания. Следующий шаг — реверсить `libgojni.so` тем же методом, что и TrustTunnel |
| **FreeTurn** | Частично — транспорт (Go-бинарник) запускается, но связка с WireGuard поверх него не собрана в один пайплайн |

## Важно перед тестированием

1. **Только arm64-v8a.** Библиотека TrustTunnel извлечена только под эту
   архитектуру — на устройствах с другим ABI работать не будет, пока не
   достанете остальные ABI (armeabi-v7a/x86_64) из оригинального APK.

2. **Адресация TUN для TrustTunnel — предположение.** В `TrustTunnelEngine.kt`
   используются общепринятые значения (10.64.0.2/32, full-tunnel, DNS
   1.1.1.1/8.8.8.8), а не вычитанная из `VpnServiceConfig.parseToml()` схема —
   это заняло бы отдельный цикл реверса TOML-конфига. Если подключение
   не пойдёт — первым делом смотрите `onConnectionInfo` в логах (Logcat,
   тег `TrustTunnelEngine`), там должна быть диагностика от самой библиотеки.

3. **verifyCertificate сейчас всегда возвращает true** — это TODO по
   безопасности, а не финальное решение. Стоит доработать перед реальным
   использованием.

## Структура

```
app/src/main/java/com/onetapvpn/
├── model/Models.kt              — Engine enum, ServerProfile
├── parser/LinkParser.kt         — исправленный парсер ссылок
├── vpn/trusttunnel/
│   └── TrustTunnelNative.kt     — воссозданный JNI-мост (package
│                                   com.adguard.trusttunnel — совпадает
│                                   с оригиналом, это обязательно для JNI)
├── engine/
│   ├── VpnEngine.kt             — общий интерфейс
│   ├── TrustTunnelEngine.kt     — рабочая реализация
│   ├── AmneziaWgEngine.kt       — официальный WireGuard-android
│   ├── XraySingboxEngine.kt     — честная заглушка
│   ├── FreeTurnEngine.kt        — частичная реализация
│   └── AppVpnService.kt         — исправленный VpnService
├── dispatcher/EngineDispatcher.kt
└── ui/MainActivity.kt           — добавлен обязательный VpnService.prepare()
```
