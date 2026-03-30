# Instrucciones: dónde encaja la app de escritorio real frente al ejemplo actual

Este repositorio asume que el **widget de Windows** y una **app de escritorio** comparten el mismo paquete MSIX y la misma fuente de verdad de sesión y fichaje. El “ejemplo pocho” de hoy no es un stub suelto: es el proyecto **`Widget.TimeTracking.App`** más la infraestructura mock **`Widget.TimeTracking.Infrastructure.Mock`**, cableada en dos sitios.

## Dónde está hoy el ejemplo (qué sustituir en la práctica)

| Pieza | Ubicación | Rol |
|--------|-----------|-----|
| App acompañante mínima | `Windows/src/Widget.TimeTracking.App/` | Ventana WPF/WinUI donde hoy está login mock, botones de fichaje y timeline. **Es aquí donde evolucionarías o reemplazarías la UI y el flujo real.** |
| Composición DI de la app | `Windows/src/Widget.TimeTracking.App/Composition/AppServices.cs` | Registra hoy `LocalJson*` + `MockAuthenticationService`. **Sustituye estas implementaciones por servicios reales** (API, tokens seguros, etc.) manteniendo los contratos de `Widget.TimeTracking.Core`. |
| Host del widget (no es “la app de usuario”) | `Windows/src/Widget.TimeTracking.WidgetHost/` | Proveedor COM + Adaptive Card. Sigue siendo necesario; **no lo confundas con la app de escritorio.** |
| Composición del host | `Windows/src/Widget.TimeTracking.WidgetHost/Composition/AppServices.cs` | Misma pila mock que la app para que widget y app lean el mismo estado local. **Cuando tengas persistencia/API real, ambos deben usar la misma implementación** (proyecto de infraestructura compartido o ensamblado común). |
| Empaquetado | `Windows/src/Widget.TimeTracking.Package/` (`Package.appxmanifest`, `.wapproj`) | Declara **dos** aplicaciones: el ejecutable del host (`Id="WidgetHost"`) y el de la app (`Id="TimeTrackingApp"`). La acción **open-app** del widget apunta al `Id` de la segunda. |

## Dónde colocaría la app de escritorio “de verdad”

**Opción recomendada (menos fricción con lo ya montado):** seguir usando **`Windows/src/Widget.TimeTracking.App`** como **único punto de entrada de la app de escritorio** del producto. Sustituyes el contenido “demo” (textos, botones mock, flujos) por la app real, y mueves la lógica pesada a:

- nuevos proyectos bajo `Windows/src/` (por ejemplo `Widget.TimeTracking.Infrastructure.Api`) si quieres separar mock vs producción, **o**
- las mismas carpetas de la app si el alcance sigue siendo pequeño.

El widget **no debería** duplicar login ni reglas de negocio: sigue consumiendo `ITimeTrackingService`, `IUserSessionService` e `IAuthenticationService` desde Core, con implementaciones que coincidan con las de la app.

**Opción si ya tienes otra solución WinUI/WPF existente:** añadir ese proyecto a `Widget.TimeTracking.sln`, referenciar `Widget.TimeTracking.Core` (y tu capa de infra), y en **`Package.appxmanifest`** cambiar el `Executable` (y si aplica el `Id`) de la segunda `<Application>` para que apunte a tu EXE. Entonces actualiza la constante **`CompanionAppApplicationId`** en `Windows/src/Widget.TimeTracking.WidgetHost/Actions/WidgetActionRouter.cs` para que coincida con el **`Id`** del manifiesto; si no, **open-app** abrirá la aplicación equivocada o fallará al resolver `shell:AppsFolder\...!Id`.

## Qué no mover de sitio sin motivo fuerte

- **`Widget.TimeTracking.Core`:** contratos y modelos compartidos; el widget y la app deben seguir hablando el mismo idioma.
- **`Widget.TimeTracking.WidgetHost`:** es el proceso que Windows Widgets activa; reemplazarlo por la app de usuario rompería el modelo de extensión COM + `windows.appExtension`.
- **Separación “app dueña de la sesión, widget consumidor”:** la app inicia/cierra sesión y persiste; el widget refleja y dispara acciones acotadas.

## Resumen en una frase

La app de escritorio real **vive en el mismo hueco que hoy ocupa** `Widget.TimeTracking.App` **y en el segundo `<Application>` del MSIX** (`TimeTrackingApp`); el ejemplo se sustituye **sustituyendo UI + registros en `AppServices.cs` + (opcional) nuevo proyecto de infraestructura**, manteniendo alineados manifiesto, `CompanionAppApplicationId` y las implementaciones de Core entre host y app.
