# Requisitos del sistema — Evermail

| Campo | Valor |
| --- | --- |
| Versión documental | 0.1 |
| Fecha | 2026-10-01 |
| Estado | Borrador inicial para revisión; no certifica aceptación del producto |
| Producto | Cliente de correo de escritorio Evermail |
| Alcance | MVP; funciones futuras separadas explícitamente |
| Fuentes | Alcance del README, acuerdos del proyecto y comportamiento preparado en el backend |

## 1. Propósito y uso

Definir qué debe permitir Evermail, qué queda fuera de esta versión y cómo
comprobar su funcionamiento. Este documento guía la implementación de la interfaz,
la selección de pruebas y la aceptación del MVP. No es una descripción de clases
ni un informe que declare terminadas las funciones.

Evermail también es un proyecto personal de aprendizaje. Esta documentación debe
permitir entender las obligaciones del sistema sin conocer previamente su código.

Cada requisito tiene un identificador estable: RF para funciones, RNF para
cualidades y restricciones, y RN para reglas del producto. Los criterios describen
resultados observables; no obligan a una clase o método concretos. Todos los RF y
RNF de esta versión son necesarios para aceptar el MVP, con las condiciones de
medición pendientes indicadas expresamente. Las ideas futuras no son requisitos
de aceptación de esta versión.

## 2. Objetivo, usuarios y contexto

El usuario debe poder acceder a su correo de Google o Microsoft, consultar su
bandeja, leer mensajes y enviar correos nuevos desde una aplicación de escritorio
con almacenamiento local protegido parcialmente mediante cifrado.

| Actor o sistema externo | Participación |
| --- | --- |
| Usuario | Autoriza el acceso, consulta y redacta correo, y cierra sesión |
| Google / Microsoft | Autorizan el acceso y proporcionan los servicios de correo |
| Navegador del sistema | Presenta la autorización del proveedor |
| Almacén de claves del sistema operativo | Protege las claves locales por cuenta |

La aplicación se comunica directamente con los proveedores mediante OAuth2 e
IMAP/SMTP; no requiere un servidor intermediario propio. El entorno objetivo
actual es Windows con Java 21 y JavaFX. Otros sistemas operativos no se consideran
validados. El acceso remoto requiere conexión, una cuenta compatible y la
configuración correspondiente del proveedor.

## 3. Alcance y exclusiones

Incluye acceso y cierre de sesión, restauración de sesión disponible, bandeja con
caché y paginación, lectura en texto o HTML seguro, y envío de mensajes nuevos en
texto plano. Conservar correos enviados localmente no implica incluir una pantalla
de enviados.

Quedan fuera: responder, reenviar, archivar, eliminar correo remoto, gestionar
etiquetas, buscar, notas, calendario, gestión de varias cuentas simultáneas,
borradores persistentes, pantalla de enviados, navegación anterior/siguiente del
lector y envío o descarga de adjuntos. La existencia de clases o controles visuales
relacionados no amplía el alcance. Las imágenes incrustadas admitidas por el lector
no equivalen a una función de gestión de adjuntos.

Los controles de funciones futuras presentes en el diseño deben conservarse sin
acciones funcionales. El espacio de adjuntos se conserva vacío. Esta restricción
respeta el contenido del diseño acordado; no exige implementar esas funciones.

## 4. Requisitos funcionales

### RF-01 — Autorizar acceso

El sistema debe permitir iniciar sesión con Google o Microsoft mediante el
navegador y gestionar la cancelación o el fallo de la autorización.

**Aceptación:** el usuario elige un proveedor; una autorización válida habilita la
sesión correspondiente. Si cancela o falla, recibe un estado comprensible y no
obtiene acceso a datos de otra cuenta. Evermail no solicita la contraseña del
correo en un formulario propio. Debe comprobarse con cada proveedor real.

### RF-02 — Restaurar sesión al iniciar

El sistema debe comprobar la sesión guardada y presentar acceso, bandeja o un
estado recuperable según corresponda.

