# Cómo leer y validar los diagramas Markdown

Los archivos `.md` contienen texto Markdown y bloques `mermaid`. El editor procesa Markdown, pero un motor adicional interpreta los diagramas. Un error de contenido técnico, un aviso de formato Markdown y un error de sintaxis Mermaid son problemas diferentes.

## Resultado de la revisión del 17 de octubre de 2026

Antes de modificar los archivos se extrajeron los **12 bloques Mermaid originales** y se ejecutaron tanto el análisis de sintaxis como el renderizado SVG con **Mermaid 11.12.0**, en Microsoft Edge sin interfaz. Los 12 se renderizaron correctamente. Por tanto, esta revisión **no reprodujo un fallo de sintaxis Mermaid en los originales** y no atribuye el error observado previamente a una causa que no se pudo confirmar.

La documentación actual contiene **26 bloques**, entre clases, estados, secuencias, arquitectura general y ER. Se validaron extrayendo cada bloque completo, analizándolo y renderizándolo; una revisión visual de varias figuras complementó la comprobación automática. Los artefactos de esta revisión están en `build/docs-validation`, que es una carpeta temporal de compilación y puede desaparecer con `clean`.

Las inspecciones Markdown del IDE distinguieron avisos de tablas no alineadas (`Table is not correctly formatted`) de errores de Mermaid. Las tablas de la documentación modificada se alinearon. Un aviso de alineación no implica que el diagrama tenga una gramática inválida.

## Convenciones usadas

- Cada bloque comienza con un tipo válido: `classDiagram`, `stateDiagram-v2`, `sequenceDiagram`, `flowchart` o `erDiagram`.
- Las llaves de clases, bloques de secuencia y cercas de Markdown se cierran por separado.
- Los nombres del código se conservan; las clases anidadas reciben un nombre breve y una explicación del nombre Java completo.
- Los retornos genéricos largos se abrevian, por ejemplo `CompletionStage`, y el texto explica qué valor transportan. En firmas largas se conservan los nombres de parámetros y se omiten sus tipos para evitar saltos de línea superpuestos en algunos renderizadores. Las firmas Java completas están en las fuentes enlazadas. Esto mejora la lectura y reduce dependencia de combinaciones complejas de sintaxis.
- En diagramas de clases, `+` significa público, `-` privado y `~` visibilidad de paquete. Las propiedades de records representan datos accesibles por sus accesores. Los atributos de modelos Lombok representan los campos generados privados.
- Las flechas `-->` representan asociación/uso, `..>` dependencia, `<|--` herencia y `<|..` implementación. No todas las flechas significan una llamada en ese instante.
- PK, FK y UK en el ER representan claves primarias, foráneas y únicas. Las restricciones adicionales y lo que comprueba la aplicación se explican debajo.

## Si el visor vuelve a mostrar un error

1. Identifica el archivo, el bloque y el mensaje exacto. Un Markdown puede contener varios diagramas y fallar solo uno.
2. Comprueba que el editor tiene habilitado soporte Mermaid. Un bloque mostrado como texto no demuestra un fallo de gramática.
3. Comprueba la versión del motor o del complemento. Que el documento funcione con Mermaid 11.12.0 no garantiza que una versión anterior admita cada construcción.
4. Prueba el bloque completo en otro visor Mermaid compatible, sin las cercas de Markdown. Si allí funciona, revisa el complemento y su configuración; si falla, usa la línea y el token que indica el analizador.
5. Distingue un fallo de renderizado de un diagrama demasiado ancho: las figuras de clases se pueden ampliar y están separadas por responsabilidad para facilitar su lectura.

Las inconsistencias encontradas en esta revisión fueron principalmente **de arquitectura**: clases propuestas inexistentes, firmas diferentes, plazos antiguos y una política de caché anterior. Esas diferencias se corrigieron contra el código; no se presentaron como errores de sintaxis.
