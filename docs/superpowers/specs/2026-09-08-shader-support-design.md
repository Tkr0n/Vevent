# Diseño: Soporte de Shaders para VerkkuCraft Launcher

## Resumen

Agregar soporte completo de shaders al launcher VerkkuCraft. El sistema detectará shaders existentes en otros launchers (Minecraft oficial y SKLauncher), permitirá al usuario importarlos, e instalará automáticamente los mods necesarios (Iris + Sodium).

## Objetivos

1. Detectar shaders en launchers externos
2. Permitir importación selectiva de shaders
3. Instalar automáticamente Iris + Sodium cuando se importen shaders
4. Mantener shaders persistidos entre sesiones
5. Opcionalidad: los usuarios pueden ignorar completamente la función

## Componentes

### 1. Servicio de Detección de Shaders (`ShaderDetectionService.cs`)

**Responsabilidad:** Buscar shaders existentes en otros launchers instalados.

**Launchers soportados:**
- Minecraft Launcher oficial: `%appdata%\.minecraft\shaderpacks`
- SKLauncher: Misma ubicación que .minecraft

**Comportamiento:**
- Escanea las carpetas de shaderpacks de cada launcher
- Filtra archivos con extensión `.zip`
- Retorna lista de `DetectedShader` con:
  - `Name` - Nombre del archivo (sin extensión)
  - `FileName` - Nombre completo del archivo
  - `SourcePath` - Ruta completa de origen
  - `Size` - Tamaño en bytes
  - `SourceLauncher` - Nombre del launcher de origen

**Manejo de errores:**
- Si una carpeta no existe, la omite silenciosamente
- Si no hay permisos, la omite con log de advertencia
- Nunca falla completamente, retorna lista (posiblemente vacía)

### 2. Servicio de Instalación de Shaders (`ShaderInstallerService.cs`)

**Responsabilidad:** Copiar shaders seleccionados e instalar mods necesarios.

**Comportamiento:**
1. Recibe lista de shaders seleccionados por el usuario
2. Crea carpeta `shaderpacks` en `%appdata%\VerkkuCraft\game\[version]\shaderpacks`
3. Copia cada shader seleccionado a la carpeta destino
4. Verifica si Iris y Sodium están en la carpeta `mods`
5. Si faltan, los descarga desde Modrinth:
   - Iris: projectId `iris`
   - Sodium: projectId `sodium`
6. Retorna resultado de la operación

**Prevención de duplicados:**
- Verifica si ya existe un archivo con el mismo nombre en destino
- Si existe, pregunta al usuario si desea sobrescribir (o lo omite)

### 3. Modelo de Datos

#### En `manifest.json`

```json
{
  "shaderMods": [
    {
      "name": "Iris",
      "modrinthProjectId": "iris",
      "version": "",
      "fileName": "",
      "downloadUrl": ""
    },
    {
      "name": "Sodium",
      "modrinthProjectId": "sodium",
      "version": "",
      "fileName": "",
      "downloadUrl": ""
    }
  ]
}
```

#### Clases C# nuevas

```csharp
public class ShaderModInfo
{
    public string Name { get; set; } = string.Empty;
    public string ModrinthProjectId { get; set; } = string.Empty;
    public string Version { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string DownloadUrl { get; set; } = string.Empty;
}

public class DetectedShader
{
    public string Name { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string SourcePath { get; set; } = string.Empty;
    public long Size { get; set; }
    public string SourceLauncher { get; set; } = string.Empty;
}
```

### 4. Flujo de Usuario

