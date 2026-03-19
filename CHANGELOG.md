# Changelog

## 1.1.0
- Refactor interno para separar config, LuckPerms y webhook.
- Resolucion de jugadores mas segura usando jugador online, cache local o UUID conocido en LuckPerms.
- Mejora del flujo de webhook: validacion de URL, revision de codigos HTTP y rate limit por accion y jugador.
- Limpieza de entradas viejas del rate limit para evitar crecimiento innecesario de memoria.
- Alineacion de `README.md`, `plugin.yml` y `config.yml` con el comportamiento real del plugin.
- Añadidos tests unitarios basicos para utilidades de texto y serializacion.
