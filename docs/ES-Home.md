# Guía de InstagramTracker

[Türkçe](../README.md) · [English](EN-Home.md) · [Español](ES-Home.md) · [Português (Brasil)](PT-BR-Home.md)

[Instalación](#instalación) · [Archivo ZIP](ES-Instagram-Data-Archive.md) · [Conexión](#conexión-y-primeras-listas) · [Seguimiento](#seguimiento-y-notificaciones) · [Privacidad](#privacidad-y-copias) · [Ayuda](#solución-de-problemas)

InstagramTracker permite revisar exportaciones de Instagram, comparar listas y conservar un historial local de cambios. Las capturas muestran Windows con datos de ejemplo. Algunas etiquetas de las imágenes están en turco.

## Instalación

En Windows, descarga el [ZIP x64 de la versión preliminar 0.2.84](https://github.com/KaanKaya17/InstagramTracker-Downloads/releases/tag/windows-v0.2.84). Extrae todo el ZIP y ejecuta **InstagramTracker.exe** en la carpeta extraída. Mantén a su lado las carpetas y archivos del paquete. El ejecutable no tiene firma Authenticode. En la página de la versión se publica el SHA-256 del ZIP. Para actualizar, elige **Salir completamente** en la bandeja y abre la nueva versión desde una carpeta propia.

Android necesita Android 7.0 o posterior. La distribución pública actual es para Windows. La versión candidata 0.1.100 se instaló en un teléfono. Instagram abrió una sesión y devolvió HTTP 429 al leer el perfil. Queda pendiente verificar una conexión nueva hasta completar las listas en Android. La vista web utiliza datos de ejemplo. [Estado de las versiones (inglés)](EN-Releases.md).

## Conexión y primeras listas

En **Resumen**, puedes abrir un ZIP o vincular tu cuenta para el seguimiento en vivo. Completa en Instagram el código o CAPTCHA que se muestre. En Android, si ves que la sesión se abrió y que queda por completar la conexión, espera a que termine la pausa y solicita la comprobación del perfil una vez. Cerrar la pantalla conserva la sesión pendiente cifrada. La conexión en Tracker se confirma al obtener el perfil.

La primera lista completa sirve como referencia. Para detectar diferencias hace falta una segunda lista completa. Un resultado parcial conserva la referencia anterior. La ausencia de un nombre puede deberse a un cambio de usuario, una cuenta privada o una limitación de acceso. Si falta un dato, consulta su estado antes de interpretar el recuento. Las cuentas conectadas tienen historiales separados y comparten la pausa del dispositivo.

## Seguimiento y notificaciones

En **Perfiles guardados → Agregar persona**, busca por nombre o pega el enlace del perfil. Guardarlo solo afecta a Tracker. Activa el seguimiento periódico para incluirlo en los controles. Puedes usar favoritos, grupos y notas. En Windows, **Ctrl+K** o **/** busca elementos locales, **Ctrl+R** actualiza el perfil cuando la pantalla lo admite, **1–5** abre las secciones principales y **Esc** cierra el panel superior.

**Listas de seguimiento** muestra seguidores, cuentas seguidas y comparaciones disponibles. Selecciona dos perfiles para ver las cuentas en común cuando sus listas estén disponibles. En **Los que no te siguen**, puedes mover una cuenta a excepciones desde el menú de su fila. Queda visible en una sección aparte y puedes devolverla a la lista principal.

En Windows, ajusta las comprobaciones y los tipos de aviso en **Configuración**. **Notificaciones** agrupa los cambios de un mismo control y muestra valores anteriores y nuevos. Con Windows activo, conectado y Tracker en la bandeja, los controles pueden continuar. Suspender o apagar el equipo interrumpe los controles. Android programa el trabajo en segundo plano en periodos de **15 minutos** y puede retrasarlo. El intervalo de perfil seleccionado y el escaneo completo son ajustes distintos.

## Historial, gráficos e historias

**Cronología** registra la hora en que Tracker observó cada cambio. En el historial del perfil se pueden comparar dos observaciones del nombre, la biografía, la foto y los recuentos disponibles. **Historial diario** conserva los primeros y últimos valores observados, el mínimo, el máximo y el número de controles de cada día. Los días se agrupan en UTC. Un día sin observación queda sin datos.

El archivo de historias permite revisar medios guardados mediante un calendario. **Consultar historias** inicia una lectura de las historias disponibles para la cuenta conectada. Los medios y las menciones dependen de lo recibido. Las historias ocultas o caducadas y las visitas al perfil no se pueden determinar con este historial.

## Privacidad y copias

Windows protege los datos locales con DPAPI y Android con Keystore y AES-GCM. La conexión en vivo usa servicios de Instagram. La aplicación no guarda contraseñas ni códigos en el historial y conserva cifrada la sesión necesaria para leer datos.

**Historial portátil** crea un archivo protegido con contraseña que puede abrirse en Windows o Android. Incluye perfiles, notas, observaciones, listas completas e historial diario. La copia portátil excluye sesiones y medios. La copia de Windows se restaura con el mismo usuario de Windows. Los CSV no están cifrados. Borrar los datos locales elimina los registros de la cuenta seleccionada. Los archivos exportados se gestionan aparte.

## Solución de problemas

- **No se puede iniciar sesión:** comprueba que puedes iniciar sesión desde cero en Instagram. Comparte solo el resultado y la versión de Tracker.
- **HTTP 429:** consulta **Detalles de la solicitud**, espera el fin de la pausa y vuelve a intentar la acción una vez. Cambiar de cuenta o reinstalar no elimina la pausa.
- **Lista incompleta:** revisa el sentido de la lista, los recuentos y el estado de continuación. La última lista completa sigue disponible para comparar.
- **Sin aviso:** revisa el registro de la aplicación, el tipo de aviso, el seguimiento y los permisos del sistema.
- **Servicio local de Windows cerrado:** sal por completo, extrae todo el ZIP y vuelve a abrir el ejecutable sin iniciar dos versiones.

Para pedir ayuda, indica la plataforma, la versión, los pasos, el error y la hora aproximada. Omite contraseñas, códigos, cookies y exportaciones personales. [Incidencias de Windows](https://github.com/KaanKaya17/InstagramTracker-Downloads/issues).
