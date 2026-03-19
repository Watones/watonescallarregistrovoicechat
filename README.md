# WatonesCallarRegistroVoiceChat

Plugin de **Watones Network** para gestionar mutes de voice chat, aplicando un **grupo temporal de LuckPerms** y enviando **registros a Discord** mediante webhook.

> Compatibilidad: **Paper 1.21.x**
> Lenguaje/Build: **Java 21 + Maven**

---

## Caracteristicas
- `/callar` y `/descallar` con feedback configurable.
- Asigna un grupo temporal de LuckPerms por **1 dia fijo**.
- `/descallar` elimina solo nodos temporales de ese grupo.
- Envia mensaje a Discord Webhook cuando se aplica o retira el mute.
- Rate limit por accion y jugador para evitar duplicados.
- Valida webhook y registra respuestas HTTP fallidas.

---

## Dependencias
- [PaperMC 1.21+](https://papermc.io/downloads)
- [LuckPerms](https://luckperms.net/)

---

## Instalacion
1. Descarga el `.jar` desde **Releases** o desde la pestaña **Actions**.
2. Coloca el archivo en `plugins/` de tu servidor Paper.
3. Reinicia el servidor para generar `config.yml`.
4. Crea en LuckPerms el grupo configurado en `group_name`.

---

## Configuracion (`config.yml`)
```yaml
group_name: "mutevoice"
webhook_url: ""
modality_name: "Survival"
rate_limit_ms_per_target: 1500

messages:
  prefix: "&4&lWATONES &8| &r"
  usage_callar: "&cUso: &7/callar <jugador>"
  usage_descallar: "&cUso: &7/descallar <jugador>"
  muted: "&aJugador &f%target% &amuteado en voice chat por &e1 dia&a."
  unmuted: "&aMute de voice chat retirado de &f%target%&a."
  no_perm: "&cNo tienes permiso."
  not_found: "&cNo encontre al jugador &f%target%&c."
  error: "&cOcurrio un error, intenta nuevamente."

discord:
  use_embeds: true
  username: "Moderacion Watones"
  avatar_url: ""
  content_template: "**[{action}]** Staff: {staff} -> Jugador: {target} (duracion: {duration}) | {modality} | {timestamp}"
  embed:
    title: "Voice Chat {action}"
    description: "Registro de moderacion | Modalidad: {modality}"
    color: "#FF0044"
    footer: "Watones | {timestamp}"
    fields:
      - name: "Accion"
        value: "{action}"
        inline: true
      - name: "Staff"
        value: "{staff}"
        inline: true
      - name: "Jugador"
        value: "{target}"
        inline: true
      - name: "Duracion"
        value: "{duration}"
        inline: true
```

## Comportamiento
- El mute aplica el grupo configurado con expiracion fija de `1d`.
- La resolucion de jugador prioriza online, cache local y UUID conocido en LuckPerms.
- El webhook corre fuera del hilo principal del servidor.
- Si `webhook_url` esta vacio o invalido, el plugin sigue funcionando sin Discord.

## Rendimiento
- No hay tareas por tick.
- No hay listeners masivos.
- El costo de CPU y memoria es muy bajo porque solo actua al ejecutar comandos.
- El uso de red queda aislado en un hilo dedicado.
