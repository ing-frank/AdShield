# AD Shield

Aplicación Android personal que reduce publicidad, rastreadores y dominios
potencialmente maliciosos en todo el teléfono mediante una **VPN local**
(`android.net.VpnService`). Sin root, sin modificar otras aplicaciones y sin
servidor externo propio.

> **Estado de verificación.** El proyecto se escribió en un entorno sin Android SDK,
> por lo que **no ha sido compilado ni ejecutado en un dispositivo**. La lógica que no
> depende de Android (motor de filtrado, paquetes DNS, lector de listas, estadísticas,
> validación de dominios, diagnóstico) sí se compiló y se probó: 53 pruebas, todas
> correctas. El servicio VPN, Room, las pantallas Compose y WorkManager están sin
> compilar: espera tener que corregir algún error en la primera compilación.

## Cómo funciona

```
App (YouTube, Chrome, juego…)
        │  consulta DNS: "¿IP de ads.example.com?"
        ▼
AD Shield VPN (solo recibe consultas DNS)
        │
        ▼
AdBlockEngine ── lista blanca → lista negra → seguridad → publicidad → rastreo
        │
   ┌────┴─────┐
 BLOCK      ALLOW
   │          │
0.0.0.0    se reenvía al DNS de tu red y se devuelve la respuesta
```

- La VPN solo enruta **una dirección**: un servidor DNS virtual (`10.215.173.2`).
  Páginas, vídeos y mensajes siguen por la conexión normal y nunca pasan por la app.
- Un dominio bloqueado recibe `0.0.0.0` (o `::`), así la app no puede conectar con él.
- Una regla sobre `example.com` cubre también sus subdominios.

## Qué hace y qué no

| Sí | No |
|---|---|
| Bloquea dominios de publicidad, rastreo, telemetría, malware y phishing de listas públicas | No elimina **todos** los anuncios |
| Lista blanca y lista negra propias | No bloquea anuncios servidos desde el mismo dominio que el contenido (YouTube, Facebook, Instagram, TikTok) |
| Tres modos: Normal, Estricto, Máximo | No filtra apps que usan su propio DNS cifrado (DoH/DoT) |
| Excluir aplicaciones concretas | No funciona con el «DNS privado» de Android en modo estricto (Diagnóstico lo avisa) |
| Estadísticas, actividad, diagnóstico | No inspecciona HTTPS, no instala certificados, no lee contenido |
| Parada inmediata desde la app o la notificación | No puede convivir con otra VPN (Android solo permite una) |

Otras limitaciones conocidas:

- Solo se atienden consultas DNS por **UDP/IPv4** hacia el DNS virtual. Las consultas
  DNS por TCP (poco frecuentes) no se responden.
- La aplicación que hizo cada consulta se obtiene con `getConnectionOwnerUid`
  (Android 10+). Android no siempre la informa; entonces aparece «No identificada».
- Las reglas por aplicación se limitan a **proteger o excluir** la app. Android no
  permite, con este mecanismo, aplicar listas de dominios distintas por aplicación.
- La categoría de un bloqueo es la de la lista de origen; algunas listas mezclan tipos.
- En modo Máximo se cargan en memoria cientos de miles de dominios; en teléfonos con
  poca RAM es preferible Normal o Estricto.

## Privacidad

Se guarda únicamente, y solo en el teléfono: hora, dominio consultado, decisión,
categoría y aplicación. Máximo 2.000 eventos y 32 días de contadores. Se puede
desactivar («Guardar estadísticas») y borrar desde Configuración.

Las consultas permitidas se reenvían al DNS que informe tu red. Solo si la red no
informa ninguno se usan `1.1.1.1` (Cloudflare) y `9.9.9.9` (Quad9) como respaldo.

## Permisos

| Permiso | Para qué |
|---|---|
| `INTERNET` | Reenviar consultas DNS y descargar listas |
| `ACCESS_NETWORK_STATE` | Leer los DNS de la red y comprobar la conexión |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Mantener la VPN con notificación persistente |
| `POST_NOTIFICATIONS` | Mostrar esa notificación (Android 13+). Si se rechaza, la protección funciona igual |
| `RECEIVE_BOOT_COMPLETED` | Opción «Protección al iniciar» |
| Permiso de VPN (diálogo del sistema) | Crear la VPN local |

