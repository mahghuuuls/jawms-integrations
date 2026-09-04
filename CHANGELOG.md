# Changelog

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