**Aceptación:** con una sesión local válida se accede a su bandeja sin repetir la
autorización manual. Si se requiere reautorización, se informa al usuario; cuando
la cuenta y su clave local siguen disponibles, puede consultar el contenido
guardado. Un fallo de inicio no se presenta como una sesión lista.

### RF-03 — Consultar y actualizar la bandeja

El sistema debe mostrar inicialmente hasta 50 mensajes recientes, utilizar primero
la caché disponible y permitir actualizar desde el proveedor.

**Aceptación:** la lista presenta los correos por UID remoto descendente dentro de
la vigencia de la carpeta. Si existen menos de 50, muestra los disponibles. Una
página que se solapa con mensajes existentes no crea duplicados. Una actualización
fallida conserva la lista local e informa que no pudo actualizarse. Una bandeja
sin mensajes se distingue de una bandeja que no pudo cargarse.

### RF-04 — Cargar más mensajes

El sistema debe permitir incorporar páginas de hasta 50 mensajes anteriores.

**Aceptación:** cada acción agrega los mensajes disponibles sin duplicar los ya
mostrados. Al agotarse los resultados, el control deja de ofrecer más páginas. Un
fallo conserva la lista y permite reintentar desde el punto anterior.

### RF-05 — Abrir y leer un mensaje

El sistema debe presentar la cabecera y el cuerpo del mensaje seleccionado,
utilizando el cuerpo local cuando esté disponible.

**Aceptación:** un cuerpo guardado puede abrirse sin descargarlo de nuevo. Si falta
y la red no permite recuperarlo, se informa el error sin mostrar otro mensaje como
si fuera el seleccionado. El usuario puede volver a la bandeja conservada.

### RF-06 — Mostrar HTML seguro y alternativa de texto

El sistema debe permitir leer el formato HTML admitido y disponer de una
alternativa legible en texto. No se exige reproducción idéntica de toda plantilla.

**Aceptación:** los mensajes solo de texto se leen normalmente; los mensajes con
HTML permitido conservan su formato compatible. No se ejecutan scripts ni se
descargan imágenes externas automáticamente. Los recursos incompatibles se omiten
o se representan de forma segura. Un mensaje que excede los límites admitidos
produce un error recuperable, sin bloquear la aplicación.

### RF-07 — Redactar y enviar texto plano

El sistema debe permitir redactar un correo nuevo con De, Para, CC, CCO, Asunto y
Mensaje. De se obtiene de la cuenta activa; CC y CCO son opcionales.

**Aceptación:** se comprueban los destinatarios antes de enviar; una dirección
inválida impide el envío y genera un aviso. El mensaje se envía como texto plano.
Durante el envío se evita una segunda solicitud desde el mismo formulario. El
resultado distingue fallo, aceptación del proveedor y registro local completado.
La política exacta de campos vacíos queda registrada en las decisiones abiertas.

### RF-08 — Gestionar envíos con resultado incierto

El sistema debe distinguir un envío confirmado de uno cuya confirmación se perdió.

**Aceptación:** si se pierde la respuesta tras iniciar la entrega, no informa un
éxito confirmado ni reenvía automáticamente. Conserva el estado incierto al
reiniciar; repetir la misma identidad de envío no genera otra entrega SMTP.
Mientras el formulario conserva ese resultado, informa la incertidumbre y evita
reenviarlo como si hubiera fallado antes de enviarse. No se exige restaurar el
borrador visual tras reiniciar en esta versión.

### RF-09 — Cerrar sesión

El sistema debe pedir confirmación antes de cerrar la sesión y eliminar los datos
locales de la cuenta y su clave según la política del MVP.

**Aceptación:** cancelar la confirmación conserva la sesión. Confirmarla limpia
inmediatamente los datos visibles e invalida las operaciones anteriores. Al
completarse, la cuenta y sus datos dependientes se han eliminado localmente. Un
fallo de eliminación se informa y requiere recuperación; no permite seguir usando
una cuenta parcialmente eliminada. No se promete revocar la autorización remota.

### RF-10 — Informar operaciones y errores

El sistema debe mostrar los estados de carga, error, falta de conexión y
reautorización necesarios para utilizar los flujos anteriores.

