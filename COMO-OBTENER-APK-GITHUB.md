# Obtener la APK con GitHub Actions (sin instalar nada)

GitHub compila el proyecto en la nube y te deja la APK lista para descargar.
Necesitas solo una cuenta de GitHub (gratis).

## 1. Crear el repositorio

1. Entra a <https://github.com/new>.
2. Nombre: `sms-aviso-controlado` (o el que quieras).
3. Marca **Private** (privado) si no quieres que sea público. Da igual para compilar.
4. **No** marques "Add a README". Crea el repo vacío.

## 2. Subir el código

### Opción A — desde la web (lo más simple)

1. En el repo vacío, pulsa **uploading an existing file**.
2. Arrastra **todo el contenido** de la carpeta `sms-aviso-controlado`
   (las carpetas `app/`, `gradle/`, `.github/` y los archivos `build.gradle.kts`,
   `settings.gradle.kts`, `gradle.properties`, etc.).
   - Importante: sube el contenido, no la carpeta contenedora. En la raíz del repo
     deben quedar `settings.gradle.kts` y la carpeta `.github/`.
3. Pulsa **Commit changes**.

### Opción B — con Git desde tu PC

```bash
cd "sms-aviso-controlado"
git init
git add .
git commit -m "Primera version"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/sms-aviso-controlado.git
git push -u origin main
```

## 3. Esperar la compilación

1. Al hacer el push/commit, GitHub lanza el workflow automáticamente.
2. Ve a la pestaña **Actions** del repo.
3. Abre la ejecución **Build APK** en curso. Tarda unos 3–6 minutos.
4. Si sale un check verde ✅, está lista.

Si falla ❌: abre el paso "Build debug APK", copia el error y me lo pasas.

## 4. Descargar la APK

1. En la misma pantalla de la ejecución terminada, baja hasta **Artifacts**.
2. Descarga **sms-aviso-controlado-debug-apk** (es un `.zip`).
3. Descomprímelo: dentro está `app-debug.apk`.

## 5. Instalar en el teléfono

1. Pasa `app-debug.apk` al teléfono (cable, Drive, WhatsApp a ti mismo, etc.).
2. Ábrelo. Android pedirá permitir "instalar apps de esta fuente": acéptalo.
3. Al abrir la app, concede los permisos de **SMS, Contactos, Teléfono y
   Notificaciones**. Sin ellos no funciona.

> Es una APK de depuración (*debug*), firmada con la clave de depuración estándar.
> Sirve perfectamente para uso propio. No se puede publicar en Google Play así.

## 6. Volver a compilar tras un cambio

Cada vez que subas un cambio al repo, Actions genera una APK nueva.
También puedes lanzarlo a mano en **Actions → Build APK → Run workflow**.
