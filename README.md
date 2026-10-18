# Shulkerboxes Reloaded

An independently written Fabric mod for Minecraft, inspired by the functionality of Advanced Shulkerboxes. No upstream mod code or artwork is included in the release.

## Installation

Install `shulkerboxes_reloaded-<version>.jar` and Fabric API on a Fabric server, or in your local Minecraft instance for singleplayer.
Multiplayer clients can connect without installing this mod. Optional client installation handles item-use prediction locally.

## Usage

- Right-click with a shulker box in either hand to open it.
- Right-click a box in your own survival inventory with an empty cursor to open it directly. Left-click moves it normally. Other container screens keep their normal controls.
- Sneak while using a box on a block to place it. Interactable blocks such as chests retain vanilla interaction priority.
- Use the standard 27-slot shulker screen, including shift-clicks and dragging. The open box stays locked in its inventory slot. Shulker boxes cannot be nested.
- Custom names, colors, existing contents, opening/closing sounds, lock components and unopened loot tables are supported.

Configuration: `config/shulkerboxes_reloaded.properties` (created on first launch):

```properties
# Set false to allow normal placement without sneaking.
sneak_place=true
```

Restart to apply changes. If installed on both sides, keep this option consistent on client and server for consistent prediction. The server decides the actual action. The old mod's configuration is not imported.

Run `./gradlew build` (Windows: `.\gradlew.bat build`) with Java 25.
The installable jar is in `build/libs`; do not install the `-sources.jar`.
`build` includes configuration and text-rendering regression verification.

## License

AGPL-3.0; see [LICENSE](LICENSE). Copyright (c) 2026 SwordfishBE.