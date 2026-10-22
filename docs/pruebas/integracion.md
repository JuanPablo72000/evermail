# Pruebas de integración antes de conectar FXML

Las cuatro pruebas de `com.juanpablo.evermail.integration` recorren el presentador,
el adaptador de presentación, el coordinador, los servicios y los repositorios
reales con SQLite en un directorio temporal por prueba. Complementan las pruebas
unitarias existentes: comprueban que las capas colaboran correctamente.

| Prueba | Comportamiento protegido |
| --- | --- |
| Inicio y actualización | Muestra primero 50 correos locales; mezcla una página remota parcialmente solapada, conserva las identidades y deja 60 registros únicos. |
| Pérdida y recuperación de red | Conserva la bandeja con indicación de error, permite leer un cuerpo cifrado en caché sin solicitarlo al proveedor y recupera la sincronización en la misma sesión. |
| Cierre de sesión durante lectura | Limpia inmediatamente la presentación, descarta la respuesta tardía y elimina los datos locales y la clave de la cuenta. |
| Reinicio tras envío incierto | Persiste UNKNOWN cuando se pierde la confirmación SMTP; al reabrir la base de datos no reenvía automáticamente ni vuelve a enviar la misma identidad de operación. |

`IntegrationHarness` administra los recursos y procesa las notificaciones en un
único hilo de prueba. `ScriptedMailGateway` simula exclusivamente el transporte:
sus señales permiten detener y liberar respuestas sin usar `Thread.sleep`.
Los límites temporales evitan que un fallo deje la ejecución bloqueada.
Las claves de cifrado viven en memoria y se comparten únicamente entre las dos
instancias del escenario de reinicio. No se usan cuentas, credenciales ni correo
reales; tampoco se abre JavaFX ni se carga FXML.

El reinicio cierra y reconstruye las instancias y la conexión SQLite, conservando
el mismo archivo temporal. No simula una caída del sistema operativo ni comprueba
la restauración de un borrador o un aviso de envío en la interfaz.

Ejecución en PowerShell, con JDK 21:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
./gradlew.bat test --tests 'com.juanpablo.evermail.integration.*' --no-daemon
./gradlew.bat test --no-daemon
```

La etiqueta `integration` identifica los escenarios, pero no los excluye de la
tarea normal `test`. No se añaden dependencias ni se modifican pruebas existentes.