```
┌─────────────────────────────────────────────────────────────┐
│  Inicio del Launcher                                        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  ¿Habilitar detección de shaders? (configuración)          │
└─────────────────────────────────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
        [Sí]                      [No / Primera vez]
              │                           │
              ▼                           ▼
┌─────────────────────────────────┐  ┌─────────────────────────┐
│  Escanear launchers externos    │  │  Continuar sin shaders  │
└─────────────────────────────────┘  └─────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│  ¿Se encontraron shaders?                                  │
└─────────────────────────────────────────────────────────────┘
              │
      ┌───────┴───────┐
      ▼               ▼
  [No]             [Sí]
      │               │
      ▼               ▼
┌───────────┐  ┌─────────────────────────────────────────────┐
│ Continuar │  │  Mostrar diálogo de selección               │
└───────────┘  │  - Lista de shaders con checkboxes          │
               │  - Opción: "Seleccionar todos"              │
               │  - Botón: "Importar" / "Cancelar"           │
               └─────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Copiar shaders seleccionados a shaderpacks                │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  ¿Iris y Sodium instalados?                                │
└─────────────────────────────────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
        [Sí]                      [No]
              │                           │
              ▼                           ▼
┌─────────────────────────┐  ┌─────────────────────────────────┐
│  Continuar              │  │  Descargar e instalar Iris      │
└─────────────────────────┘  │  + Sodium desde Modrinth        │
                             └─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Inicio del juego con shaders disponibles                  │
└─────────────────────────────────────────────────────────────┘
```

### 5. Persistencia

- Los shaders copiados permanecen en `%appdata%\VerkkuCraft\game\[version]\shaderpacks`
- No se borran automáticamente entre sesiones
- El usuario puede eliminarlos manualmente desde el juego o la carpeta
- Los mods Iris/Sodium se mantienen en `mods` como cualquier otro mod

### 6. Configuración

Opcionalmente, se puede agregar una opción en configuración del launcher:

```json
{
  "shaderDetectionEnabled": true
}
```

Por defecto: `true`

## Archivos a Modificar/Crear

### Archivos existentes (modificar)

| Archivo | Cambios |
|---------|---------|
| `manifest.json` | Agregar sección `shaderMods` |
| `LauncherManifest.cs` | Agregar propiedad `ShaderMods` y clase `ShaderModInfo` |
| `GameInstallerService.cs` | Integrar llamada a `ShaderInstallerService` |

### Archivos nuevos (crear)

| Archivo | Descripción |
|---------|-------------|
| `Services/ShaderDetectionService.cs` | Detección de shaders en launchers externos |
| `Services/ShaderInstallerService.cs` | Instalación de shaders y mods necesarios |
| `Models/DetectedShader.cs` | Modelo para shaders detectados |

## Orden de Implementación

1. **Modelo de datos**
   - Actualizar `manifest.json` con sección `shaderMods`
   - Agregar clases `ShaderModInfo` y `DetectedShader`

2. **Servicio de detección**
   - Implementar `ShaderDetectionService`
   - Probar detección en launchers reales

3. **Servicio de instalación**
   - Implementar `ShaderInstallerService`
   - Integrar con `ModrinthService` para descargas

4. **Integración en GameInstallerService**
   - Llamar detección al inicio
   - Mostrar UI de selección
   - Ejecutar instalación

5. **UI (opcional)**
   - Diálogo de selección de shaders
   - Integración en ventana principal

## Consideraciones Técnicas

### Compatibilidad de Versiones

- Los shaders son compatibles entre versiones de Minecraft
- No se necesita filtrar por versión de Minecraft
- Los mods (Iris/Sodium) sí necesitan filtrar por versión

### Rendimiento

- La detección es rápida (solo lista archivos)
- La copia puede ser lenta para muchos shaders grandes
- Usar progreso en UI para operaciones largas

### Seguridad

- No se ejecutan archivos de shaders
- Solo se copian archivos .zip
- Se validan tamaños razonables (< 100MB por shader)

## Criterios de Aceptación

1. ✅ El launcher detecta shaders en `.minecraft\shaderpacks`
2. ✅ El usuario puede seleccionar qué shaders importar
3. ✅ Los shaders se copian correctamente a la carpeta del juego
4. ✅ Iris y Sodium se instalan automáticamente si faltan
5. ✅ Los shaders persisten entre sesiones del launcher
6. ✅ La función es completamente opcional

## Fuera de Alcance

- UI para gestionar shaders instalados (activar/desactivar)
- Detección de otros launchers (CurseForge, MultiMC, etc.)
- Actualización automática de shaders
- Soporte para shaders .jar (solo .zip)

---

**Fecha:** 2026-09-08
**Estado:** Diseño aprobado
