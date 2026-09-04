# Alfombra TP — Design Doc

Fecha: 2026-09-04
Proyecto: VEventDrops (Paper 1.20.4, Java 17)
Opción elegida: A — Enlace manual con clicks (1 a 1, destino fijo)

## 1. Resumen
Nuevo ítem "Alfombra TP": te paras sobre ella y te agachas (sneak) para teletransportarte a otra alfombra enlazada. El enlace es fijo 1 a 1, bidireccional (A↔B para poder volver), y exige mismo color. Se obtiene por receta + comando. Sigue el patrón actual del plugin (Pergamino de Retorno, Mesa de Desvinculación, Cofre de Redstone): ítem vanilla + `PersistentDataContainer` + `VEventPlugin` + `CommandManager` + Manager dedicado.

## 2. Ítem y obtención
- Base: alfombra vanilla del color (`WHITE_CARPET` … `BLACK_CARPET`, 16 variantes).
- Identificación: `PersistentDataContainer` `vevent_tp_carpet=true` + `vevent_tp_color=<MATERIAL>`.
- Nombre: `§b§lAlfombra TP [<Color>]`. Lore inicial: `§7Sin enlazar` / `§7Shift+click para enlazar`.
- Al enlazarse, el lore del ítem dropeado/colocado pasa a `§aEnlazada` (solo informativo; la verdad vive en `carpets.yml`).
- Comando: `/vevent givecarpet [jugador] [color]`
  - Sin args → 1 alfombra blanca al ejecutor (debe ser jugador; desde consola error `§cEspecifica jugador.`).
  - Solo jugador → 1 blanca a ese jugador.
  - Solo color (`/vevent givecarpet rojo`) → si el primer arg coincide con un color válido se trata como color y el destino es el ejecutor.
  - Jugador + color → a ese jugador de ese color.
  - Color por defecto: `blanco`. Nombres aceptados en español + inglés (`roja/red`, etc.), case-insensitive. Tab-complete para jugador y color.
  - Permiso: `vevent.admin`.
- Receta (una por color, 16 recetas): alfombra vanilla del color en el centro + 4 ender pearls en cruz + 4 lana del color en esquinas = 2x Alfombra TP de ese color. Ejemplo rojo:
  ```
  L P L
  P A P
  L P L
  L=lana roja, P=ender pearl, A=alfombra roja
  ```
- Permisos nuevos: `vevent.tpcarpet.use` (default true, usar TP), `vevent.tpcarpet.place` (default true, colocar/enlazar). Craft libre.

## 3. Colocación y enlace
- `BlockPlaceEvent`: si el ítem tiene `vevent_tp_carpet`, se registra la ubicación en `carpets.yml` como `sin enlazar` (mundo, x/y/z, color `Material`, dueño UUID, fecha). Se cancela si la ubicación ya está registrada.
- Modo enlace (`PlayerInteractEvent`, click derecho sobre bloque alfombra TP + `isSneaking()`):
  - Primer shift+click → `pendingLink<UUID jugador, Location origen>` + mensaje `§aOrigen marcado (Rojo). Ahora shift+click en el destino del mismo color.` + partículas.
  - Segundo shift+click → validaciones y enlace. Mensaje éxito a ambos lados: `§a¡Alfombras enlazadas!`.
  - Validaciones: ambos bloques siguen siendo Alfombra TP registradas; mismo `Material` (mismo color) si no → error `§cDeben ser del mismo color`; origen != destino; si alguno ya estaba enlazado se rompe el par anterior con aviso `§eEnlace anterior roto`.
  - `pendingLink` caduca en 60 s. Hacer shift+click en aire o cambiar de mundo lo limpia.
  - Enlace guardado como par bidireccional: `pairs: [{id, color, locA, locB, owner}]`. A→B y B→A resuelven al otro extremo.
- Rotura (`BlockBreakEvent`): si se rompe una alfombra registrada → se elimina el par completo (la otra queda `sin enlazar`), se dropea 1x Alfombra TP del color (no la vanilla), mensaje al dueño si está online. Si la rompe otro jugador con permiso de construir, se permite igual y se notifica.
- Protección v1: cancelar `BlockPistonExtend/Retract` y `EntityExplode/BlockExplode` que afecten a alfombras registradas. No se protege contra fuego/lava en v1 (se documenta).

