# Evermail

Evermail es un cliente de correo de escritorio desarrollado con **Java 21 y JavaFX**. Su objetivo es ofrecer acceso al correo de Google y Microsoft de forma **ligera, ágil, segura y privada**, con almacenamiento local protegido mediante cifrado.

La aplicación se comunica directamente con los proveedores: OAuth2 para autorizar el acceso e IMAP/SMTP para recibir y enviar correo. Las imágenes externas de los mensajes se descargan mediante un [proxy de Azure configurable](docs/desarrollo/proxy-imagenes.md).

## Alcance del MVP

La [especificación de requisitos del sistema](docs/requisitos/requisitos-del-sistema.md) desarrolla este alcance con criterios de aceptación y decisiones abiertas. Es un borrador inicial para revisión.

- Iniciar sesión mediante OAuth2 y cerrar sesión.
- Consultar la bandeja de entrada.
- Leer correos recibidos en HTML seguro o texto, con alternativa de texto disponible.
- Redactar y enviar correos nuevos en texto plano.
- Mostrar inicialmente los **50 correos más recientes** de la bandeja.
- Añadir otros **50 correos** cada vez que se seleccione **Cargar más**.
- Conservar los correos recibidos y enviados en almacenamiento persistente del dispositivo para reducir solicitudes al proveedor.

Guardar correos enviados no implica una pantalla de enviados en este MVP. Responder, reenviar, gestionar etiquetas y descargar adjuntos no forman parte del alcance mínimo acordado. Existen clases de borradores, etiquetas y adjuntos como infraestructura previa; su presencia no convierte esas funciones en requisitos del MVP.

## Rendimiento esperado

La lectura híbrida, sus límites y migración se describen en [Lectura HTML/texto](docs/desarrollo/lector-correo.md). Las [vistas JavaFX](docs/desarrollo/previsualizar-interfaz.md) están conectadas al presentador y al backend. Google fue confirmado por el usuario; Microsoft continúa en diagnóstico.

Estos tiempos son **objetivos de mejora pospuestos para el MVP de uso personal**,
por decisión del usuario el 11 de octubre de 2026; no son garantías implementadas.

| Operación | Objetivo máximo |
|---|---:|
| Pantalla de carga inicial | 5 segundos |
| Carga de la bandeja de entrada | 5 segundos |
| Abrir un correo | 2 segundos |
| Cargar el contenido después de abrirlo | 2 segundos adicionales |
| Tiempo total para poder leer un correo | 4 segundos |
| Envío de un correo nuevo | 4 segundos |

El tiempo que la persona necesite para autorizar OAuth2 queda separado del arranque. La caché local debe reducir la dependencia de la red y las operaciones de entrada/salida deben ejecutarse fuera del hilo de interfaz.

Los tiempos de red dependen del proveedor y de la conexión. La interfaz informa cargas y errores y bloquea reenvíos inciertos; falta medir los flujos con cuentas reales. No se simula éxito ni se reenvía automáticamente un mensaje cuyo resultado sea incierto.

## Seguridad y privacidad

El diseño evita almacenar contraseñas de correo y utiliza OAuth2 con PKCE. La implementación incluye AES-256-GCM para tokens y cuerpos de correos/borradores, y una clave por cuenta almacenada mediante java-keyring. También existe cifrado de archivos para la infraestructura de adjuntos.

**La base SQLite completa no está cifrada.** Direcciones, asuntos, fechas y otros metadatos permanecen legibles; el cifrado se aplica a campos específicos. Los cuerpos y tokens se descifran en memoria al utilizarlos.

