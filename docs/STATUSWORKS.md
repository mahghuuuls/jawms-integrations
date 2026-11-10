# Authoring Statusworks mana contributions

Statusworks status and pool definitions can name the contribution types below. Pack authors choose the definitions, values, scaling and presentation. Installing JAWMS Integrations creates no status, pool or bonus.

The addon requires JAWMS 1.5.0 or newer. Statusworks is optional. Active mana effects require Statusworks 1.3.0 or newer and the JAWMS API 1.10 readiness contract. Statusworks 1.2.0 can validate these types for authoring while gameplay reports `UNSUPPORTED`. The server's `statusworks.enabled` setting defaults to enabled and takes effect on restart. With compatible native authoring, disabling gameplay preserves definition validation. Statusworks still manages the definitions and their native state. The operator command `/jawmsintegrations status [player]` reports availability and the mana effects accepted by JAWMS. Statusworks HUD labels describe the authored status or pool.

## Contribution types

Each ID uses the `jawmsintegrations:` namespace.

| ID after the namespace | Amount |
| --- | --- |
| `max_mana_flat` | Whole signed mana |
| `max_mana_percent` | Percentage points of maximum mana |
| `mana_regen_flat` | Mana per second |
| `mana_regen_percent` | Percentage points of regeneration |
| `mana_regen_delay_flat` | Seconds added to the post-cast delay |
| `mana_regen_delay_percent` | Percentage points of post-cast delay |
| `spell_efficiency` | Efficiency points for every school |
| `spell_efficiency_magic` | Magic efficiency points |
| `spell_efficiency_fire` | Fire efficiency points |
| `spell_efficiency_ice` | Ice efficiency points |
| `spell_efficiency_lightning` | Lightning efficiency points |
| `spell_efficiency_necromancy` | Necromancy efficiency points |
| `spell_efficiency_earth` | Earth efficiency points |
| `spell_efficiency_sorcery` | Sorcery efficiency points |
| `spell_efficiency_healing` | Healing efficiency points |

Amounts must be finite. Zero contributes nothing. Negative maximum and regeneration amounts reduce their stats. Negative delay amounts shorten the delay. A delay value of `-10` percent means 10% shorter. A percentage reduction beyond 100% or a negative efficiency amount is invalid.

Use `fixed` or `per_stack` on statuses and `fixed`, `per_amount` or `per_fill` on pools. Statusworks resolves stacking, amount, fill and configured update steps. A fixed amount can apply while an empty pool instance exists. The resolved flat maximum must fit a signed 32-bit integer. Each fractional flat maximum is rejected and reported separately, even when several fractions would sum to a whole number. Invalid sources are omitted and valid sources are added per stat and operation. If a sum falls outside its valid range, that field contributes zero and other valid fields still apply.

Mana effects are for real server players with status or pool contributions. Marks have no mana effect. Statusworks validates contribution IDs and forms, and its native strict mode can stop startup when a definition names an unavailable type.

## Java

In a pack addon's startup registration, use the normal Statusworks builders. Compile against the Statusworks API and use a namespace you own. If your addon always registers definitions using these contribution IDs, declare both Statusworks and JAWMS Integrations as runtime dependencies. The Statusworks API validates definitions when loading completes.

```java
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;

// In an initialization handler:
StatusworksApi.registerStatus(StatusDefinition.builder("mypack:mana_reserve")
        .defaultDuration(200)
        .contribute(Contribution.type("jawmsintegrations:max_mana_flat", ValueForm.FIXED, 20.0))
        .build());
StatusworksApi.registerPool(PoolDefinition.builder("mypack:casting_rhythm")
        .capacity(100)
        .startAmount(50)
        .contribute(Contribution.type("jawmsintegrations:mana_regen_delay_percent", ValueForm.PER_FILL, -10.0))
        .build());
```

## CraftTweaker

Install CraftTweaker 4.1.20 or newer for Statusworks's native script API. Use the default script loader. The separate JAWMS Integrations CraftTweaker setting controls its own scripting integration.

```zenscript
import mods.statusworks.Statuses;
import mods.statusworks.Pools;

Statuses.define("mypack:mana_reserve")
    .defaultDuration(200)
    .contributeType("jawmsintegrations:max_mana_flat", "fixed", 20.0)
    .register();

Pools.define("mypack:casting_rhythm")
    .capacity(100.0)
    .startAmount(50.0)
    .contributeType("jawmsintegrations:mana_regen_delay_percent", "per_fill", -10.0)
    .register();
```
