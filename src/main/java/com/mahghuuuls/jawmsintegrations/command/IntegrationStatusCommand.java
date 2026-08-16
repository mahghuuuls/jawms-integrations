package com.mahghuuuls.jawmsintegrations.command;

import com.mahghuuuls.jawmsintegrations.diagnostic.IntegrationDiagnosticsService;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/** Operator-only, read-only status command. */
public final class IntegrationStatusCommand extends CommandBase {

    private final IntegrationDiagnosticsService diagnostics;

    public IntegrationStatusCommand(IntegrationDiagnosticsService diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("Diagnostics service must not be null");
        }
        this.diagnostics = diagnostics;
    }

    @Override
    public String getName() {
        return "jawmsintegrations";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jawmsintegrations status [player]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0 || (args.length == 1 && "status".equalsIgnoreCase(args[0]))) {
            send(sender, diagnostics.overallStatus());
            return;
        }
        if (args.length == 2 && "status".equalsIgnoreCase(args[0])) {
            EntityPlayerMP player = getPlayer(server, sender, args[1]);
            send(sender, diagnostics.playerStatus(player));
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server,
                                          ICommandSender sender,
                                          String[] args,
                                          @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status");
        }
        if (args.length == 2 && "status".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    private static void send(ICommandSender sender, List<String> lines) {
        for (String line : lines) {
            sender.sendMessage(new TextComponentString(line));
        }
    }
}