## Requisitos e instalación

- Android Studio Ladybug (2024.2) o superior, JDK 17, Android SDK 35.
- Teléfono o emulador con Android 8.0 (API 26) o superior.

1. Android Studio → **Open** → carpeta `AdShield`.
2. Esperar la sincronización de Gradle.
3. Ejecutar la configuración `app`.

Línea de comandos:

```
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # pruebas unitarias
./gradlew connectedDebugAndroidTest   # pruebas instrumentadas (emulador o teléfono)
```

## Cómo probar cada función

1. **Activar:** Inicio → «Activar protección» → aceptar notificaciones y el permiso
   de VPN. Debe aparecer el icono de llave/VPN y «Protección activa».
2. **Bloqueo:** en Chrome abre `http://doubleclick.net` → no debe cargar. Abre
   cualquier web normal → debe cargar. En Actividad aparecen ambos dominios.
3. **Lista negra:** agrega `example.com`, ábrelo en el navegador → no carga.
   (Chrome guarda DNS en caché: si ya lo habías abierto, espera un minuto o usa otra pestaña de incógnito.)
4. **Lista blanca:** agrega `doubleclick.net` → vuelve a resolverse.
5. **Aplicaciones:** excluye una app → deja de aparecer en Actividad.
6. **Modos:** Protección → Estricto / Máximo (pide confirmación) → «Actualizar ahora».
7. **Listas:** Protección → «Actualizar ahora». Cada fuente muestra dominios y fecha, o su error.
8. **Diagnóstico:** «Ejecutar diagnóstico» con la protección activa y desactivada.
9. **Emergencia:** «Detener protección» en Inicio o «Detener protección» en la notificación.
10. **Inicio automático:** Configuración → «Protección al iniciar» → reiniciar el teléfono.

## Solución de problemas

| Problema | Qué hacer |
|---|---|
| Una app no funciona | Detén la protección. Si era eso, exclúyela en Aplicaciones o agrega su dominio a la lista blanca (míralo en Actividad) |
| «Error de protección» | Desconecta cualquier otra VPN y vuelve a activar |
| No bloquea nada | Diagnóstico. Causa habitual: «DNS privado» de Android en modo estricto, o listas sin descargar |
| Sin Internet con la protección activa | Detén la protección; revisa Diagnóstico → Internet |
| Las listas no se actualizan | Protección muestra el error de cada fuente. En modo «Automático» solo se descarga con Wi‑Fi |
| Se detiene sola | Quita a AD Shield de la optimización de batería del fabricante |

## Arquitectura

```
Presentation (Compose) → ViewModel → Use Cases → Repository → Data / VPN / Database
```

```
com.adshield
├── AdShieldApp, AppContainer        inyección de dependencias manual
├── domain/
│   ├── model/                       modelos puros
│   ├── repository/                  interfaces
│   └── usecase/                     SaveDomainRule, SetProtectionMode, RunDiagnostics, StatsAggregator
├── data/
│   ├── database/                    Room: entidades, DAO, AppDatabase
│   ├── preferences/                 DataStore (ajustes)
│   ├── blocklist/                   fuentes, descarga, lector de listas
│   ├── repository/                  implementaciones + RuleLoader
│   └── system/                      consultas al sistema para Diagnóstico
├── filter/                          AdBlockEngine, RuleManager, DomainMatcher
├── vpn/                             AdShieldVpnService, VpnManager, VpnState, DnsPacket, BootReceiver
├── stats/                           TrafficRecorder (escrituras por lotes)
├── worker/                          BlocklistUpdateWorker, UpdateScheduler
└── presentation/                    navigation, theme, components, viewmodel, screens
```

Decisiones a tener en cuenta:

- **Ajustes en DataStore, no en Room.** El mandato pedía una entidad `Settings` y
  también DataStore; se usa solo DataStore para no duplicar. En su lugar Room tiene
  `blocklist_sources` (estado de cada descarga).