La integración conserva PKCE y validación OIDC, exige TLS para el correo y evita mostrar credenciales en la interfaz o errores. El cierre de sesión elimina la cuenta, sus datos dependientes en SQLite y su clave, con recuperación ante fallos. No revoca el consentimiento remoto. Los adjuntos no forman parte del MVP. Consulta las [protecciones y límites de seguridad](docs/desarrollo/configuracion-oauth.md#protecciones-conservadas-y-reforzadas); no se afirma ausencia total de vulnerabilidades.

## Estado actual

**Integración de interfaz y backend implementada; Google confirmado por el usuario,
Microsoft personal con fallos de IMAP/SMTP aún pendientes.**

| Área | Estado real |
|---|---|
| Java 21, Gradle y dependencias | Configurados; compilación y empaquetado comprobados con Java 21. |
| Pantalla de acceso | FXML adaptable con botones conectados a Google/Microsoft y cancelación de autorización. |
| Controladores, navegación y pantalla de carga | Arranque, restauración, recuperación y navegación conectados mediante MailWindow y MailPresenter. |
| OAuth2 y sesiones IMAP/SMTP | Google confirmado por el usuario. Microsoft renueva OAuth pero rechaza IMAP y SMTP. |
| SQLite, DAO y repositorios | Migraciones y transacciones con pruebas. La versión 2 conserva los cuerpos anteriores y añade almacenamiento híbrido cifrado. |
| Sincronización y lectura | Bandeja con caché y lector HTML/texto seguro conectados; Google confirmado por el usuario. |
| Cargar más | Paginación en bloques de 50; funcionamiento confirmado por el usuario. |
| Composición y envío | Formulario conectado; estados de aceptación, guardado e incertidumbre diferenciados. |
| Pruebas automatizadas | Suite JUnit de backend, integración JavaFX, OAuth, persistencia y lectores disponible en src/test. |
| Objetivos de rendimiento | Presupuestos implementados; lectura probada localmente. Pendiente medición de los flujos completos con cuentas reales. |

Actualización del 11 de octubre de 2026: el usuario confirmó el funcionamiento de
Google, la restauración de sesión al reabrir y «Cargar más». Microsoft personal
sigue en diagnóstico por rechazo del acceso IMAP y de la autorización SMTP.
Los objetivos de velocidad se posponen por decisión del usuario para el MVP de
uso personal; no se consideran cumplidos ni garantizados. El instalador y el
ejecutable nativo quedan aplazados a petición del usuario.
Consulta el [estado de aceptación y las pruebas de errores](docs/pruebas/aceptacion-mvp.md)
para distinguir la evidencia vigente de las referencias históricas de este README.

## Ejecutar el prototipo

El objetivo soportado es **Java 21**. Utiliza JDK 21 tanto para ejecutar Gradle como para compilar; la toolchain declarada no fija por sí sola el JDK que inicia Gradle. En la revisión, la construcción con Java 24 falló antes de compilar.

En Windows, con JAVA_HOME apuntando a tu instalación de JDK 21:

~~~powershell
.\gradlew.bat --version
.\gradlew.bat test assemble
.\gradlew.bat run
~~~

La primera ejecución puede descargar Gradle y dependencias. `run` inicia el backend y restaura la sesión o muestra acceso. `test` ejecuta la suite automatizada; una compilación correcta no valida por sí sola los flujos completos con proveedores reales. `run --args="--preview=main"` abre solo la previsualización con datos ficticios, sin servicios.

Copia `.env.example` como `.env` y completa los valores del proveedor que vayas a utilizar: `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET`, o `MICROSOFT_CLIENT_ID`. Microsoft usa un cliente público sin secreto y un retorno local configurable mediante `MICROSOFT_REDIRECT_PORT` (53682 por defecto). Sigue la [guía de configuración OAuth](docs/desarrollo/configuracion-oauth.md). No publiques credenciales ni archivos `.env`.

La ruta de datos actual es %APPDATA%\Evermail\evermail.db. El prototipo está orientado a Windows; otras plataformas no están verificadas. No se deben borrar bases existentes para sustituir las migraciones pendientes.

## Arquitectura

La dirección principal de llamadas es:

ui → presentation → application → service → repository → dao

- **ui / presentation / navigation:** FXML, renderizado, acciones de usuario, estados y rutas.
- **application:** coordina sesión, cancelación y trabajo fuera del hilo visual.
- **controller / facade:** componentes anteriores reutilizables y fachadas Task; la interfaz principal utiliza el coordinador.
- **service:** lógica síncrona de autenticación y correo, sin dependencias de JavaFX.
- **repository:** combina DAO y coordina el cifrado de campos.
- **dao:** acceso SQL; utiliza SqliteConnectionProvider, ubicado actualmente en repository.
- **model, config, util, exception:** modelos, configuración, utilidades y errores compartidos.

Esta dirección describe responsabilidades, no una prohibición absoluta de dependencias entre paquetes: existen colaboraciones entre servicios y el proveedor de conexión es infraestructura compartida.

## Tecnologías

Java 21 · JavaFX 21.0.6 · Gradle Wrapper 8.13 · Jakarta Mail / Eclipse Angus · SQLite JDBC · dotenv-java · Gson · java-keyring · Lombok · JUnit Jupiter.

## Documentación técnica

El [índice de documentación](docs/README.md) organiza los requisitos, el diseño, las pruebas y las notas técnicas. Los diagramas existentes se han reubicado sin una revisión completa de su vigencia; no sustituyen al futuro manual de arquitectura.

- [Modelo de datos ER](docs/datos/diagramas/er-diagram.md)
- [Modelos Java](docs/arquitectura/diagramas/uml-model.md)
- [DAO y conexión](docs/arquitectura/diagramas/uml-dao.md)
- [Repositorios](docs/arquitectura/diagramas/uml-repositories.md)
- [Servicios](docs/arquitectura/diagramas/uml-service.md)
- [Fachadas](docs/arquitectura/diagramas/uml-facade.md)
- [Controladores propuestos](docs/arquitectura/diagramas/uml-controllers.md)
- [Configuración](docs/arquitectura/diagramas/uml-config.md)
- [Utilidades](docs/arquitectura/diagramas/uml-util.md)
- [Excepciones](docs/arquitectura/diagramas/uml-exception.md)

## Autor y licencia

**Juan Pablo Rodríguez Hurtado** — Ingeniería en Sistemas Computacionales.

Distribuido bajo la [licencia MIT](LICENSE).
