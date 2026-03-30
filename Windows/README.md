# Widget TimeTracking

Base profesional para un **widget de Windows 11 orientado al fichaje laboral**, construida con **C#**, **Windows App SDK** y **MSIX packaging**.

> Estado actual: el widget funciona, consume sesión mock, y ahora la solución incluye una **app mínima acompañante** que pasa a ser la dueña de la sesión mock y del visionado rico de descansos.

---

## Qué contiene la solución

```text
Widget.TimeTracking.sln
+- Directory.Build.props
+- README.md
+- src
   +- Widget.TimeTracking.Core
   +- Widget.TimeTracking.Infrastructure.Mock
   +- Widget.TimeTracking.WidgetHost
   +- Widget.TimeTracking.App
   +- Widget.TimeTracking.Package
```

### `Widget.TimeTracking.Core`
- dominio de fichaje
- descansos tipados (`Coffee` / `Food`)
- eventos de jornada y resúmenes diarios
- contratos de sesión/autenticación
- tokens de branding (`BrandColors`)

### `Widget.TimeTracking.Infrastructure.Mock`
- persistencia JSON local
- `LocalJsonTimeTrackingService`
- `LocalJsonUserSessionService`
- `MockAuthenticationService`

### `Widget.TimeTracking.WidgetHost`
- provider del widget
- mapeo `SignedOut` / `SignedIn`
- resumen compacto de descansos
- Adaptive Card JSON
- acción `open-app`

### `Widget.TimeTracking.App`
App mínima acompañante para:
- iniciar sesión mock
- cerrar sesión
- mostrar usuario actual
- ejecutar acciones de fichaje y descansos
- visualizar timeline diario
- mostrar resumen de último fichaje, mes, café hoy y comida hoy
- convertirse más adelante en la app real

### `Widget.TimeTracking.Package`
- MSIX principal
- declara el widget host
- ahora también declara la app acompañante empaquetada

---

## Arquitectura de autenticación

### Decisión
**La app es dueña del login. El widget consume el estado de sesión.**

Eso significa:
- el widget NO hace login real
- la app crea/elimina la sesión mock hoy
- mañana la app podrá gestionar login real y almacenamiento seguro
- el widget seguirá siendo solo consumidor de sesión

---

## Estados del widget

### SignedOut
Muestra:
- `Fichaje`
- mensaje para iniciar sesión
- CTA `Abrir app`

### SignedIn
Muestra:
- usuario actual
- estado de fichaje con tipo de descanso si aplica
- última acción
- resumen corto (`Último fichaje`, `Café hoy`, `Comida hoy`, `Mes`)
- timeline textual compacto
- acciones rápidas relevantes (`Entrar`, `Iniciar café`, `Finalizar café`, `Iniciar comida`, `Finalizar comida`, `Salir`)

---

## App mínima acompañante

La app nueva vive en:

```text
src/Widget.TimeTracking.App
```

### Qué hace hoy
- reutiliza `IUserSessionService` e `IAuthenticationService`
- usa la misma infraestructura mock que el widget
- permite manejar la sesión **sin tocar JSON a mano**
- funciona como superficie rica principal para el visionado de descansos
- dispara refresh del widget cuando cambia la sesión o el fichaje

### Pantalla mínima actual
- estado de sesión
- usuario actual
- fecha/hora de inicio de sesión
- acciones rápidas:
  - `Entrar`
  - `Iniciar café`
  - `Finalizar café`
  - `Iniciar comida`
  - `Finalizar comida`
  - `Salir`
- estado visible con diferenciación entre:
  - `Sin fichar`
  - `Trabajando`
  - `En descanso de café`
  - `En descanso de comida`
  - `Fuera de jornada`
- timeline diario basado en `WorkdayEvents`
- resumen con:
  - `Total horas último fichaje`
  - `Total horas trabajadas este mes`
  - `Café hoy`
  - `Comida hoy`
- `TimePicker` de referencia local
- rutas de los archivos locales

## Persistencia local

### Fichaje
```text
%LocalAppData%\Widget.TimeTracking\time-tracking-state.json
```

### Sesión mock
```text
%LocalAppData%\Widget.TimeTracking\user-session.json
```

El estado de fichaje local ahora guarda también:
- break activo por tipo
- sesiones de descanso del día
- eventos de jornada
- resúmenes mock de trabajo/descansos

---

## Estándar visual

Colores base acordados:
- **Blanco**: `#FFFFFF`
- **Primario**: `#5F96F9`

La app mínima usa esa base visual con:
- superficies blancas
- bordes suaves azulados
- acentos en `#5F96F9`
- tarjetas de resumen con tipografía destacada en azul

El widget usa branding mínimo y color semántico donde Adaptive Cards lo permite. Además, el template intenta forzar una superficie clara para mejorar legibilidad; aun así, el host de Widgets puede seguir imponiendo parte del cromado exterior según tema del sistema.

> Importante: el diseño móvil de referencia se tomó como intención funcional (coffee / food / timeline / summaries), no como copia literal de React/Tailwind dentro del widget.

## Modelo de descansos

### Tipos de descanso
- `Coffee`
- `Food`

### Eventos de jornada
- `ClockIn`
- `StartCoffeeBreak`
- `EndCoffeeBreak`
- `StartFoodBreak`
- `EndFoodBreak`
- `ClockOut`

### Resumen calculado
El mock local ya calcula:
- jornada actual trabajada
- total del último fichaje
- total acumulado del mes
- café consumido hoy
- comida consumida hoy

## Cómo probar el flujo actual