- **Sin Hilt.** Un contenedor manual basta para este tamaño.
- **Casos de uso solo donde hay lógica.** Las operaciones triviales van del ViewModel al repositorio.
- **Búsqueda rápida.** Las reglas viven en un `HashMap`; cada consulta cuesta una
  búsqueda por etiqueta del dominio, nunca un recorrido de la lista.
- **Pocas escrituras.** Contadores y eventos se acumulan en memoria y se guardan cada 5 s.

## Listas de bloqueo

Definidas en `data/blocklist/BlocklistSources.kt` (un solo archivo para cambiarlas).

| Modo | Lista | Categoría | Licencia |
|---|---|---|---|
| Normal | Peter Lowe's Ad and tracking server list | Publicidad | McRae GPL |
| Normal | EasyList (dominios, vía Firebog) | Publicidad | GPL-3.0 / CC BY-SA 3.0 |
| Normal | EasyPrivacy (dominios, vía Firebog) | Rastreo | GPL-3.0 / CC BY-SA 3.0 |
| Normal | URLhaus (abuse.ch) | Malware | CC0 |
| Normal | Phishing Army | Phishing | CC BY-NC 4.0 |
| Estricto | StevenBlack Unified hosts | Publicidad | MIT |
| Estricto | AdGuard DNS filter (vía Firebog) | Publicidad | GPL-3.0 |
| Estricto | WindowsSpyBlocker | Telemetría | MIT |
| Máximo | HaGeZi Multi PRO | Publicidad | GPL-3.0 |

Las licencias indicadas son las que declaran sus autores; **revísalas antes de
distribuir la app**. Phishing Army no permite uso comercial. Hasta la primera
descarga solo actúa una lista mínima de 34 dominios incluida en la app.

## Pruebas

| Archivo | Qué cubre | Ejecutada |
|---|---|---|
| `DomainMatcherTest`, `RuleManagerTest`, `AdBlockEngineTest` | Motor de filtrado, prioridades, modos | Sí |
| `DnsPacketTest` | Lectura y construcción de paquetes IPv4/UDP/DNS | Sí |
| `HostsParserTest` | Formatos de lista | Sí |
| `StatsAggregatorTest` | Totales y barras de la gráfica | Sí |
| `SaveDomainRuleUseCaseTest`, `RunDiagnosticsUseCaseTest` | Validación de dominios y diagnóstico | Sí |
| `DomainListViewModelTest` | ViewModel de las listas | No (necesita las librerías de AndroidX) |
| `DatabaseTest` (androidTest) | Room, repositorios y carga de reglas | No (necesita emulador) |

Las marcadas «Sí» se ejecutaron con el compilador de Kotlin 2.0.21 fuera de Gradle.
La activación y desactivación reales de la VPN solo pueden probarse en un dispositivo.

## Firmar el APK Release

1. Crear el almacén de claves (una sola vez; guarda el archivo y las contraseñas, sin
   ellos no podrás publicar actualizaciones):

   ```
   keytool -genkeypair -v -keystore adshield-release.jks -alias adshield \
       -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Copiar `keystore.properties.example` como `keystore.properties` en la raíz del
   proyecto y rellenarlo. Ese archivo y los `.jks` están en `.gitignore`.

3. Generar:

   ```
   ./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
   ```

Sin `keystore.properties`, `assembleRelease` genera un APK sin firmar que no se puede
instalar. Alternativa gráfica: Android Studio → Build → Generate Signed App Bundle / APK.

La versión Release usa R8 (minificación). Pruébala en un teléfono antes de
distribuirla: es donde pueden aparecer errores que no se ven en Debug.

**Google Play:** las apps que usan `VpnService` deben cumplir la política de Play
sobre VPN (la VPN debe ser la función principal, declararse en la ficha y no usarse
para manipular anuncios de forma que afecte a la monetización de otras apps). Los
bloqueadores de anuncios para todo el sistema suelen ser rechazados en Play; lo
habitual es distribuirlos por APK directo o F-Droid. Revísalo antes de planificar la publicación.

## Versiones

AGP 8.7.3 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28 · Gradle 8.11.1 · Compose BOM 2024.12.01 ·
Navigation 2.8.5 · Lifecycle 2.8.7 · Room 2.6.1 · DataStore 1.1.1 · WorkManager 2.9.1 ·
Coroutines 1.9.0
