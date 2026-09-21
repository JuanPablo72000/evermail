# Evermail

Evermail es un cliente de correo de escritorio desarrollado con **Java 21 y JavaFX**. Su objetivo es ofrecer acceso al correo de Google y Microsoft de forma **ligera, ágil, segura y privada**, con almacenamiento local protegido mediante cifrado.

La aplicación se comunica directamente con los proveedores: OAuth2 para autorizar el acceso e IMAP/SMTP para recibir y enviar correo. No incorpora un servidor intermediario propio.

## Alcance del MVP

- Iniciar sesión mediante OAuth2 y cerrar sesión.
- Consultar la bandeja de entrada.
- Leer correos recibidos.
- Redactar y enviar correos nuevos.
- Mostrar inicialmente los **50 correos más recientes** de la bandeja.
- Añadir otros **50 correos** cada vez que se seleccione **Cargar más**.
- Conservar los correos recibidos y enviados en almacenamiento persistente del dispositivo para reducir solicitudes al proveedor.

Guardar correos enviados no implica una pantalla de enviados en este MVP. Responder, reenviar, gestionar etiquetas y descargar adjuntos no forman parte del alcance mínimo acordado. Existen clases de borradores, etiquetas y adjuntos como infraestructura previa; su presencia no convierte esas funciones en requisitos del MVP.

## Rendimiento esperado

Estos tiempos son **objetivos de aceptación**, no resultados medidos ni garantías ya implementadas.

| Operación | Objetivo máximo |
|---|---:|
| Pantalla de carga inicial | 5 segundos |
| Carga de la bandeja de entrada | 5 segundos |
| Abrir un correo | 2 segundos |
| Cargar el contenido después de abrirlo | 2 segundos adicionales |
| Tiempo total para poder leer un correo | 4 segundos |
| Envío de un correo nuevo | 4 segundos |

El tiempo que la persona necesite para autorizar OAuth2 queda separado del arranque. La caché local debe reducir la dependencia de la red y las operaciones de entrada/salida deben ejecutarse fuera del hilo de interfaz.

Los tiempos de red dependen del proveedor y de la conexión. Falta implementar y medir el comportamiento ante demoras: informar el estado real sin bloquear la interfaz, simular un éxito ni reenviar automáticamente un mensaje cuyo resultado sea incierto.

## Seguridad y privacidad

El diseño evita almacenar contraseñas de correo y utiliza OAuth2 con PKCE. La implementación incluye AES-256-GCM para tokens y cuerpos de correos/borradores, y una clave por cuenta almacenada mediante java-keyring. También existe cifrado de archivos para la infraestructura de adjuntos.

**La base SQLite completa no está cifrada.** Direcciones, asuntos, fechas y otros metadatos permanecen legibles; el cifrado se aplica a campos específicos. Los cuerpos y tokens se descifran en memoria al utilizarlos.

La implementación de seguridad todavía necesita correcciones y pruebas: consistencia entre base y almacén de claves, recuperación ante errores y manejo seguro de rutas de adjuntos. El cierre de sesión actual elimina la cuenta y sus datos dependientes en SQLite; todavía no revoca tokens remotos ni limpia claves y archivos locales asociados.

## Estado actual

**Prototipo en desarrollo; los flujos del MVP aún no funcionan de extremo a extremo.**

| Área | Estado real |
|---|---|
| Java 21, Gradle y dependencias | Configurados; compilación y empaquetado comprobados con Java 21. |
| Pantalla de acceso | FXML, estilos e imágenes existentes; carga comprobada. Botones sin conexión a autenticación. |
| Controladores, navegación y pantalla de carga | Pendientes; App carga directamente la pantalla de acceso. |
| OAuth2 y sesiones IMAP/SMTP | Clases implementadas, con correcciones pendientes y sin validación con cuentas reales en la revisión. |
| SQLite, DAO y repositorios | Implementados parcialmente; faltan inicialización/migraciones y transacciones completas. Hay un error en las consultas de MailDAO. |
| Sincronización y lectura | Lógica existente; pendientes correcciones MIME, deduplicación, fechas y separación de bandeja/otros correos. |
| Cargar más | Pendiente; el código actual utiliza un límite de 50, sin paginación del MVP. |
| Composición y envío | Servicios y fachada existentes; interfaz y recuperación consistente después de SMTP pendientes. |
| Pruebas automatizadas | JUnit configurado, pero no existen pruebas en src/test. |
| Objetivos de rendimiento | Pendientes de implementar y medir. |

