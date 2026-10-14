# Previsualizar la interfaz sin iniciar sesión

La ventana utiliza el logo de Evermail como icono. En Windows 11, su barra de
título nativa adopta el morado `#251a30` de la aplicación con texto blanco;
conserva minimizar, maximizar, cerrar y redimensionar. Los sistemas que no
admiten estos colores conservan la decoración del sistema. El cambio se ve al
iniciar de nuevo la aplicación y también se aplica en previsualización.
Esto configura la ventana en ejecución; el icono de un futuro archivo `.exe`
se configurará al preparar el instalador, que sigue aplazado.

Con Java 21 disponible, abre PowerShell en la carpeta del proyecto y ejecuta:

```powershell
.\gradlew.bat run --args="--preview=main"
```

Sustituye `main` por `login`, `loading`, `loaded`, `reader` o `logout` para ver
las otras pantallas. Los datos son ficticios y este modo no consulta tus cuentas.
La vista `reader` muestra el ejemplo de interfaz; no sustituye una prueba de un
correo real en el lector HTML.

Para abrir la aplicación normalmente:

```powershell
.\gradlew.bat run
```

Las pruebas visuales se ejecutan con `gradlew.bat test` y guardan sus capturas en
`build/ui-previews`. La ventana se adapta a distintos anchos; algunos controles
corresponden a funciones futuras y permanecen deshabilitados.
