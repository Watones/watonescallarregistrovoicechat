# WatonesCallarRegistroVoiceChat

Plugin de **Watones Network** para gestionar mutes de voice chat, aplicando un **grupo de LuckPerms** configurable y enviando **notificaciones a Discord** mediante Webhook.

> Compatibilidad: **Paper 1.21.x**  
> Lenguaje/Build: **Java 21 + Maven**

---

##  Características
- /callar y /descallar con feedback configurable.
- Asigna/Quita un **grupo de LuckPerms** (`group_name`) al jugador muteado.
- Envía mensaje a **Discord Webhook** cuando se aplica/retira mute.
- Rate limit por jugador y prefijos de mensajes personalizables.
- Config listo para múltiples modalidades (ej. `Survival`).

---

##  Dependencias
- [PaperMC 1.21+](https://papermc.io/downloads)
- [LuckPerms](https://luckperms.net/)

---

## 🔧 Instalación
1. Descarga el `.jar` desde **Releases** o desde la pestaña **Actions** (Artifact).
2. Coloca el archivo en `plugins/` de tu servidor Paper/Spigot.
3. Reinicia el servidor para generar `config.yml`.

---

## ⚙️ Configuración (`config.yml`)
```yaml
# Nombre del grupo de LuckPerms que se usa para silenciar
group_name: "mutevoice"

# Webhook de Discord (opcional). Si se deja vacío, no envía nada.
webhook_url: ""

# Nombre de la modalidad para mostrar en mensajes y/o embeds
modality_name: "Survival"

messages:
  prefix: "&6[&lWATONES &6]&r "
  usage_callar: "&cUso: /callar <jugador>"
  usage_descallar: "&cUso: /descallar <jugador>"
  muted: "&aMute aplicado a %jugador%."
  unmuted: "&aMute quitado de %jugador%."
  no_perm: "&cNo tienes permiso."
  not_found: "&cNo encontré al jugador."
  error: "&cOcurrió un error. Inténtalo de nuevo."