**Aceptación:** el usuario puede distinguir una operación pendiente de una
terminada; los avisos no contienen tokens, cuerpos de mensajes ni trazas internas.
Cuando la operación admite reintento, este no elimina la caché útil ni provoca
reenvíos automáticos de mensajes inciertos.

## 5. Reglas del producto

| ID | Regla |
| --- | --- |
| RN-01 | La sesión activa determina qué cuenta puede consultar y enviar correo. |
| RN-02 | Una respuesta de una sesión anterior no puede repoblar la interfaz tras cerrar o cambiar la sesión. |
| RN-03 | Guardar un enviado localmente no acredita que el destinatario lo haya recibido; la aceptación SMTP no garantiza entrega final. |
| RN-04 | Cerrar la aplicación no equivale a cerrar sesión: la persistencia necesaria para restaurar debe conservarse. |
| RN-05 | La conservación de datos después de cerrar sesión es una propuesta futura, no la política vigente del MVP. |
| RN-06 | Los botones futuros y el espacio de adjuntos conservan el contenido del diseño sin efectuar acciones de esas funciones. |

## 6. Requisitos no funcionales

| ID | Requisito | Criterio de aceptación |
| --- | --- | --- |
| RNF-01 | Protección de credenciales y cuerpos | No guardar contraseñas de correo; usar OAuth2 con PKCE y cifrado AES-256-GCM para tokens y cuerpos persistidos, con claves por cuenta en el almacén del sistema. Verificar persistencia y manejo de fallos. |
| RNF-02 | Límites claros de privacidad | Documentar que SQLite no está cifrada íntegramente: asuntos, direcciones y otros metadatos pueden estar legibles. No afirmar protección de toda la base. |
| RNF-03 | Interfaz receptiva | Ejecutar red, acceso a datos y preparación costosa fuera del hilo visual. Durante una demora la ventana sigue procesando interacción y muestra su estado. Validar con la interfaz integrada. |
| RNF-04 | Consistencia local | Una operación fallida no deja registros parciales que se presenten como completos; las migraciones conservan los datos compatibles. Comprobar rollback, migraciones y reapertura. |
| RNF-05 | Aislamiento de sesión | Cancelar o invalidar resultados pendientes al cambiar la sesión; comprobar que respuestas tardías no exponen contenido previo. |
| RNF-06 | Uso sin conexión limitado | Permitir consultar mensajes ya guardados si su cuenta y clave siguen disponibles. No prometer descarga ni envío sin conexión. |
| RNF-07 | Cierre ordenado | Al salir, invalidar resultados pendientes y cerrar recursos después de que deje de usarlos la operación activa, sin bloquear el hilo visual. No interpretar cancelación como reversión de una entrega ya realizada. |
| RNF-08 | Entorno y verificación reproducibles | Compilar y ejecutar la suite con JDK 21. Las pruebas automatizadas ordinarias no necesitan cuentas reales ni modifican los datos personales del usuario. |

### RNF-09 — Objetivos de tiempo

Los valores siguientes proceden del alcance existente. Son objetivos por validar,
no mediciones obtenidas ni garantías de respuesta de los proveedores.

| Operación | Objetivo máximo |
| --- | ---: |
| Carga inicial | 5 s |
| Carga de bandeja | 5 s |
| Apertura de cabecera | 2 s |
| Obtención y preparación del contenido | 2 s adicionales |
| Tiempo total hasta poder leer | 4 s |
| Envío de un mensaje nuevo | 4 s |

La autorización manual OAuth queda fuera del tiempo de inicio. Los presupuestos
internos no demuestran cumplimiento visual: se deberá medir desde la acción del
usuario hasta el resultado visible, incluyendo esperas relevantes. Si el proveedor
se demora, se informa el estado real; nunca se simula éxito. Faltan acordar equipo,
red, volumen de datos y método de medición antes de emitir una aceptación formal
de rendimiento.

## 7. Datos y conservación

Se conservan localmente la cuenta, las credenciales cifradas, los mensajes
sincronizados, los cuerpos descargados y la información de envíos necesaria para
recuperar su estado. Los cuerpos se descifran en memoria al utilizarlos.

