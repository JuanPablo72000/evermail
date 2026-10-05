# Configurar el inicio de sesión de Evermail, paso a paso

Puedes configurar solamente Google o solamente Microsoft. El archivo `.env` contiene
los datos que identifican a Evermail ante esos servicios. **No pongas ahí tu contraseña
de correo ni tokens de sesión.**

## 1. Dónde colocar el archivo

En la ubicación actual del proyecto, la ruta exacta sería:

```text
C:\Users\juanp\OneDrive\Escritorio\Proyectos\evermail\.env
```

Debe quedar junto a `gradlew.bat`, `build.gradle.kts` y `.env.example`, nunca dentro de
`src`, `resources`, `docs` o `build`.

**Antes de poner credenciales, recomiendo copiar el proyecto completo fuera de OneDrive**,
a `C:\Users\juanp\Proyectos\evermail`, y abrir esa copia en tu editor. En ese caso,
el archivo irá en `C:\Users\juanp\Proyectos\evermail\.env`. Esta guía no mueve archivos.
Excluir `.env` de Git no evita que OneDrive lo sincronice.

## 2. Crear `.env`

1. Abre en el Explorador la carpeta del proyecto que elegiste.
2. Activa **Ver → Mostrar → Extensiones de nombre de archivo**.
3. Copia `.env.example` y pega la copia en esa misma carpeta.
4. Renombra la copia a `.env`, con el punto inicial y sin `.txt`. Si ya existe,
   edítalo sin sobrescribir sus valores.
5. Ábrelo con tu editor de código. Debe contener estas líneas:

```dotenv
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
MICROSOFT_CLIENT_ID=
MICROSOFT_REDIRECT_PORT=53682
```

Guarda con **Ctrl + S**, como texto UTF-8. Completarás los valores después del `=`
en los siguientes pasos. No escribas credenciales reales en `.env.example`, porque
esa plantilla sí se comparte.

## 3. Google: crear las credenciales

### Preparar el registro

