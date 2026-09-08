package be.isach.ultracosmetics.command.subcommands;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.command.SubCommand;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.player.UltraPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public final class SubCommandKillEffects extends SubCommand {
    public SubCommandKillEffects(UltraCosmetics plugin) {
        super("killeffects", "Toggle visibility of other players' Kill Effects", "[on|off]", plugin, true);
    }
    @Override
    protected void onExePlayer(Player sender, String[] args) {
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            badUsage(sender);
            return;
        }
        UltraPlayer player = ultraCosmetics.getPlayerManager().getUltraPlayer(sender);
        if (player == null || !player.getProfile().isLoaded()) return;
        boolean visible = args.length == 2 ? args[1].equalsIgnoreCase("on") : !player.getProfile().isViewKillEffects();
        player.getProfile().setViewKillEffects(visible);
        MessageManager.send(sender, "Kill-Effects.Visibility-" + (visible ? "On" : "Off"));
    }
    @Override protected void onExeAnyone(CommandSender sender, String[] args) { notAllowed(sender); }
    @Override protected void tabComplete(CommandSender sender, String[] args, List<String> options) {
        if (args.length == 2) { options.add("on"); options.add("off"); }
    }
}
