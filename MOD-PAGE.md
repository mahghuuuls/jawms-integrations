Adds optional compatibility between [Just Another Wizardry Mana System](https://www.curseforge.com/minecraft/mc-mods/just-another-wizardry-mana-system) and other mods. Each integration activates only when its mod is installed and can be disabled independently.

### Included Integrations

Currently includes integrations for:

- [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools)
- [Ancient Spellcraft](https://www.curseforge.com/minecraft/mc-mods/ancient-spellcraft)

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>


### For Mod Developers

This project is not intended to be the only source of JAWMS integrations. It uses the same public JAWMS API available to developers creating their own addons.

## [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools)

Adds configurable JAWMS qualities to Electroblob Wizardry mage armor, including maximum mana, mana regeneration, Spell Efficiency, school-specific Spell Efficiency, and post-cast regeneration delay.

### Built-In Qualities

These positive qualities can appear on official Electroblob Wizardry mage armor by default:

- **Manawoven:** Increases maximum mana by 5%.
- **Meditative:** Increases mana regeneration by 5%.
- **Efficient Casting:** Adds 5 Spell Efficiency, improving mana-cost efficiency for spells from every school.
- **Swift Recovery:** Reduces the delay before mana begins regenerating after casting by 5%.
- **Magic Focus:** Adds 8 Magic Spell Efficiency, improving mana-cost efficiency for Magic spells.
- **Fire Focus:** Adds 8 Fire Spell Efficiency, improving mana-cost efficiency for Fire spells.
- **Ice Focus:** Adds 8 Ice Spell Efficiency, improving mana-cost efficiency for Ice spells.
- **Lightning Focus:** Adds 8 Lightning Spell Efficiency, improving mana-cost efficiency for Lightning spells.
- **Necromancy Focus:** Adds 8 Necromancy Spell Efficiency, improving mana-cost efficiency for Necromancy spells.
- **Earth Focus:** Adds 8 Earth Spell Efficiency, improving mana-cost efficiency for Earth spells.
- **Sorcery Focus:** Adds 8 Sorcery Spell Efficiency, improving mana-cost efficiency for Sorcery spells.
- **Healing Focus:** Adds 8 Healing Spell Efficiency, improving mana-cost efficiency for Healing spells.

### For Modpack Creators

Modpack authors can create additional Quality Tools qualities using JAWMS attributes. Add the following names to the `attributes` array of a quality in the Quality Tools configuration:

- `jawmsintegrations.max_mana_flat`: flat maximum mana. The amount must be a whole number.
- `jawmsintegrations.max_mana_percent`: percentage maximum mana.
- `jawmsintegrations.mana_regen_flat`: flat mana regeneration.
- `jawmsintegrations.mana_regen_percent`: percentage mana regeneration.
- `jawmsintegrations.spell_efficiency`: global Spell Efficiency.
- `jawmsintegrations.spell_efficiency_magic`: Magic Spell Efficiency.
- `jawmsintegrations.spell_efficiency_fire`: Fire Spell Efficiency.
- `jawmsintegrations.spell_efficiency_ice`: Ice Spell Efficiency.
- `jawmsintegrations.spell_efficiency_lightning`: Lightning Spell Efficiency.
- `jawmsintegrations.spell_efficiency_necromancy`: Necromancy Spell Efficiency.
- `jawmsintegrations.spell_efficiency_earth`: Earth Spell Efficiency.
- `jawmsintegrations.spell_efficiency_sorcery`: Sorcery Spell Efficiency.
- `jawmsintegrations.spell_efficiency_healing`: Healing Spell Efficiency.
- `jawmsintegrations.mana_regen_delay_reduction_flat`: flat post-cast mana-regeneration-delay reduction in seconds.
- `jawmsintegrations.mana_regen_delay_reduction_percent`: percentage post-cast mana-regeneration-delay reduction.

Use attribute operation `0`. Positive mana and regeneration values are bonuses, while negative values are penalties. Positive delay-reduction values shorten the delay, while negative values lengthen it. Spell Efficiency values cannot be negative, and percentage delay reduction cannot exceed 100.

For example, this gives a quality a +10% maximum-mana modifier:

```json
"attributes": [
  {
    "name": "jawmsintegrations.max_mana_percent",
    "amount": 10,
    "operation": 0
  }
]
```

## [Ancient Spellcraft](https://www.curseforge.com/minecraft/mc-mods/ancient-spellcraft)

Reworks selected mana-related baubles to use JAWMS mana. This includes:

- Lesser and Greater Mana Rings, which add flat maximum mana.
- Majestic Mana Charm, which adds percentage maximum mana.
- Crystal Ring, which adds Spell Efficiency.
- Everfull Flask of Mana, which stores and regenerates mana for offhand use.
- Ring of Dagorim, which can consume ordinary mana flasks when the wearer is low on mana.

## Configuration And Installation

The generated `jawmsintegrations.cfg` can enable or tune each installed integration. Changes require a restart.

JAWMS is required. Install the addon on both the client and server. Supported integration mods are optional.

[Source code](https://github.com/mahghuuuls/jawms-integrations)