1. Abre [Google Cloud Console](https://console.cloud.google.com/) e inicia sesión.
2. En el selector de proyectos superior, crea un proyecto llamado **Evermail** y selecciónalo.
3. Ve a **Google Auth Platform → Branding → Get started / Comenzar**.
4. Pon **Evermail** como nombre y tu correo como contacto y soporte.
5. Selecciona **External / Externo** como público y termina el formulario.
6. En **Audience → Test users → Add users**, agrega el Gmail con el que probarás
   Evermail y guarda. Mantén el estado **Testing / Pruebas**.
7. En **Data Access → Add or Remove Scopes**, agrega estos permisos, actualiza la
   selección y guarda. Usa la entrada manual si alguno no aparece en el buscador:

```text
openid
https://www.googleapis.com/auth/userinfo.email
https://www.googleapis.com/auth/userinfo.profile
https://mail.google.com/
```

Sirven para identificarte y trabajar con tu correo. Los menús pueden aparecer en inglés.
[Guía oficial del consentimiento](https://developers.google.com/workspace/guides/configure-oauth-consent).

### Copiar los valores a `.env`

1. Ve a **Google Auth Platform → Clients / Clientes → Create client / Crear cliente**.
2. Elige **Desktop app / Aplicación de escritorio**.
3. Escribe **Evermail Windows** y pulsa **Crear**.
4. Copia el **Client ID / ID de cliente** completo después de `GOOGLE_CLIENT_ID=`.
5. Copia el **Client secret / Secreto de cliente** después de `GOOGLE_CLIENT_SECRET=`.
   Guarda esos valores cuando aparezcan al crear el cliente. Si descargas el JSON,
   corresponden a `client_id` y `client_secret`, dentro de `installed`. No pegues
   el JSON completo en `.env` ni lo guardes en el repositorio.
6. Guarda `.env`.

El ID suele terminar en `.apps.googleusercontent.com`. El secreto lo genera Google:
**no es tu contraseña de Gmail**. Ambos valores deben pertenecer al mismo cliente.
[Crear credenciales de escritorio](https://developers.google.com/workspace/guides/create-credentials).

No necesitas registrar una página web para este cliente. Evermail prepara su dirección
local de regreso automáticamente. [Acceso desde aplicaciones de escritorio](https://developers.google.com/identity/protocols/oauth2/native-app).

Esta configuración sirve para pruebas con las cuentas agregadas. Distribuir Evermail
públicamente puede requerir verificación adicional por su acceso al correo.
[Verificación de Google](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification).

## 4. Microsoft: crear las credenciales

### Registrar Evermail

1. Abre [Microsoft Entra](https://entra.microsoft.com/).
2. Ve a **Entra ID → App registrations / Registros de aplicaciones → New registration**.
3. Escribe **Evermail** como nombre.
4. Elige **Cuentas de cualquier directorio organizativo y cuentas Microsoft personales**.
5. Deja vacía por ahora la dirección de redirección y pulsa **Registrar**.
6. En **Overview / Información general**, copia **Application (client) ID / Id. de
   aplicación (cliente)** después de `MICROSOFT_CLIENT_ID=` en `.env`.

No copies el ID de objeto ni el de directorio. No necesitas crear un secreto de Microsoft.
Necesitas acceso a un directorio de Entra y permiso para registrar aplicaciones.
Si el portal te lo impide, solicita ese acceso al administrador; puedes configurar
Google mientras tanto. [Registro oficial](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app).

### Registrar la dirección de regreso

Esta versión de Evermail necesita exactamente:

```text
http://127.0.0.1:53682/callback
```

Microsoft puede rechazar esta dirección en el formulario normal. Puedes agregarla
mediante **Manifest / Manifiesto**, el editor de configuración del registro de Evermail.
[Restricciones de direcciones locales](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url).

1. Abre **Manifest / Manifiesto** dentro del registro.
2. Busca `publicClient`. En el formato actual debe incluir esta configuración:

   ```json
   "publicClient": {
     "redirectUris": [
       "http://127.0.0.1:53682/callback"
     ]
   }
   ```

3. Modifica solo esa sección, conservando el resto del documento y sus comas.
   Si ya tiene otras direcciones, agrega esta a la lista.
4. Pulsa **Save / Guardar**. No pongas la dirección en las secciones `web` o `spa`.

Esa sección registra la aplicación como cliente de escritorio.
[Formato actual del manifiesto](https://learn.microsoft.com/en-us/entra/identity-platform/reference-microsoft-graph-app-manifest#publicclient-attribute).

Si el editor muestra el formato antiguo, con `replyUrlsWithType` en lugar de
`publicClient`, agrega esta entrada a esa lista y guarda:

```json
{
  "url": "http://127.0.0.1:53682/callback",
  "type": "InstalledClient"
}
```

Usa solamente el formato que aparezca en tu editor. Los fragmentos no reemplazan todo
el documento. [Formato antiguo](https://learn.microsoft.com/en-us/entra/identity-platform/reference-app-manifest#replyurlswithtype-attribute).

Mantén `MICROSOFT_REDIRECT_PORT=53682` en `.env`. No cambies `127.0.0.1` por `localhost`,
ni `http` por `https`, ni agregues una barra después de `callback`. Esa dirección
funciona dentro de tu computadora: no tienes que abrir puertos del router.

### Agregar los permisos

1. Abre **API permissions / Permisos de API → Add a permission / Agregar un permiso**.
2. Elige **Microsoft Graph → Delegated permissions / Permisos delegados**.
3. Busca y selecciona `IMAP.AccessAsUser.All` y `SMTP.Send`.
4. Agrega también `openid`, `email`, `profile` y `offline_access` si no están presentes.
5. Pulsa **Add permissions / Agregar permisos**.

Son permisos para actuar con tu autorización al iniciar sesión.
[Referencia de permisos](https://learn.microsoft.com/en-us/graph/permissions-reference).
Evermail solicita los permisos de correo con las direcciones de Outlook indicadas
por Microsoft; no tienes que pegarlas en `.env`.
[Autorización para leer y enviar correo](https://learn.microsoft.com/en-us/exchange/client-developer/legacy-protocols/how-to-authenticate-an-imap-pop-smtp-application-by-using-oauth).

En cuentas escolares o de trabajo, el administrador puede tener que aprobar la aplicación
y permitir IMAP/SMTP con OAuth. No desactives las políticas de seguridad para forzar el acceso.

## 5. Revisar los valores

| Línea | Qué poner después de `=` |
| --- | --- |
| `GOOGLE_CLIENT_ID` | ID del cliente de escritorio de Google. |
| `GOOGLE_CLIENT_SECRET` | Secreto de ese mismo cliente de Google. |
| `MICROSOFT_CLIENT_ID` | ID de aplicación (cliente) del registro de Microsoft. |
| `MICROSOFT_REDIRECT_PORT` | `53682`. |

Pega cada valor completo en una sola línea, sin espacios alrededor del `=`.
Deja vacías las credenciales del proveedor que no usarás. No pongas enlaces a las
consolas, tu correo ni contraseñas en esos campos.

## 6. Abrir Evermail e iniciar sesión

1. Abre la carpeta que contiene `.env` y `gradlew.bat` en el Explorador.
2. Escribe `powershell` en su barra de dirección y pulsa Enter.
3. Ejecuta estas líneas. La ruta corresponde al JDK 21 instalado en esta computadora:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
   .\gradlew.bat run
   ```

4. Pulsa **Continuar con Google** o **Continuar con Microsoft**, según lo configurado.
5. Completa el acceso en la página oficial que abre el navegador y revisa los permisos.
   Tu contraseña se escribe únicamente en la página del proveedor.
6. Regresa a Evermail y comprueba que carga la bandeja. Abre un correo con doble clic
   o Enter. Para probar el envío, envía un mensaje a tu propia cuenta.

Si ejecutas desde el editor, usa JDK 21 y la raíz del proyecto como carpeta de trabajo.
No uses `--preview=...`: ese modo solo muestra pantallas con datos ficticios.
Reinicia Evermail después de modificar `.env`.

Una sesión guardada puede abrir directamente la bandeja. Cerrar la ventana conserva
la sesión; **Cerrar sesión** elimina los datos locales de la cuenta y su clave de cifrado.
No elimina tus correos del proveedor ni revoca por sí mismo los permisos concedidos allí.

## 7. Proteger `.env`

1. **Evita carpetas sincronizadas o compartidas.** Usa la ubicación local del paso 1.
   Pausar OneDrive temporalmente no impide una sincronización futura.
2. **No lo subas a GitHub.** Ya está excluido en `.gitignore`. Desde el proyecto,
   `git check-ignore .env` debe mostrar `.env`. No uses `git add -f .env`.
   La exclusión no elimina copias subidas anteriormente.
3. **Revisa quién puede leerlo.** Clic derecho sobre `.env` → **Propiedades → Seguridad**.
   Otros usuarios normales del equipo no deberían tener acceso. Si lo tienen,
   corrige los permisos de la carpeta con ayuda del administrador, conservando los
   permisos necesarios de tu usuario y del sistema.
4. **No compartas capturas, ZIP ni JSON con credenciales.** Guarda respaldos en un
   gestor de contraseñas o almacenamiento cifrado de acceso personal. Excluye `.env`
   cuando compartas o empaquetes el proyecto.
5. **Si publicaste el secreto de Google**, reemplázalo desde ese cliente en Google Cloud,
   invalida el anterior y actualiza `.env`. Borrar el archivo no elimina las copias previas.
   Si también se expuso una sesión, revoca el acceso de Evermail desde la seguridad
   de la cuenta afectada.

`.env` es texto normal: **no está cifrado por tener ese nombre**. Los identificadores
de cliente no son contraseñas. El secreto de una aplicación de escritorio tampoco
puede mantenerse oculto si se distribuye dentro del programa; la protección del
inicio de sesión incluye medidas adicionales.
[Seguridad de aplicaciones nativas](https://www.rfc-editor.org/rfc/rfc8252.html).

## 8. Si algo falla

| Problema | Qué revisar |
| --- | --- |
| Falta configuración | Nombre `.env`, ubicación, valores completos y reinicio de Evermail. |
| Google rechaza tu cuenta de prueba | Que ese Gmail esté en **Audience → Test users** del proyecto del cliente. |
| Google indica cliente inválido | Que ID y secreto sean del mismo cliente **Desktop app**. |
| Microsoft muestra `AADSTS50011` | Dirección exacta `http://127.0.0.1:53682/callback`, registrada como cliente de escritorio. |
| Puerto local ocupado | Cierra otra instancia de Evermail. Si cambias el puerto, usa uno entre 1024 y 65535 y actualiza tanto `.env` como la dirección registrada en Microsoft. |
| Microsoft pide aprobación | Solicita autorización al administrador para la aplicación y sus permisos. |
| Autorizas, pero no puedes leer o enviar | Comprueba que el buzón permita IMAP/SMTP con OAuth; consulta al administrador si es una cuenta de organización. |
| Solo ves datos de ejemplo | Ejecuta sin `--preview`. |

Para pedir ayuda, comparte el error ocultando datos personales, credenciales y códigos.
No compartas `.env` ni la dirección completa de regreso que muestra el navegador.

## Protecciones conservadas y reforzadas

- El acceso usa el navegador del sistema y comprueba que la respuesta corresponda
  al intento iniciado por Evermail (PKCE, estado y verificación de identidad).
- El intercambio con los proveedores y las conexiones de correo exigen cifrado y
  comprobación del servidor. El regreso local solo escucha dentro de tu equipo.
- Los tokens y cuerpos de correo guardados se cifran; sus claves están en el almacén
  del sistema operativo. Los metadatos del correo no están todos cifrados.
- Los tokens de sesión no se guardan en `.env` ni se muestran en la interfaz.
- El lector bloquea scripts y recursos externos. Cambiar de sesión limpia la vista
  y descarta resultados pendientes de la sesión anterior.

Estas medidas no garantizan protección frente a programas maliciosos con acceso a tu
usuario o a un administrador del equipo, ni ausencia total de vulnerabilidades.

## Estado de las comprobaciones

Las pruebas automatizadas cubren la interfaz y comprobaciones de seguridad con servicios
simulados. **Aún falta confirmar el acceso y el envío con cuentas reales configuradas.**
Esta guía permite hacer esa prueba; no significa que ya se haya realizado.
