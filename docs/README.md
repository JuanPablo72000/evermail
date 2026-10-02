# Documentación de Evermail

Este índice separa los requisitos del producto, su diseño y la evidencia de
verificación. Las notas técnicas de implementación se conservan como material de
consulta; no sustituyen los documentos formales pendientes.

## Organización y estado

| Área | Contenido disponible | Documentación pendiente |
| --- | --- | --- |
| [Requisitos](requisitos/requisitos-del-sistema.md) | Primera versión de requisitos y [funciones futuras](requisitos/pendientes-versiones.md) | Resolver las decisiones abiertas y revisar los criterios con la interfaz terminada |
| Arquitectura | [Diagramas de clases](#diagramas-de-clases) | Manual de arquitectura, explicación de paquetes, flujos y decisiones técnicas |
| Datos | [Diagrama ER](datos/diagramas/er-diagram.md) | Diccionario de datos |
| Pruebas | [Escenarios de integración](pruebas/integracion.md) | Plan general, trazabilidad completa y validación con proveedores reales |
| Referencia del código | Código fuente y comentarios existentes | Referencia Javadoc completa por paquete y clase |
| Desarrollo | Instrucciones básicas en el [README principal](../README.md) | Guía completa de instalación, configuración y desarrollo |
| Usuario | Pendiente de la interfaz | Manual de usuario |

Las carpetas de referencia, desarrollo y usuario se crearán cuando tengan
contenido. Una carpeta o un diagrama por sí solos no indican que un manual esté
terminado. Los diagramas existentes se han reubicado sin auditar su contenido:
su concordancia con el código se revisará al elaborar el manual técnico.

```text
docs/
├── README.md
├── requisitos/
│   ├── requisitos-del-sistema.md
│   └── pendientes-versiones.md
├── arquitectura/
│   └── diagramas/                # Diagramas UML existentes
├── datos/
│   └── diagramas/                # Diagrama ER existente
├── pruebas/
│   └── integracion.md
└── notas-tecnicas/               # Contexto de implementación, no manual formal
```

## Orden de lectura

1. Leer los requisitos para conocer el alcance y los criterios de aceptación.
2. Consultar los diagramas para ubicar las piezas y sus relaciones.
3. Consultar las pruebas para distinguir lo comprobado de lo pendiente.
4. Utilizar las notas técnicas como apoyo al leer la implementación.

## Diagramas de clases

- [Modelos](arquitectura/diagramas/uml-model.md)
- [DAO y conexión](arquitectura/diagramas/uml-dao.md)
- [Repositorios](arquitectura/diagramas/uml-repositories.md)
- [Servicios](arquitectura/diagramas/uml-service.md)
- [Fachadas](arquitectura/diagramas/uml-facade.md)
- [Controladores propuestos](arquitectura/diagramas/uml-controllers.md)
- [Configuración](arquitectura/diagramas/uml-config.md)
- [Utilidades](arquitectura/diagramas/uml-util.md)
- [Excepciones](arquitectura/diagramas/uml-exception.md)

## Notas técnicas conservadas

- [Coordinación de aplicación y sesión](notas-tecnicas/application-coordination.md)
- [Presentación y navegación](notas-tecnicas/presentation-navigation.md)
- [Lectura HTML y texto](notas-tecnicas/html-reading.md)

## Mantenimiento

Los requisitos describen comportamientos esperados; los manuales explicarán cómo
se implementan; las pruebas aportan evidencia. Se enlazan entre sí sin copiar
todos sus detalles. Los identificadores de requisitos se conservan al editar su
redacción y no se reutilizan para comportamientos distintos.
