# Changelog

## 1.4.0

### Added

- Optional Setworks support for pack-authored set bonuses. Active bonuses can modify maximum mana, mana regeneration, post-cast regeneration delay and Spell Efficiency using the same stats as Quality Tools.
- The `setworks.enabled` setting disables mana effects while keeping compatible authored definitions valid. Operator status shows the Setworks integration state and applied contribution.

### Compatibility

- JAWMS 1.4.1 or newer is required for all installations.
- Setworks mana effects require optional Setworks 1.1.0 or newer. Packs supply their own set definitions.

## 1.3.0

### Added

- Added optional Just Another Stamina System support for Wizardry wand casting, including addon spells cast through a wand. Scrolls are exempt.
- Added configurable base prices of 12 stamina per successful single cast and 8 stamina per in-game second of continuous casting. Normal JASS exhaustion rules apply.

### Compatibility

- Spell stamina requires JAWMS 1.2.0 or newer and JASS 1.2.0 or newer. Other integrations retain the existing JAWMS requirement.

## 1.2.0

### Added

- Added optional Just Another Food System support. Pack makers can configure active foods to modify maximum mana, mana regeneration, global and school-specific Spell Efficiency, and post-cast regeneration delay.
- Added the JAWMS Integrations icon to the in-game mod list.

### Compatibility

- Turning off the food mana integration preserves existing food definitions and active food timers.
- Use JAWMS 1.1.1 or newer on Cleanroom for compatibility with its built-in MixinBooter support.

## 1.1.1

### Fixed

- Fixed a startup crash when the Ancient Spellcraft integration was loaded on Cleanroom.
- Kept Ancient Spellcraft's legacy charge bars hidden for the Lesser Mana Ring, Greater Mana Ring, and Majestic Mana Charm while their JAWMS replacements are active. The native bars remain available when the integration is disabled.

### Compatibility

- Added Cleanroom support alongside standard Forge for Minecraft 1.12.2.

## 1.1.0

### Added

- Added an optional CraftTweaker API for inspecting JAWMS mana state, setting or restoring mana, exact consumption, bounded draining, and starting the post-cast regeneration lockout.

### Changed

- Updated the required JAWMS baseline to 1.1.0 or newer and adopted JAWMS API 1.6.
- Increased the default Swift Recovery quality from 5% to 10%. Existing user configuration values remain unchanged.
- Updated compatibility metadata and diagnostics for the three supported optional integrations: Quality Tools, Ancient Spellcraft, and CraftTweaker.

## 1.0.0

### Added

- Added optional Quality Tools support for JAWMS maximum mana, mana regeneration, Spell Efficiency, school-specific Spell Efficiency, and post-cast mana-regeneration delay.
- Added twelve configurable positive default qualities for official Electroblob Wizardry mage armor.
- Added JAWMS replacements for the Lesser and Greater Mana Rings, Majestic Mana Charm, Crystal Ring, Everfull Flask of Mana, and Ring of Dagorim from Ancient Spellcraft.
- Added per-integration configuration and a read-only operator status command.

### Compatibility

- Requires JAWMS 0.4.0.
- Supports Quality Tools 1.0.7 and Ancient Spellcraft 1.8.3 as optional integrations.
- Supports standard Forge for Minecraft 1.12.2 and installation on both client and server.