Bloqueos conocidos: consultas que omiten sender_display_name, esquema sin inicialización automática, configuración IMAP con prefijo incorrecto, autorización limitada a dos segundos y guardados que pueden quedar incompletos. Los diagramas detallan las diferencias entre el diseño y el código actual.

## Ejecutar el prototipo

El objetivo soportado es **Java 21**. Utiliza JDK 21 tanto para ejecutar Gradle como para compilar; la toolchain declarada no fija por sí sola el JDK que inicia Gradle. En la revisión, la construcción con Java 24 falló antes de compilar.

En Windows, con JAVA_HOME apuntando a tu instalación de JDK 21:

~~~powershell
.\gradlew.bat --version
.\gradlew.bat test assemble
.\gradlew.bat run
~~~

La primera ejecución puede descargar Gradle y dependencias. Actualmente run abre únicamente la pantalla de acceso, sin iniciar los servicios. test informa NO-SOURCE mientras no se incorporen pruebas; una compilación correcta no valida los flujos del MVP.

La lógica de configuración existente espera un archivo .env con GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, MICROSOFT_CLIENT_ID y MICROSOFT_CLIENT_SECRET. Esta exigencia necesita corregirse: Microsoft está modelado como cliente público y no utiliza ese secreto. No se necesitan esas credenciales para mostrar la pantalla actual. No publiques credenciales ni archivos .env.

La ruta de datos actual es %APPDATA%\Evermail\evermail.db. El prototipo está orientado a Windows; otras plataformas no están verificadas. No se deben borrar bases existentes para sustituir las migraciones pendientes.

## Arquitectura

La dirección principal de llamadas es:

controller → facade → service → repository → dao

- **controller / navigation:** interacción y navegación previstas, todavía pendientes.
- **facade:** construye Task de JavaFX; el consumidor decide cuándo ejecutarlas.
- **service:** lógica síncrona de autenticación y correo, sin dependencias de JavaFX.
- **repository:** combina DAO y coordina el cifrado de campos.
- **dao:** acceso SQL; utiliza SqliteConnectionProvider, ubicado actualmente en repository.
- **model, config, util, exception:** modelos, configuración, utilidades y errores compartidos.

Esta dirección describe responsabilidades, no una prohibición absoluta de dependencias entre paquetes: existen colaboraciones entre servicios y el proveedor de conexión es infraestructura compartida.

## Tecnologías

Java 21 · JavaFX 21.0.6 · Gradle Wrapper 8.13 · Jakarta Mail / Eclipse Angus · SQLite JDBC · dotenv-java · Gson · java-keyring · Lombok · JUnit Jupiter.

## Documentación técnica

Los diagramas describen el estado actual salvo donde se identifica explícitamente un diseño pendiente:

- [Modelo de datos ER](docs/diagrams/er-diagram.md)
- [Modelos Java](docs/diagrams/uml-model.md)
- [DAO y conexión](docs/diagrams/uml-dao.md)
- [Repositorios](docs/diagrams/uml-repositories.md)
- [Servicios](docs/diagrams/uml-service.md)
- [Fachadas](docs/diagrams/uml-facade.md)
- [Controladores propuestos](docs/diagrams/uml-controllers.md)
- [Configuración](docs/diagrams/uml-config.md)
- [Utilidades](docs/diagrams/uml-util.md)
- [Excepciones](docs/diagrams/uml-exception.md)

## Autor y licencia

**Juan Pablo Rodríguez Hurtado** — Ingeniería en Sistemas Computacionales.

Distribuido bajo la [licencia MIT](LICENSE).
