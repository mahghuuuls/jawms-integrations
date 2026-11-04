package com.mahghuuuls.jawmsintegrations.validation;

import com.mahghuuuls.jass.api.JassApi;
import com.mahghuuuls.jass.api.StaminaChangeEvent;
import com.mahghuuuls.jass.api.StaminaContribution;
import com.mahghuuuls.jass.api.StaminaCostEvent;
import com.mahghuuuls.jass.api.StaminaPublicState;
import com.mahghuuuls.jawms.api.CastAttemptContext;
import com.mahghuuuls.jawms.api.CastAttemptOutcome;
import com.mahghuuuls.jawms.api.CastCostPreparation;
import com.mahghuuuls.jawms.api.CastParticipantOutcome;
import com.mahghuuuls.jawms.api.ICastCostParticipant;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Isolated validation mod. Its observer never prepares, settles or changes a cast. */
@Mod(modid = SpellStaminaFixture.ID, name = "Spell stamina validation", version = "dev",
        dependencies = "required-after:jawmsintegrations;required-after:jawms@[1.2.0,);required-after:jass@[1.2.0,)")
public final class SpellStaminaFixture implements ICastCostParticipant {
    public static final String ID = "spellstaminafixture";
    private static final Logger LOG = LogManager.getLogger(ID);
    private static final ResourceLocation SETUP = new ResourceLocation(ID, "setup");
    private static final ResourceLocation ADAPTER = new ResourceLocation("jawmsintegrations", "spell_stamina");

