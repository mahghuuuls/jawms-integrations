# JAWMS Integrations

JAWMS Integrations adds optional compatibility between [Just Another Wizardry Mana System](https://www.curseforge.com/minecraft/mc-mods/just-another-wizardry-mana-system) and other Minecraft 1.12.2 mods.

- [Player and download-page description](MOD-PAGE.md)
- [Changelog](CHANGELOG.md)
- [CraftTweaker scripting API](docs/CRAFTTWEAKER.md)
- [License](LICENSE)
- [Third-party notices](THIRD-PARTY-NOTICES.md)

## Runtime Scope

- Standard Forge and Cleanroom runtimes for Minecraft 1.12.2.
- Integrations 1.4.0 requires JAWMS 1.4.1 or newer.
- Quality Tools, Ancient Spellcraft, CraftTweaker, Just Another Food System, Just Another Stamina System, and Setworks are optional. Each integration's gameplay effects activate when its supported mod is installed and enabled.
- Install JAWMS Integrations on both the client and server. Gameplay configuration and mana changes are server-authoritative.

## Quality Tools Integration

The mod adds twelve configurable positive default qualities to official Electroblob Wizardry mage armor. They cover percentage maximum mana, percentage mana regeneration, global and school-specific Spell Efficiency, and percentage post-cast mana-regeneration-delay reduction.

Modpack authors can also use these attributes in user-configured Quality Tools qualities with attribute operation `0`:

- `jawmsintegrations.max_mana_flat`
- `jawmsintegrations.max_mana_percent`
- `jawmsintegrations.mana_regen_flat`
- `jawmsintegrations.mana_regen_percent`
- `jawmsintegrations.spell_efficiency`
- `jawmsintegrations.spell_efficiency_magic`
- `jawmsintegrations.spell_efficiency_fire`
- `jawmsintegrations.spell_efficiency_ice`
- `jawmsintegrations.spell_efficiency_lightning`
- `jawmsintegrations.spell_efficiency_necromancy`
- `jawmsintegrations.spell_efficiency_earth`
- `jawmsintegrations.spell_efficiency_sorcery`
- `jawmsintegrations.spell_efficiency_healing`
- `jawmsintegrations.mana_regen_delay_reduction_flat`
- `jawmsintegrations.mana_regen_delay_reduction_percent`

## Ancient Spellcraft Integration

Selected Ancient Spellcraft mana items are adapted to JAWMS:

- Lesser Ring of Mana: +8 maximum mana.
- Greater Ring of Mana: +12 maximum mana.
- Majestic Mana Charm: +15% maximum mana.
- Crystal Ring: +25 Spell Efficiency.
- Everfull Flask of Mana: stores up to 100 mana, regenerates while carried, and restores player mana from the offhand.
- Ring of Dagorim: can consume ordinary mana flasks when the wearer is below its configured mana threshold.

Ancient Spellcraft items with unrelated self-contained bauble mana remain unchanged.

## CraftTweaker Integration

Pack authors can inspect coherent JAWMS mana state, set or restore mana, perform atomic exact consumption or bounded draining, and start the current post-cast regeneration lockout from ZenScript. See the [CraftTweaker scripting API](docs/CRAFTTWEAKER.md) for the stable names, units, results, errors, and examples.

## Just Another Food System

[Just Another Food System](https://www.curseforge.com/minecraft/mc-mods/just-another-food-system) food definitions can grant maximum mana, mana regeneration, Spell Efficiency by school or across all schools, and changes to the post-cast regeneration delay. Each bonus lasts while its food is active. Define the bonuses in `config/jafs/foods.json`. For example, this modifier adds 20 maximum mana to a food:

```json
{"property":"jawmsintegrations:max_mana","operation":"flat","amount":20}
```

Use the `jawmsintegrations:` prefix for these properties:

| Property suffix | Operations and units |
| --- | --- |
| `max_mana` | `flat` in whole mana or `percent` in percentage points |
| `mana_regen` | `flat` in mana per second or `percent` in percentage points |
| `mana_regen_delay` | `flat` in seconds or `percent` in percentage points. Negative values shorten the delay. |
| `spell_efficiency` | `flat` in non-negative Spell Efficiency points across all schools |
| `spell_efficiency_<school>` | `flat` in non-negative points for `magic`, `fire`, `ice`, `lightning`, `necromancy`, `earth`, `sorcery`, or `healing` |

For percent modifiers, `10` means 10% and reductions are limited to 100%.

Set `jafs.enabled` to `false` in `config/jawmsintegrations.cfg` and restart to turn off food mana effects while preserving the food definitions and timers.

If your pack uses JAFS, consider setting `demonic_seal.enabled` to `false` in `config/jawms/jawms.cfg`. JAFS keeps hunger fixed, which can replenish the hunger that Demonic Seal spends to cover a mana shortfall.

## Just Another Stamina System

With [Just Another Stamina System](https://www.curseforge.com/minecraft/mc-mods/just-another-stamina-system) 1.2.0 or newer, Wizardry wand casts also use stamina. This includes addon spells cast through a Wizardry wand in either hand. Scrolls are exempt.

The base prices are 12 stamina per successful single cast and 8 per in-game second of continuous casting. Normal JASS rules govern the final price, exhaustion, and overexertion. Continuous spells stop when stamina runs out.

Use the `spell_stamina` section of `config/jawmsintegrations.cfg` to set `discreteCost` and `continuousCostPerSecond`. A price of zero keeps the normal JASS check for whether casting can start. Set `enabled` to `false` to turn off spell stamina entirely. Restart to apply changes. In multiplayer, the server settings apply.

## Setworks Integration

With [Setworks](https://www.curseforge.com/minecraft/mc-mods/setworks) 1.1.0 or newer, pack authors can add `jawmsintegrations:mana` to their set bonuses. The bonus applies its mana stats while Setworks keeps it active. Set definitions are supplied by the pack.

This CraftTweaker script defines a leather helmet set bonus that adds 20 maximum mana. Put it in the pack's `scripts/` directory and restart.

```zenscript
#loader setworks
import mods.setworks.Setworks;

Setworks.set("example:mana_set")
    .name("Mana Set")
    .piece("hood", "head", <minecraft:leather_helmet>);

Setworks.bonus("example:mana_set")
    .requires("example:mana_set", 1)
    .effect("jawmsintegrations:mana", {stat: "max_mana", operation: "flat", amount: "20"});
```

Pack authors using the Setworks Java API can supply the same effect ID and string parameters. Use the JAFS property suffixes above as Setworks stat names, with the same operations and units. For `mana_regen_delay`, negative amounts shorten the delay and positive amounts lengthen it. Setworks handles invalid definitions under its own validation settings.

Set `setworks.enabled` to `false` in `config/jawmsintegrations.cfg` and restart to turn off mana effects while keeping compatible definitions valid. Packs that use this effect need Setworks 1.1.0 or newer, since older versions can reject an unknown effect under strict validation.

## Configuration And Diagnostics

`config/jawmsintegrations.cfg` controls integration enablement, default qualities, replacement values, Everfull behavior, Ring of Dagorim behavior, and optional startup diagnostics. Configuration changes require a restart.

Operators can inspect integration state with:

```text
/jawmsintegrations status [player]
```

## License

JAWMS Integrations is available under the [MIT License](LICENSE). See [Third-Party Notices](THIRD-PARTY-NOTICES.md) for template attribution.