### Widget
1. desplegá `Widget.TimeTracking.Package`
2. abrí `Win + W`
3. agregá `Fichaje`

### App acompañante
1. abrí la solución en Visual Studio
2. usá el proyecto/app empaquetada dentro del package principal
3. iniciá la app `Fichaje`
4. probá:
   - `Iniciar sesión mock`
   - `Cerrar sesión`
   - `Actualizar`
   - mover el `TimePicker` para validar la preferencia visual local

### Resultado esperado
- si iniciás sesión desde la app, el widget debería pasar a estado autenticado
- si cerrás sesión, el widget debería volver a estado signed-out
- si fichás entrada, la app y el widget deben reflejar estado de trabajo
- si iniciás café o comida, la app debe mostrar el tipo de descanso y el widget debe reflejarlo de forma compacta
- la timeline diaria y las tarjetas de resumen deben moverse con cada acción
- si fichás desde la app:
  - el timeline diario debe crecer
  - los resúmenes deben actualizarse
  - el widget debe reflejar el estado compacto resultante

---

## `open-app`
La acción `open-app` del widget ya dejó de ser un stub puro:
- intenta abrir la app empaquetada `TimeTrackingApp` dentro del mismo package
- si el lanzamiento no está disponible todavía en un entorno concreto, deja trazabilidad con `Debug.WriteLine`

> Esto deja la costura correcta preparada sin meter backend real ni inventar login dentro del widget.

---

## Cómo reemplazar el mock por una implementación real

### Servicios contractuales
```csharp
ITimeTrackingService
IUserSessionService
IAuthenticationService
```

### Evolución recomendada
1. mantener `Widget.TimeTracking.App` como dueña del login
2. reemplazar `MockAuthenticationService` por auth real
3. reemplazar `LocalJsonUserSessionService` por sesión segura
4. reemplazar `LocalJsonTimeTrackingService` por API real si hace falta
5. mantener el widget desacoplado del detalle de infraestructura

### Modelo funcional actual
- `BreakType`: `Coffee`, `Food`
- eventos de jornada:
  - `ClockIn`
  - `StartCoffeeBreak`
  - `EndCoffeeBreak`
  - `StartFoodBreak`
  - `EndFoodBreak`
  - `ClockOut`
- resumen diario:
  - horas jornada actual
  - horas último fichaje
  - horas trabajadas este mes
  - tiempo en café hoy
  - tiempo en comida hoy

---

## Archivos principales

### App nueva
- `src/Widget.TimeTracking.App/Widget.TimeTracking.App.csproj`
- `src/Widget.TimeTracking.App/App.xaml`
- `src/Widget.TimeTracking.App/App.xaml.cs`
- `src/Widget.TimeTracking.App/MainWindow.xaml`
- `src/Widget.TimeTracking.App/MainWindow.xaml.cs`
- `src/Widget.TimeTracking.App/Composition/AppServices.cs`

### Nota sobre SmokeTest
Los proyectos `Widget.SmokeTest.*` ya no forman parte de la solución principal. Sirvieron como experimento de control para diagnosticar el entorno de Widgets, pero dejarlos cargados confundía el flujo diario.

### Host del widget
- `src/Widget.TimeTracking.WidgetHost/Actions/WidgetActionRouter.cs`
- `src/Widget.TimeTracking.WidgetHost/Providers/TimeTrackingWidgetProvider.cs`
- `src/Widget.TimeTracking.WidgetHost/Rendering/TimeTrackingWidgetViewModelMapper.cs`
- `src/Widget.TimeTracking.WidgetHost/Rendering/AdaptiveCardTemplateBuilder.cs`

### Packaging
- `src/Widget.TimeTracking.Package/Package.appxmanifest`
- `src/Widget.TimeTracking.Package/Widget.TimeTracking.Package.wapproj`

---

## Limitaciones actuales

1. **La app sigue siendo mínima**
   - no hay login real
   - no hay backend
   - no hay almacenamiento seguro de credenciales

2. **El timeline y los resúmenes siguen siendo mock locales**
   - correctos para MVP
   - cuando llegue backend real habrá que redefinir la fuente de verdad mensual

3. **`open-app` depende del empaquetado**
   - está preparado para lanzar la app empaquetada
   - puede requerir ajuste fino cuando se compile y despliegue todo junto por primera vez

4. **No se ejecutó build después de estos cambios**
   - por regla del repo

5. **El widget sigue siendo una vista compacta**
   - la superficie rica para descansos es la app
   - el widget resume, no reemplaza el dashboard

6. **El widget sigue dependiendo del entorno Windows**
   - en pruebas reales se confirmó que hacía falta tener Windows actualizado para que Widgets detectara correctamente el provider

## Siguientes pasos recomendados

1. validar el flujo completo:
   - app inicia sesión
   - widget refleja sesión
   - entrada / café / comida / salida actualizan app y widget
   - timeline y summaries se mueven correctamente
2. si todo está bien:
   - agregar apertura real robusta desde `open-app`
   - endurecer persistencia y errores
   - diseñar la app final como dueña del login real
3. recién después:
   - integrar backend
   - almacenamiento seguro de tokens
   - histórico real y reportes avanzados

## Referencias oficiales
- Windows Widgets overview  
  https://learn.microsoft.com/en-us/windows/apps/design/widgets/
- Implement a widget provider in a C# Windows App  
  https://learn.microsoft.com/en-us/windows/apps/develop/widgets/implement-widget-provider-cs
- Widget provider package manifest XML format  
  https://learn.microsoft.com/en-us/windows/apps/develop/widgets/widget-provider-manifest


