# Vistas JavaFX basadas en las capturas

Se implementan las cinco composiciones entregadas el 2 de octubre de 2026:
carga (inicial y completa), acceso, bandeja con redacción, lector y cierre de
sesión. Los estados de los botones proceden de las variantes visibles en las
capturas. No se han modificado archivos de Figma.

## Archivos

Todos los FXML están en `src/main/resources/com/juanpablo/evermail/fxml` y los
estilos en la carpeta vecina `css`.

| Vista / componente | FXML | CSS |
| --- | --- | --- |
| Acceso | Login-Screen.fxml | Login-Styles.css |
| Carga | Loading-Screen.fxml, Loading-Complete.fxml | Loading.css |
| Principal | Main-Screen.fxml | Main.css |
| Navegación y cuenta | Sidebar.fxml | Sidebar.css |
| Bandeja | Inbox.fxml | Inbox.css |
| Fila de correo | Mail-Row.fxml | Mail-Row.css |
| Redacción | Compose.fxml | Compose.css |
| Lector | Reader-Screen.fxml | Reader.css |
| Confirmación de salida | Logout-Dialog.fxml | Logout.css |

`Common.css` contiene colores, tipografía, campos, avatares, barras de
desplazamiento y estados comunes. `Reader-Sample.fxml` contiene exclusivamente
el mensaje de demostración de la captura; se carga en el modo de previsualización.

## Distribución adaptable

No se utilizan `layoutX`, `layoutY`, `translateX`, `translateY` ni posicionamiento
absoluto de controles. Las dimensiones preferidas de ventana, logos, iconos y
espaciados son referencias de diseño; los paneles crecen mediante restricciones
de GridPane y prioridades de crecimiento.

`ResponsiveGrid` usa tres columnas a partir de 1100 px para la vista principal.
Entre 760 y 1099 px muestra navegación y bandeja en la primera fila, y redacción
debajo. Por debajo de 760 px apila los tres paneles. El lector pasa de dos
columnas a una por debajo de 760 px; el acceso apila sus dos bloques por debajo
de 680 px. Las barras de herramientas y los botones de los diálogos se
redistribuyen con FlowPane. Las ventanas bajas o estrechas ofrecen desplazamiento
vertical, sin ocultar funciones para hacerlas caber. El ancho mínimo de ventana
es de 360 px. `ViewController` solo adapta el contenido al área visible.

## Interacción visual

`AnimatedButton` anima la escala al entrar/salir y pulsar, durante 130 ms, sin
mover el espacio asignado al control. Las animaciones se interrumpen suavemente
cuando cambia el estado. CSS define hover, pulsación, foco de teclado y estado
deshabilitado. Se puede desactivar el movimiento con la propiedad JVM
`-Devermail.reduceMotion=true`; los colores y el foco siguen funcionando.

## Previsualizar sin cuentas reales

Con JDK 21, la ejecución normal continúa mostrando el acceso. Para abrir una
composición concreta:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
./gradlew.bat run --args="--preview=main"
```

Valores disponibles: `login`, `loading`, `loaded`, `main`, `reader`, `logout`.
`PreviewData` añade 50 filas ficticias en la bandeja y permite alternar texto y
formato en el lector de demostración. No consulta cuentas, SQLite, credenciales
ni proveedores. Los nombres y textos del diseño son muestras, no sesión real.

## Integración pendiente

Los identificadores `fx:id` dejan disponibles los controles para la siguiente
etapa. Esta entrega no conecta OAuth, envío, paginación, cierre de sesión ni
acciones de correo al backend. Los controles de funciones futuras permanecen
visibles. El área de adjuntos continúa vacía. Las vistas de carga no avanzan ni
informan éxito automáticamente. Antes de mostrar una vista privada con una
cuenta real se deben reemplazar todos los textos de muestra por el estado del
presentador.

El futuro lector debe colocar el renderizador seguro en `bodyHost`, conservando
las protecciones HTML existentes. El ejemplo visual no es un renderizador MIME.
`Logout-Dialog.fxml` es el contenido visual; su presentación modal y la
confirmación real deben conectarse al flujo de sesión.

## Fuentes visuales y límites

Se reutilizan el fondo geométrico, los logos de proveedores y el logo de Evermail
ya presentes en el repositorio. El logo usa un viewport sobre su margen
transparente, sin alterar el archivo de imagen. `MailIcon` recrea los iconos de
línea como vectores nativos. Los SVG y las métricas originales de Figma no estaban
disponibles por el límite del conector: iconos, tipografía y medidas se aproximan
a partir de las capturas. No se afirma equivalencia píxel por píxel.

## Verificación

`FxmlViewsTest` carga las seis vistas principales y sus componentes en JavaFX,
comprueba ausencia de desbordamiento horizontal de los contenedores y genera
capturas a 1280×720, 960×540, 600×900 y 360×800. También verifica los colores hover
y la alternancia del ejemplo de lectura. Las capturas se guardan en
`build/ui-previews`, incluidas las partes inferiores de las vistas estrechas.

```powershell
./gradlew.bat test assemble --no-daemon
```

Estas pruebas no sustituyen la integración pendiente con el presentador ni una
validación de flujos con cuentas reales.