    private final Map<UUID, Trial> trials = new HashMap<>();
    private final Map<String, Spell> spells = new LinkedHashMap<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        StaminaContribution bonus = StaminaContribution.builder().flatMaximum(10).build();
        JassApi.registerContextualProvider(new ResourceLocation(ID, "temporary_maximum"), player -> {
            Trial trial = trials.get(player.getUniqueID());
            return trial != null && trial.hasTemporaryMaximum ? bonus : StaminaContribution.EMPTY;
        });
        // Sort before the adapter so the observer also receives its refused receipts.
        ManaApi.registerCastCostParticipant(new ResourceLocation("jawmsintegrations", "observe_spell_stamina"), this);
    }

    @SubscribeEvent
    public void registerSpells(RegistryEvent.Register<Spell> event) {
        for (String name : Arrays.asList("probe", "free", "charged", "channel")) {
            Spell spell = new ProbeSpell(name, name.equals("channel"));
            spells.put(name, spell);
            event.getRegistry().register(spell);
        }
    }

    @Mod.EventHandler
    public void starting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FixtureCommand());
    }

    @Mod.EventHandler
    public void stopped(FMLServerStoppedEvent event) {
        trials.clear();
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        trials.remove(event.player.getUniqueID());
    }

    @Override
    public CastCostPreparation prepare(CastAttemptContext context) {
        Trial trial = trials.get(context.getPlayer().getUniqueID());
        if (trial != null) {
            trial.prepares++;
            if (context.getManaPortion() == 0) trial.zeroManaPortions++;
        }
        return CastCostPreparation.notApplicable();
    }

    @Override
    public void onAttemptResolved(CastAttemptOutcome outcome) {
        Trial trial = trials.get(outcome.getPlayerId());
        if (trial == null) return;
        for (CastParticipantOutcome receipt : outcome.getParticipants()) {
            if (!ADAPTER.equals(receipt.getParticipantId())) continue;
            trial.receipts++;
            if (receipt.isAmountKnown()) trial.paid += receipt.getAmount();
            if (trial.receipts == 1 || trial.receipts == 20 || receipt.mustStopAfterSuccess()
                    || !outcome.isAccepted() && trial.receipts <= 3) {
                LOG.info("STAMINA_FIXTURE receipt trial={} count={} spell={} hand={} status={} effect={}"
                                + " mana={} staminaStatus={} stamina={} fault={} terminal={} cumulative={}",
                        trial.label, trial.receipts, outcome.getSpellId(), outcome.getHand(), outcome.getStatus(),
                        outcome.wasEffectSuccessful(), outcome.isLocalAmountKnown() ? outcome.getPaidMana() : "unknown",
                        receipt.getStatus(), receipt.isAmountKnown() ? receipt.getAmount() : "unknown",
                        receipt.getFailure(), receipt.mustStopAfterSuccess(), trial.paid);
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(outcome.getPlayerId());
                if (player != null) server.getCommandManager().executeCommand(server, "stamina inspect " + player.getName());
            }
            break;
        }
    }

    @SubscribeEvent
    public void preCast(SpellCastEvent.Pre event) {
        EntityLivingBase caster = event.getCaster();
        if (!(caster instanceof EntityPlayer) || caster.world.isRemote) return;
        Trial trial = trials.get(caster.getUniqueID());
        if (trial != null && trial.mode == Mode.CANCEL_CAST) event.setCanceled(true);
    }

    @SubscribeEvent
    public void postCast(SpellCastEvent.Post event) {
        EntityLivingBase caster = event.getCaster();
        if (!(caster instanceof EntityPlayer) || caster.world.isRemote) return;
        Trial trial = trials.get(caster.getUniqueID());
        if (trial != null && event.getSource() == SpellCastEvent.Source.SCROLL) trial.scrollPosts++;
    }

    @SubscribeEvent
    public void finish(SpellCastEvent.Finish event) {
        EntityLivingBase caster = event.getCaster();
        if (!(caster instanceof EntityPlayer)) return;
        if (caster.world.isRemote) {
            if (event.getSpell() == spells.get("channel")) {
                LOG.info("STAMINA_FIXTURE client_finish_event player={}", caster.getUniqueID());
            }
            return;
        }
        Trial trial = trials.get(caster.getUniqueID());
        if (trial != null) trial.finishEvents++;
    }

    @SubscribeEvent
    public void pricing(StaminaCostEvent event) {
        Trial trial = trials.get(event.getPlayer().getUniqueID());
        if (trial == null || !"jawmsintegrations".equals(event.getAction().getNamespace())) return;
        trial.priceEvents++;
        if (trial.mode == Mode.REPRICE) event.setCost(40);
        if (trial.mode == Mode.FREE_PRICE) event.setCanceled(true);
    }

    @SubscribeEvent
    public void payment(StaminaChangeEvent event) {
        Trial trial = trials.get(event.getPlayer().getUniqueID());
        if (trial != null && "jawmsintegrations".equals(event.getCause().getNamespace())) trial.paymentEvents++;
    }

    @SubscribeEvent
    public void manaPayment(ManaStateChangeEvent event) {
        Trial trial = trials.get(event.getPlayer().getUniqueID());
        if (trial != null && trial.mode == Mode.INVALIDATE_ON_MANA && ManaCauses.SPELL_COST.equals(event.getCause())) {
            JassApi.getStaminaService().set(event.getPlayer(), 5, SETUP);
        }
    }

    private final class ProbeSpell extends Spell {
        ProbeSpell(String name, boolean continuous) {
            super(ID, name, EnumAction.NONE, continuous);
        }

        @Override
        public boolean requiresPacket() {
            return false;
        }

        @Override
        public boolean cast(World world, EntityPlayer caster, EnumHand hand, int tick, SpellModifiers modifiers) {
            if (world.isRemote) return true;
            Trial trial = trials.get(caster.getUniqueID());
            if (trial == null) return true;
            trial.effects++;
            if (trial.mode == Mode.SPEND || trial.mode == Mode.FLOOR_DROP) {
                LOG.info("STAMINA_FIXTURE competing_spend trial={} actual={}", trial.label,
                        JassApi.getStaminaService().spend(caster, 1000, SETUP).getActualDelta());
            }
            if (trial.mode == Mode.FLOOR_DROP) {
                trial.hasTemporaryMaximum = false;
                JassApi.markModifierCacheDirty(caster);
                LOG.info("STAMINA_FIXTURE maximum changed inside effect trial={} maximum={}", trial.label,
                        JassApi.getStaminaService().getMaximumStamina(caster));
            }
            if (trial.mode == Mode.INVALIDATE) JassApi.getStaminaService().set(caster, 5, SETUP);
            if (trial.mode == Mode.THROW) throw new IntentionalEffectFailure();
            return trial.mode != Mode.FALSE;
        }

        @Override
        public void finishCasting(World world, EntityLivingBase caster, double x, double y, double z,
                                  EnumFacing facing, int tick, SpellModifiers modifiers) {
            if (world.isRemote) {
                LOG.info("STAMINA_FIXTURE client_finish_body player={} tick={}", caster.getUniqueID(), tick);
                return;
            }
            Trial trial = trials.get(caster.getUniqueID());
            if (trial != null) trial.finishBodies++;
        }
    }

    private final class FixtureCommand extends CommandBase {
        @Override public String getName() { return "spellstamina-fixture"; }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public String getUsage(ICommandSender sender) {
            return "/spellstamina-fixture setup <label> <spell|missile|scroll> <main|off> <stamina> <mana> <mode>"
                    + " | snapshot | status | observe | throw | clear";
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (args.length == 7 && args[0].equals("setup")) {
                setup(player, args);
            } else if (args.length == 1 && args[0].equals("snapshot")) {
                snapshot(player);
            } else if (args.length == 1 && args[0].equals("status")) {
                server.getCommandManager().executeCommand(server, "jawmsintegrations status");
                server.getCommandManager().executeCommand(server, "jawmsintegrations status " + player.getName());
            } else if (args.length == 1 && args[0].equals("observe")) {
                trials.put(player.getUniqueID(), new Trial("A5_rejoin", Mode.NORMAL, EnumHand.MAIN_HAND));
                snapshot(player);
            } else if (args.length == 1 && args[0].equals("throw")) {
                exerciseThrowingEffect(player);
            } else if (args.length == 1 && args[0].equals("clear")) {
                trials.remove(player.getUniqueID());
                JassApi.markModifierCacheDirty(player);
            } else {
                throw new CommandException(getUsage(sender));
            }
        }
    }

    private void setup(EntityPlayerMP player, String[] args) throws CommandException {
        String label = args[1];
        if (!label.matches("[A-Za-z0-9_-]{1,40}")) throw new CommandException("Invalid fixture label");
        boolean scroll = args[2].equals("scroll");
        Spell spell = args[2].equals("missile") ? Spells.magic_missile : spells.get(scroll ? "probe" : args[2]);
        if (spell == null) throw new CommandException("Unknown fixture spell");
        EnumHand hand;
        if (args[3].equals("main")) hand = EnumHand.MAIN_HAND;
        else if (args[3].equals("off")) hand = EnumHand.OFF_HAND;
        else throw new CommandException("Specify main or off");
        double stamina = CommandBase.parseDouble(args[4], 0, 100);
        int mana = CommandBase.parseInt(args[5], 0, 100);
        Mode mode;
        try {
            mode = Mode.valueOf(args[6]);
        } catch (IllegalArgumentException exception) {
            throw new CommandException("Unknown fixture mode");
        }
        trials.remove(player.getUniqueID());
        player.resetActiveHand();
        player.inventory.currentItem = 0;
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
        ItemStack stack;
        if (scroll) {
            stack = new ItemStack(WizardryItems.scroll, 1, spell.metadata());
        } else {
            stack = new ItemStack(ItemWand.getWand(Tier.NOVICE, null));
            Spell[] slots = new Spell[ItemWand.BASE_SPELL_SLOTS];
            Arrays.fill(slots, spell);
            WandHelper.setSpells(stack, slots);
            WandHelper.setCurrentCooldown(stack, 0);
        }
        player.setHeldItem(hand, stack);
        player.getFoodStats().setFoodLevel(20);
        Trial trial = new Trial(label, mode, hand);
        trials.put(player.getUniqueID(), trial);
        JassApi.markModifierCacheDirty(player);
        JassApi.getStaminaService().set(player, stamina, SETUP);
        ManaApi.getManaService().setCurrentMana(player, mana, SETUP);
        LOG.info("STAMINA_FIXTURE ready trial={} mode={} hand={} spell={} action={} charge={}",
                label, mode, hand, spell.getRegistryName(), stack.getItemUseAction(), spell.getChargeup());
        snapshot(player);
    }

    private void exerciseThrowingEffect(EntityPlayerMP player) throws CommandException {
        Trial trial = trials.get(player.getUniqueID());
        if (trial == null || trial.mode != Mode.THROW) throw new CommandException("Arm a THROW trial first");
        ItemStack stack = player.getHeldItem(trial.hand);
        if (!(stack.getItem() instanceof ItemWand)) throw new CommandException("Fixture wand missing");
        ItemWand wand = (ItemWand) stack.getItem();
        Spell spell = wand.getCurrentSpell(stack);
        SpellModifiers modifiers = wand.calculateModifiers(stack, player, spell);
        if (!wand.canCast(stack, spell, player, trial.hand, 0, modifiers)) {
            throw new CommandException("Throw trial did not reach the effect");
        }
        try {
            wand.cast(stack, spell, player, trial.hand, 0, modifiers);
            throw new CommandException("Expected the fixture effect to throw");
        } catch (IntentionalEffectFailure expected) {
            LOG.info("STAMINA_FIXTURE expected exception reached command boundary trial={}", trial.label);
            snapshot(player);
        }
    }

    private void snapshot(EntityPlayerMP player) throws CommandException {
        Trial trial = trials.get(player.getUniqueID());
        if (trial == null) throw new CommandException("No active fixture trial");
        StaminaPublicState stamina = JassApi.getStaminaService().getState(player);
        LOG.info("STAMINA_FIXTURE snapshot trial={} player={} mana={} stamina={} active={} heldCount={}"
                        + " effects={} finishEvents={} finishBodies={} scrollPosts={} prepares={} zeroManaPortions={}"
                        + " receipts={} knownPaid={} priceEvents={} paymentEvents={}",
                trial.label, player.getUniqueID(), ManaApi.getManaService().getCurrentMana(player),
                stamina == null ? "unavailable" : stamina.getStamina(), player.isHandActive(),
                player.getHeldItem(trial.hand).getCount(), trial.effects, trial.finishEvents, trial.finishBodies,
                trial.scrollPosts, trial.prepares, trial.zeroManaPortions, trial.receipts, trial.paid,
                trial.priceEvents, trial.paymentEvents);
    }

    private enum Mode {
        NORMAL, FALSE, THROW, SPEND, FLOOR_DROP, INVALIDATE, INVALIDATE_ON_MANA, REPRICE, FREE_PRICE, CANCEL_CAST
    }

    private static final class Trial {
        final String label;
        final Mode mode;
        final EnumHand hand;
        int effects, finishEvents, finishBodies, scrollPosts, prepares, zeroManaPortions;
        int receipts, priceEvents, paymentEvents;
        double paid;
        boolean hasTemporaryMaximum;

        Trial(String label, Mode mode, EnumHand hand) {
            this.label = label;
            this.mode = mode;
            this.hand = hand;
            this.hasTemporaryMaximum = mode == Mode.FLOOR_DROP;
        }
    }

    private static final class IntentionalEffectFailure extends RuntimeException {}
}