## 4. Activación (TP)
- Trigger: jugador de pie sobre una alfombra TP enlazada + agachado. Se detecta por `PlayerToggleSneakEvent` (empieza a agacharse estando encima) y por `PlayerMoveEvent` con throttle (entra caminando ya agachado a un bloque nuevo que es origen). Solo estando parado encima, no al pasar sin sneak.
- Flujo: buscar par por ubicación → destino = otro extremo → validaciones → cooldown → teleport.
- Validaciones destino: chunk cargado (cargar async si hace falta, como hace `ActiveEventManager`), bloque destino sigue siendo alfombra TP del mismo color, 2 bloques de aire encima libres. Si falla → `§cDestino bloqueado o destruido.` + sonido grave, sin TP.
- Teleport: centro del bloque destino `+0.5, +1.0`, yaw/pitch preservados. Cross-mundo permitido (mismo servidor).
- Cooldown: 3 s por jugador (`Map<UUID, Long>`). Mensaje si spam.
- Anti-bucle: tras TP se marca `justTeleported<UUID>` 2 s para no re-disparar al caer agachado en destino.
- Efectos: `ENDERMAN_TELEPORT` + partículas `PORTAL` en origen y destino.
- Permiso: requiere `vevent.tpcarpet.use`. Sin permiso → mensaje y no TP.

## 5. Persistencia
- Fichero: `plugins/vEventDrops/carpets.yml` (nuevo, gestionado por `TpCarpetManager`).
- Estructura:
  ```yaml
  pairs:
    - id: "a1b2c3"
      color: "RED_CARPET"
      worldA: "world"
      xA: 100
      yA: 64
      zA: 200
      worldB: "world"
      xB: -30
      yB: 70
      zB: 90
      owner: "uuid"
  unlinked:
    - {color: "BLUE_CARPET", world: "world", x: 1, y: 64, z: 2, owner: "uuid"}
  ```
- Carga en `onEnable`, guardado async en cada cambio + en `onDisable`. Migración: si el fichero no existe se crea vacío.

## 6. Arquitectura
- Nueva clase `com.vevent.managers.TpCarpetManager` (registro, enlace, TP, cooldown, persistencia, limpieza). Instanciada en `VEventPlugin.onEnable`, detenida en `onDisable`.
- `VEventPlugin`: `getTpCarpetManager()`, registro de las 16 recetas `registerTpCarpetRecipes()`.
- `CommandManager`: subcomando `givecarpet` + tab-complete (jugadores + colores). Reutiliza patrón `givescroll/givetable/givechest`.
- Listeners dentro del Manager (no nueva clase Listener para v1, igual que `DisenchantManager/RedstoneChestManager` hacen `registerEvents` internos).
- Sin base de datos, sin datapack, sin NMS.

## 7. Mensajes (es)
- `§aOrigen marcado (Rojo). Ahora shift+click en el destino.`
- `§a¡Alfombras enlazadas! (Rojo)`
- `§cDeben ser del mismo color.`
- `§cDestino bloqueado o destruido. Revisa la otra alfombra.`
- `§eEnlace anterior roto, nuevo enlace creado.`
- `§cEspera 3 s antes de volver a usarla.`

## 8. Casos borde
- Destino roto → queda sin enlazar, aviso al usar.
- Re-enlace → rompe par previo.
- Dos jugadores usan a la vez → cooldown individual, sin lock global (el TP es lectura).
- Reinicio → se recarga de `carpets.yml`.
- Alfombra sin enlazar + sneak → nada (o pista `§7Sin enlazar` con throttle 5 s).

## 9. Testing
- Manual en TestServer: dar 2 rojas, colocar, enlazar, sneak → TP ida y vuelta; probar distinto color (debe fallar); romper destino → mensaje; reiniciar → persiste; receta craftea 2; cooldown respeta 3 s.
- Sin tests automatizados en v1 (el proyecto no tiene framework de tests).

## 10. Fuera de alcance v1
- Enlace a más de 2 (red por color), TP con un solo toque sin sneak, coste por uso, GUI de pares, protección contra lava/fuego, límite de pares por jugador.
