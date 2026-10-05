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
de 900 px, para reservar espacio suficiente al formulario y sus textos.
El panel de acceso mantiene un ancho máximo de 326 px y una altura calculada
según su contenido (439 px como mínimo de diseño). No rellena la altura de la
ventana: `LoginCard` limita su máximo con `Region.USE_PREF_SIZE`, y la cuadrícula
conserva el centrado vertical. Este valor especial se configura desde Java,
no como `-Infinity` en CSS. Las barras de herramientas y los botones de los diálogos se
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

Con JDK 21, la ejecución normal inicia el backend y restaura la sesión o muestra el acceso. Para abrir una
composición concreta:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
./gradlew.bat run --args="--preview=main"
```

Valores disponibles: `login`, `loading`, `loaded`, `main`, `reader`, `logout`.
`PreviewData` añade 50 filas ficticias en la bandeja y permite alternar texto y
formato en el lector de demostración. No consulta cuentas, SQLite, credenciales
ni proveedores. Los nombres y textos del diseño son muestras, no sesión real.

## Integración funcional

`MailWindow` conecta los controles al `MailPresenter`, a través de
`CoordinatedPresentationBackend` y `ApplicationCoordinator`. Las notificaciones
se procesan con `Platform.runLater`. La aplicación reemplaza los textos de muestra
por la cuenta activa y los resultados del backend antes de presentar vistas
privadas. La carga refleja una operación pendiente sin simular porcentajes.
Los controles futuros permanecen visibles y deshabilitados, y los adjuntos vacíos.

`SafeMailBody` muestra en `bodyHost` el documento saneado por el backend, sin
iniciar otra carga de correo. Conserva el bloqueo de scripts, popups y recursos
remotos y la alternativa de texto. El diálogo de cierre es modal y confirma
la operación del presentador. El cierre de ventana espera `closeAsync()` sin
bloquear JavaFX. La [guía de configuración OAuth](../desarrollo/configuracion-oauth.md)
explica la preparación necesaria y las garantías y límites de seguridad.

## Fuentes visuales y límites

La revisión del 4 de octubre de 2026 consulta directamente
[Evermail Desing](https://www.figma.com/design/H0AVEbg2EFwkm0n3h3ucYv/Evermail-Desing?node-id=0-1).
Se comparan acceso (8:249), principal (8:282), lector (48:66), carga (20:2),
carga completa (20:10) y cierre (102:316).

`images/figma` contiene los recursos originales descargados. Los SVG se conservan
junto a sus PNG rasterizados a 3× para ImageView; `MailIcon` utiliza esos recursos,
sin redibujar sus trazados. `FigmaImage` mantiene sus proporciones en contenedores
nativos. Inter, Poppins e Inria Serif se incluyen en `fonts`, con sus licencias OFL,
y se cargan antes de aplicar los estilos. No se necesitan fuentes instaladas ni
URLs temporales de Figma durante la ejecución.

Los tamaños de la bandeja y el lector parten de 1920×1080. `ViewController` ajusta
la tipografía base entre 13.33 y 20 px; las dimensiones CSS en em acompañan esa
escala. Los paneles siguen redistribuyéndose con GridPane, VBox, HBox y FlowPane.
`ReaderDetails` apila remitente, fecha, destinatarios y formatos por debajo de
650 px de ancho disponible, y recupera dos columnas al ampliar la ventana.

La equivalencia no es píxel por píxel: el efecto Glass de Figma se aproxima con
GaussianBlur y una capa translúcida en `LoginCard`; JavaFX tiene métricas y
suavizado de texto propios. El cuerpo de ejemplo permanece como contenido
adaptable; los correos reales conservan su HTML saneado y sus estilos. Las
funciones futuras siguen deshabilitadas en la aplicación y los datos de cuenta
son dinámicos. No se modificó el archivo de Figma.

## Verificación

`FxmlViewsTest` carga las seis vistas principales y sus componentes en JavaFX,
comprueba ausencia de desbordamiento horizontal de los contenedores y genera
capturas a 1920×1080, 1280×720, 960×540, 760×730, 600×900 y 360×800. También verifica
los colores hover, la alternancia del ejemplo de lectura, el redimensionamiento
de una misma vista y que los textos del cierre de sesión no pierdan altura al
ajustarse. Las capturas se guardan en
`build/ui-previews`, incluidas las partes inferiores de las vistas estrechas.

```powershell
./gradlew.bat test assemble --no-daemon
```

Estas pruebas visuales se complementan con `MailWindowTest`, `SafeMailBodyTest`
y las pruebas del transporte OAuth; ninguna sustituye la validación con cuentas reales.
