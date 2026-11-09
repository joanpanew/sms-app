# Estado Programado (WhatsApp)

App Android que **publica un video (o foto) en tu estado de WhatsApp a la hora que elijas**,
por ejemplo a las 5:00 a. m. mientras duermes.

## Cómo funciona

WhatsApp no tiene ninguna función oficial para programar estados, así que la app hace
lo mismo que harías tú con el dedo:

1. A la hora programada salta una alarma exacta (como un despertador, funciona aunque
   el teléfono esté en reposo).
2. La app enciende la pantalla, quita el bloqueo (si no tienes PIN) y comparte el video con WhatsApp.
3. Un **servicio de accesibilidad** pulsa **"Mi estado"** y luego **"Enviar"**.
4. Vuelve a la pantalla de inicio, apaga la pantalla y te deja una notificación con el resultado.

## Requisitos (importantes)

| Requisito | Por qué |
|---|---|
| **Activar el servicio de accesibilidad** "Estado Programado" | Es lo que pulsa los botones en WhatsApp. Sin esto no funciona. |
| **Pantalla sin PIN / patrón / huella esa noche** | Ninguna app puede escribir tu PIN. Pon el bloqueo en *Ninguno* o *Deslizar* antes de dormir. |
| Teléfono **encendido** (mejor cargando) | Si está apagado a esa hora, se publica al encender solo si no han pasado más de 30 min. |
| Quitar la **optimización de batería** para la app | Algunas marcas (Xiaomi, Huawei, Samsung…) matan las apps en segundo plano. En Xiaomi activa también "Inicio automático". |
| Video de **máximo 60 s** aprox. | Es el límite de WhatsApp para estados; si es más largo, WhatsApp lo corta. |

La app muestra todo esto en la sección *"1. Preparar el teléfono"* con botones para arreglarlo.

## Uso

1. Instala la APK (ver abajo) y ábrela.
2. Pulsa **Arreglar** en cada aviso ⚠️ de la sección 1.
3. Elige el video, escribe un texto opcional, la fecha y la hora → **Programar**.
4. **Haz primero la prueba** con *"Probar: publicar en 20 segundos"* y deja el teléfono quieto
   (puedes apagar la pantalla). Así compruebas que funciona en tu teléfono y tu versión de WhatsApp.

Puedes programar varios estados; la lista muestra si cada uno quedó PUBLICADO o FALLÓ y por qué.

## Obtener la APK

Cada push al repositorio compila en GitHub Actions. En **Actions → Build APK → (última ejecución)
→ Artifacts** descarga **estado-programado-whatsapp-apk**, descomprime y pasa `estado-debug.apk`
al teléfono.

## Limitaciones

- Depende de los textos de WhatsApp ("Mi estado", "My status"…) y del botón de enviar. Si
  WhatsApp cambia su diseño puede dejar de funcionar hasta ajustar la app.
- Es automatización no oficial: úsala para tu propia cuenta y con moderación.
- La app no envía datos a ningún servidor; el servicio de accesibilidad solo actúa durante los
  ~2 minutos siguientes a cada hora programada.
