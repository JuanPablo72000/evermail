# Documentación de Evermail

Esta carpeta contiene documentación para ti: requisitos, diseño, guías de uso y
resultados de verificación. Las instrucciones de trabajo del agente se mantienen
en AGENTS.md, fuera de docs.

## Organización y estado

| Área                                               | Contenido disponible                                                                    | Documentación pendiente                                                            |
| -------------------------------------------------- | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| [Requisitos](requisitos/requisitos-del-sistema.md) | Primera versión de requisitos y [funciones futuras](requisitos/pendientes-versiones.md) | Resolver las decisiones abiertas y revisar los criterios con la interfaz terminada |
| Arquitectura                                       | [Guía de arquitectura](arquitectura/README.md), diagramas, flujos y decisiones          | Ampliar la referencia conforme cambie la implementación                            |
| Datos                                              | [Diagrama ER](datos/diagramas/er-diagram.md)                                            | Diccionario de datos                                                               |
| Pruebas                                            | [Escenarios de integración](pruebas/integracion.md)                                     | Plan general, trazabilidad completa y validación con proveedores reales            |
| Referencia del código                              | Código fuente y comentarios existentes                                                  | Referencia Javadoc completa por paquete y clase                                    |
| Desarrollo                                         | Instrucciones básicas en el [README principal](../README.md)                            | Guía completa de instalación, configuración y desarrollo                           |
| Usuario                                            | Guías de configuración, lector y previsualización                                       | Manual de usuario completo                                                         |

Una carpeta o un diagrama por sí solos no indican que un manual esté terminado.
Los diagramas y las guías de arquitectura se contrastaron con el código el
17 de octubre de 2026. Describen la implementación, separan límites actuales de
propuestas futuras y explican las decisiones de los flujos más complejos.

```text
docs/
├── README.md
├── requisitos/
│   ├── requisitos-del-sistema.md
│   └── pendientes-versiones.md
├── arquitectura/
│   ├── README.md                 # Mapa de capas y recorrido de aprendizaje
│   ├── sesion-y-concurrencia.md
│   ├── lectura-y-cache.md
│   ├── envio-y-recuperacion.md
│   ├── persistencia-y-seguridad.md
│   ├── sintaxis-diagramas.md
│   └── diagramas/                # UML por responsabilidad
├── datos/
│   └── diagramas/                # Diagrama ER existente
├── pruebas/
│   └── integracion.md
└── desarrollo/                  # Guías de configuración y uso
```

## Orden de lectura

1. Leer los requisitos para conocer el alcance y los criterios de aceptación.
2. Recorrer la [guía de arquitectura](arquitectura/README.md) y sus diagramas para entender las piezas, sus relaciones y sus motivos.
3. Consultar las pruebas para distinguir lo comprobado de lo pendiente.
4. Consultar las guías de desarrollo para configurar y utilizar la aplicación.

## Diagramas de clases

- [Modelos](arquitectura/diagramas/uml-model.md)
- [DAO y filas](arquitectura/diagramas/uml-dao.md)
- [Repositorios](arquitectura/diagramas/uml-repositories.md)
- [Servicios](arquitectura/diagramas/uml-service.md)
- [Fachadas](arquitectura/diagramas/uml-facade.md)
- [Arranque y lector independiente](arquitectura/diagramas/uml-controllers.md)
- [Coordinación de aplicación](arquitectura/diagramas/uml-application.md)
- [Presentación](arquitectura/diagramas/uml-presentation.md)
- [Navegación](arquitectura/diagramas/uml-navigation.md)
- [Interfaz JavaFX](arquitectura/diagramas/uml-ui.md)
- [Diagnóstico](arquitectura/diagramas/uml-diagnostics.md)
- [Configuración](arquitectura/diagramas/uml-config.md)
- [Utilidades](arquitectura/diagramas/uml-util.md)
- [Excepciones](arquitectura/diagramas/uml-exception.md)

## Guías para usar y configurar Evermail

- [Configurar OAuth y ejecutar la aplicación](desarrollo/configuracion-oauth.md)
- [Configurar el proxy de imágenes](desarrollo/proxy-imagenes.md)
- [Formato y compatibilidad del lector](desarrollo/lector-correo.md)
- [Previsualizar la interfaz](desarrollo/previsualizar-interfaz.md)
- [Descarga y limpieza de sesión](desarrollo/descarga-y-limpieza.md)
- [Medición de tiempos de lectura](pruebas/tiempos-lectura.md)
- [Aceptación del MVP y comprobación de errores](pruebas/aceptacion-mvp.md)

## Mantenimiento

Los requisitos describen comportamientos esperados; las guías explican cómo
se implementan y por qué; las pruebas aportan evidencia. Se enlazan entre sí sin copiar
todos sus detalles. Los identificadores de requisitos se conservan al editar su
redacción y no se reutilizan para comportamientos distintos.
