Adds optional integrations for [Just Another Wizardry Mana System](https://www.curseforge.com/minecraft/mc-mods/just-another-wizardry-mana-system). Equipment, food, set bonuses, statuses, and pools can affect mana. Wand spells can spend stamina.

<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

Compatible integrations are enabled by default. Food, set bonuses, statuses, and pools need definitions from your pack.

## [Quality Tools](https://www.curseforge.com/minecraft/mc-mods/quality-tools)

Official [Electroblob's Wizardry](https://www.curseforge.com/minecraft/mc-mods/electroblobs-wizardry) mage armor can roll qualities that increase maximum mana, mana regeneration, or Spell Efficiency, or shorten the post-cast regeneration delay. [Quality defaults and custom attributes](https://github.com/mahghuuuls/jawms-integrations#quality-tools-integration)

## [Ancient Spellcraft](https://www.curseforge.com/minecraft/mc-mods/ancient-spellcraft)

The Lesser Ring of Mana, Greater Ring of Mana, and Majestic Mana Charm increase maximum mana. The Crystal Ring adds Spell Efficiency. The Everfull Flask of Mana recharges while carried and restores mana from the offhand. The Ring of Dagorim can use ordinary mana flasks when the wearer is low on mana.

## [CraftTweaker](https://www.curseforge.com/minecraft/mc-mods/crafttweaker)

Pack scripts can read and change mana or start the post-cast regeneration delay. [CraftTweaker scripting guide](https://github.com/mahghuuuls/jawms-integrations/blob/main/docs/CRAFTTWEAKER.md)

## [Just Another Food System](https://www.curseforge.com/minecraft/mc-mods/just-another-food-system)

Pack-defined foods can change maximum mana, regeneration, Spell Efficiency, and post-cast regeneration delay while active. [Food authoring and Demonic Seal compatibility](https://github.com/mahghuuuls/jawms-integrations#just-another-food-system)

## [Just Another Stamina System](https://www.curseforge.com/minecraft/mc-mods/just-another-stamina-system)

With version 1.2.0 or newer, Wizardry wand spells use stamina alongside mana, including addon spells cast through a wand. Scrolls remain stamina-free. The base costs are 12 stamina per successful single cast and 8 per second of continuous casting. Normal stamina rules apply, and continuous spells stop when stamina runs out.

## [Setworks](https://www.curseforge.com/minecraft/mc-mods/setworks)

Pack-defined equipment set bonuses can change maximum mana, mana regeneration, post-cast regeneration delay, and Spell Efficiency while active. Mana effects require Setworks 1.1.0 or newer. [Setworks authoring example](https://github.com/mahghuuuls/jawms-integrations#setworks-integration)

## [Statusworks](https://www.curseforge.com/minecraft/mc-mods/statusworks)

Pack-defined player statuses and pools can change maximum mana, mana regeneration, post-cast regeneration delay, and Spell Efficiency while their contributions apply. Mana effects require Statusworks 1.3.0 or newer. [Statusworks Java and CraftTweaker examples](https://github.com/mahghuuuls/jawms-integrations/blob/main/docs/STATUSWORKS.md)

## Configuration and Installation

Install the addon and JAWMS 1.5.0 or newer on both the client and server. In multiplayer, gameplay follows the server's `config/jawmsintegrations.cfg`. Changes require a restart. [Runtime compatibility](https://github.com/mahghuuuls/jawms-integrations#runtime-scope) [Configuration reference](https://github.com/mahghuuuls/jawms-integrations#configuration-and-diagnostics)