La política vigente de cierre de sesión es RF-09. La conservación temporal después
de cerrar sesión no se implementará como parte de este requisito. La estructura
existente se consulta en el [diagrama ER](../datos/diagramas/er-diagram.md);
el diccionario de datos se elaborará por separado.

## 8. Verificación y trazabilidad inicial

La evidencia automatizada no equivale a la aceptación de la aplicación completa.
Los nombres siguientes localizan conjuntos de pruebas; no declaran cobertura total
de cada requisito ni sustituyen una matriz de casos individual.

| Requisitos | Evidencia automatizada relacionada | Validación restante |
| --- | --- | --- |
| RF-01, RF-02 | AuthServiceTest, StartupServiceTest, ApplicationCoordinatorTest | Autorización con ambos proveedores e integración visual |
| RF-03, RF-04 | InboxServiceTest, MailPresenterTest, InboxFlowIntegrationTest | Bandeja y control Cargar más en la aplicación |
| RF-05, RF-06 | HybridContentTest, HybridMimeTest, MailReaderPaneTest, InboxFlowIntegrationTest | Lectura integrada y muestras reales de ambos proveedores |
| RF-07, RF-08 | MailSendServiceTest, MailPresenterTest, OutboxRestartIntegrationTest | Envíos reales controlados y presentación de sus estados |
| RF-09, RN-02 | AuthServiceTest, SessionFlowIntegrationTest | Confirmación visual y recuperación visible ante fallo |
| RF-10, RNF-03, RNF-05 | OperationErrorTest, MailPresenterTest, ApplicationCoordinatorTest | Interacción en JavaFX y avisos finales |
| RNF-01, RNF-04, RNF-08 | PersistenceTest, MigrationTest, OsKeyStoreServiceTest y suite completa | Configuración y almacén real del entorno objetivo |
| RNF-07, RNF-09 | ApplicationCoordinatorTest, DeadlineTest, ServiceDeadlineTest | Cierre desde la aplicación y mediciones de extremo a extremo |

La ejecución anterior a este documento registró 125 pruebas aprobadas. Ese dato es
una referencia fechada, no un requisito de cantidad ni evidencia de pruebas nuevas
en esta edición documental. Los cuatro escenarios completos con SQLite temporal
se describen en [pruebas de integración](../pruebas/integracion.md).

## 9. Decisiones abiertas

| ID | Tema | Tratamiento mientras no se decida |
| --- | --- | --- |
| P-01 | Equipo, conexión, tamaños y metodología de rendimiento | No declarar cumplidos los objetivos temporales de extremo a extremo |
| P-02 | Retención después de cerrar sesión | Mantener la eliminación local del MVP; no inventar un plazo |
| P-03 | Mismo destinatario en Para, CC y CCO | No cambiar la validación actual; definir la política antes de ampliarla |
| P-04 | Asunto o mensaje vacíos y límites de composición | Documentar y revisar la validación actual antes de cerrar los casos de aceptación del formulario |
| P-05 | Tipos de cuentas y configuración admitida por cada proveedor | Validar compatibilidad real; no prometer soporte universal de todas las cuentas |

## 10. Criterio de terminación del MVP

El MVP se considerará listo cuando los requisitos de su alcance estén conectados a
la interfaz, sus criterios se hayan verificado y exista evidencia de los flujos
principales con Google y Microsoft. Los pendientes que afecten a la aceptación
deberán resolverse o convertirse en limitaciones explícitamente acordadas. Una
suite aprobada por sí sola no satisface este criterio.

## 11. Historial y documentos relacionados

| Versión | Fecha | Cambio |
| --- | --- | --- |
| 0.1 | 2026-10-01 | Primera especificación, criterios observables, exclusiones, trazabilidad inicial y decisiones abiertas |

- [Índice de documentación](../README.md)
- [Funciones futuras y acuerdos de alcance](pendientes-versiones.md)
- [Resumen del proyecto](../../README.md)

Al cambiar el alcance se actualizarán primero los requisitos y los acuerdos
afectados, y después sus pruebas y documentación de diseño. Las notas técnicas no
deben introducir funciones nuevas en el MVP de forma implícita.
