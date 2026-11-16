# Vento

Sistema de punto de venta en desarrollo con Spring Boot y Java 21.

## Requisitos

- JDK 21
- Maven 3.9 o posterior

## Ejecutar localmente

Desde la carpeta del proyecto:

```powershell
mvn spring-boot:run
```

Abre `http://localhost:8081` e inicia sesión con `admin` o `cajero`; deja la contraseña vacía. Al cerrar sesión, Vento termina la sesión y te redirige al formulario de inicio de sesión. El firmador DTE ocupa `localhost:8080` por defecto; ejecuta `java -jar C:\FIRMADOR\svfe-api-firmador-0.1.1.jar` por separado y configura `SERVER_PORT` si necesitas otro puerto para Vento.

## Estado actual

La pantalla del punto de venta obtiene productos de la base de datos y permite registrar ventas con existencias, detalle, descuentos e IVA. El historial de ventas está disponible en la navegación.

La base H2 predeterminada se guarda en `./data/ventodb` y conserva los datos al reiniciar. Puedes seleccionar otro porcentaje de IVA con `VENTO_IVA_TASA` (por ejemplo, `0.13` para 13 % o `0` para no cobrar IVA).

Para usar MySQL, configura `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. El catálogo inicial solo se crea cuando no hay productos.

Al iniciar por primera vez se crean los usuarios `admin` (Administrador General) y `cajero` (Cajero Turno 1), ambos sin contraseña. La aplicación se enlaza por defecto a `127.0.0.1` para que solo sea accesible desde este equipo. Las contraseñas vacías y los permisos temporales de desarrollo no son seguros: no habilites acceso desde la red ni uses esta configuración en producción.

## API

- `GET /api/v1/pos/productos?categoria=Café&busqueda=espresso` para el catálogo activo de la caja.
- `GET /api/v1/productos?categoria=Café&busqueda=espresso` para consultar el catálogo completo (`ROLE_ADMIN`).
- `GET /api/v1/productos/{id}`, `POST /api/v1/productos`, `PUT /api/v1/productos/{id}` y `DELETE /api/v1/productos/{id}` para administrar productos (`ROLE_ADMIN`). Las bajas son lógicas para conservar el historial de ventas.
- `GET /api/v1/categorias` para consultar categorías activas (`ROLE_ADMIN`).
- `GET /api/v1/inventario/movimientos` y `POST /api/v1/inventario/ajuste` para el Kardex y ajustes de existencias (`ROLE_ADMIN`).
- `GET /api/v1/reportes/dashboard?fechaInicio=2026-11-01T00:00:00&fechaFin=2026-11-06T23:59:59` para los indicadores de ventas, devoluciones, empleados, productos, categorías y métodos de pago (`ROLE_ADMIN`). Sin fechas, consulta el día actual.
- `POST /api/v1/ventas/{id}/devolucion` admite `{"items":[{"productoId":1,"cantidad":1}],"motivo":"Producto defectuoso"}` para devoluciones parciales; omitir o enviar vacía la lista devuelve todas las unidades pendientes. El motivo es obligatorio. Puede procesarlo `ROLE_USER` o `ROLE_ADMIN`; genera folio `DE`, restaura existencias y registra el usuario.
- `GET /api/v1/configuracion` para consultar la configuración (sesión iniciada) y `PUT /api/v1/configuracion` para actualizar datos del negocio, IVA, moneda y códigos de folio (`ROLE_ADMIN`).
- `PUT /api/v1/auth/password` para que un usuario autenticado cambie su contraseña. `GET/POST /api/v1/usuarios`, `PUT /api/v1/usuarios/{id}`, `PUT /api/v1/usuarios/{id}/estado`, `PUT /api/v1/usuarios/{id}/rol` y `PUT /api/v1/usuarios/{id}/password` permiten a `ROLE_ADMIN` gestionar las cuentas. El administrador no puede deshabilitar su propia cuenta.
- Las preferencias visuales claro/oscuro y el color de acento (azul, rojo, amarillo o verde) se guardan por navegador en `localStorage`.
- `POST /api/v1/ventas` para registrar una venta.
- `POST /api/v1/turnos/apertura` (`{"montoInicial":50.00,"observaciones":"Fondo inicial"}`), `GET /api/v1/turnos/activo` y `POST /api/v1/turnos/cierre` (`{"efectivoReal":125.00,"observaciones":"Corte contado"}`) administran el turno del usuario autenticado. No se aceptan identificadores de usuario en estas solicitudes. La caja esperada se calcula con el fondo inicial, ventas en efectivo y devoluciones en efectivo del turno; registrar una venta requiere un turno abierto.
- `GET /api/v1/ventas` para consultar el historial.
- `GET /api/v1/ventas/tasa-iva` para consultar la tasa configurada.
- `GET /api/v1/auth/me` (sesión iniciada) para consultar el perfil y los módulos permitidos del usuario actual.
- `POST /api/v1/auth/discount-authorization` requiere sesión y credenciales de una cuenta activa `ROLE_ADMIN` con contraseña establecida. Recibe `{"username":"admin","password":"...","amount":5.00}` y devuelve un token de un solo uso, ligado a ese importe y con vigencia de cinco minutos. En `POST /api/v1/ventas`, un descuento manual positivo requiere enviar ese token como `descuentoAuthorizationToken`; el servidor valida y consume la autorización y registra al administrador aprobador. No se aceptan identificadores de aprobador ni permisos enviados por el cliente. En la instalación de demostración, el administrador inicial no tiene contraseña: establece una contraseña antes de usar descuentos manuales.
- `PUT /api/v1/dte/configuracion` guarda los parámetros del emisor y del firmador (`ROLE_ADMIN`); `GET /api/v1/dte/configuracion` entrega los secretos enmascarados. `POST /api/v1/dte/ventas/{id}/firmar` genera y firma el DTE FCF/CCF de una venta con el servicio local del firmador. La respuesta firmada se conserva por venta para que las solicitudes repetidas sean idempotentes.
- `GET/POST /api/v1/promotions`, `PUT /api/v1/promotions/{id}` y `DELETE /api/v1/promotions/{id}` administran promociones y requieren `ROLE_ADMIN`. `GET /api/v1/promotions/active` y `POST /api/v1/promotions/quote` están disponibles para sesiones autenticadas y permiten a la caja mostrar una cotización previa; la venta vuelve a calcular las reglas en el servidor.

### Promociones y descuentos

Las reglas admitidas son `BUY_X_GET_Y` (unidades gratis del mismo producto), `PRODUCT_PERCENT`, `CATEGORY_PERCENT` y `SPEND_TIER` (porcentaje al alcanzar un gasto mínimo). Los porcentajes y umbrales se calculan sobre el subtotal antes de IVA; las cantidades de AxB se agrupan por producto y las unidades gratis usan el precio unitario de ese producto. Las fechas son opcionales e inclusivas. El descuento manual del ticket o de una línea requiere autorización administrativa de un solo uso para el total manual de la venta.

Regla de combinación: las promociones automáticas no se apilan; la venta escoge la promoción elegible que descuenta más, y en empate usa prioridad mayor y luego el ID menor. Una autorización manual sí puede combinarse con esa promoción automática; ambos descuentos se limitan al subtotal. El descuento se prorratea y persiste por línea, reduce la base gravable y el IVA en proporción a los importes de cada línea, y los importes/identidad del aprobador quedan guardados con la venta. No se aceptan importes manuales sin token válido aunque la interfaz los oculte.

El catálogo público de `GET /api/v1/pos/productos` se usa en la caja; todos los endpoints de gestión de `/api/v1/productos` y `/api/v1/categorias` requieren `ROLE_ADMIN`. Las consultas de historial de ventas permanecen abiertas temporalmente para pruebas; registrar ventas y administrar turnos requiere sesión iniciada. `/h2-console/**` permanece temporalmente abierto para pruebas. La interfaz oculta Productos, Inventario y Reportes a `ROLE_USER`; el servidor protege la gestión de productos y reportes. Las unidades iniciales de cada producto tienen existencias de demostración; actualízalas desde el módulo Productos según tu inventario real.

El esquema de la entidad de turnos se crea/actualiza con la configuración JPA existente (`spring.jpa.hibernate.ddl-auto=update`); no se ha agregado una herramienta de migraciones al proyecto.

Los folios internos se generan sin guiones y de forma correlativa por sucursal, estación y operación. Se pueden configurar con `VENTO_SUCURSAL` y `VENTO_ESTACION` (predeterminados `S01` y `C01`); las ventas y sus movimientos quedan enlazados en el Kardex.
