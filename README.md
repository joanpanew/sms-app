# SMS Aviso Controlado

> Este repositorio incluye también **[Estado Programado](estado/README.md)**: app para
> publicar un video en tu estado de WhatsApp a una hora programada (p. ej. 5:00 a. m.).

App Android para enviar un **SMS de aviso a tus contactos** de forma **controlada**:
uno por uno, con pausas y variación aleatoria, para reducir el riesgo de que la
operadora bloquee la línea por spam o de que Android corte los envíos por exceso
de mensajes por minuto.

> **Uso responsable.** Envía avisos únicamente a personas que te dieron su número
> y esperan tu comunicación. No es una herramienta de publicidad masiva. Las
> respuestas de baja (STOP, BAJA, CANCELAR...) se excluyen automáticamente.
> Cada SMS lo cobra tu operadora. Cumple la ley de protección de datos y las
> condiciones de tu operadora.

## Qué hace

- **Cola de destinatarios** desde tus contactos (selección múltiple con buscador).
- **Mensaje con plantilla**: `{nombre}` y `{nombre_completo}` se sustituyen por cada contacto.
- **Control de ritmo**:
  - Segundos entre SMS + variación aleatoria (± s) para que no sea un patrón exacto.
  - Topes por **minuto**, por **hora** y por **día**.
  - **Lotes**: tras N envíos, pausa larga configurable.
  - **Ventana horaria**: solo envía entre la hora de inicio y la de fin.
- **Servicio en primer plano** con notificación de progreso (Pausar / Reanudar / Detener).
  El envío continúa aunque cierres la pantalla.
- **Reintento automático** cuando Android devuelve "límite del sistema"
  (`RESULT_ERROR_LIMIT_EXCEEDED`): reencola el número y enfría 2 minutos.
- **Detección de bajas**: si un contacto responde STOP/BAJA/CANCELAR/etc., se añade
  a la lista de exclusión y no se le vuelve a escribir.
- **Historial** de cada intento (enviado / entregado / fallido + motivo) y
  **exportación a CSV**.
- **Doble SIM**: elige con qué tarjeta enviar.

## Valores por defecto (conservadores)

| Ajuste | Valor | Efecto |
|---|---|---|
| Segundos entre SMS | 15 | ~1 cada 15–25 s |
| Variación aleatoria | 10 | |
| Máx / minuto | 3 | |
| Máx / hora | 80 | |
| Máx / día | 400 | |
| Tamaño de lote | 30 | pausa cada 30 envíos |
| Pausa entre lotes | 8 min | |
| Ventana horaria | 8:00–21:00 | |

Ajusta según tu operadora. Si te llegan fallos por "límite del sistema", **sube**
los segundos entre SMS y **baja** el máximo por minuto.

## Cómo obtener la APK

No se entrega un `.apk` precompilado. Elige una vía:

1. **GitHub Actions (recomendado, sin instalar nada).** Ver
   [`COMO-OBTENER-APK-GITHUB.md`](COMO-OBTENER-APK-GITHUB.md).
2. **En tu servidor Ubuntu / VPS.** Ejecuta [`build-en-vps.sh`](build-en-vps.sh).
3. **Android Studio.** Abre la carpeta, espera a que sincronice y
   `Build > Build App Bundle(s) / APK(s) > Build APK(s)`.

La salida siempre es `app/build/outputs/apk/debug/app-debug.apk` (APK de
depuración, firmada con la clave debug estándar; válida para uso propio,
no publicable en Google Play tal cual).

## Permisos que pide y por qué

| Permiso | Uso |
|---|---|
| `SEND_SMS` | Enviar los avisos. |
| `RECEIVE_SMS` | Detectar respuestas de baja (STOP/BAJA...). No se reenvía nada a ningún sitio. |
| `READ_CONTACTS` | Elegir destinatarios de tu agenda. |
| `READ_PHONE_STATE` | Listar las SIM activas para elegir tarjeta. |
| `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` | Servicio de envío con notificación de progreso. |

## Stack técnico

Kotlin · minSdk 26 · targetSdk 33 · compileSdk 34 · View Binding · Room ·
Coroutines · Servicio en primer plano. Sin librerías de red: la app no envía
datos a ningún servidor.

## Estructura

```
app/src/main/java/com/joanpa/smsaviso/
  App.kt                       Application + canal de notificación
  data/                        Room: Recipient, SendLog, OptOut + DAOs + AppDatabase
  prefs/SettingsStore.kt       Ajustes (SharedPreferences)
  sms/
    SmsSenderService.kt        Bucle de envío con control de ritmo
    RateLimiter.kt             Cálculo de esperas y topes
    SimHelper.kt               SIM activas / SmsManager por suscripción
    IncomingSmsReceiver.kt     Detección de bajas
    SenderBus.kt               Estado servicio -> UI
  ui/
    MainActivity.kt            Panel de control
    ContactsActivity.kt        Selección de contactos
    HistoryActivity.kt         Historial + exclusiones + CSV
  util/                        CsvExporter, extensiones
```

## Limitaciones

- La comparación de números para exclusión/duplicados usa los **últimos 9 dígitos**
  (pensado para una lista de un solo país).
- La entrega ("DELIVERED") depende de que la operadora devuelva el informe; si no
  llega en 45 s, el intento se marca como enviado sin confirmación.
- Enviar muchos SMS seguidos puede tener coste y puede infringir las condiciones
  de tu operadora aunque el ritmo sea bajo. Úsalo con criterio.
