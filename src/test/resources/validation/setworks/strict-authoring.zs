#loader setworks
import mods.setworks.Setworks;

Setworks.set("jawms_test:authoring")
    .name("Mana Authoring Test")
    .piece("hood", "head", <minecraft:leather_helmet>);

Setworks.bonus("jawms_test:authoring")
    .requires("jawms_test:authoring", 1)
    .effect("jawmsintegrations:mana", {stat: "max_mana", operation: "flat", amount: "20"})
    .potion("minecraft:speed", 1);
