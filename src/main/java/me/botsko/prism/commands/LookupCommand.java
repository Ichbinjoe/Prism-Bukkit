package me.botsko.prism.commands;

import me.botsko.prism.Prism;
import me.botsko.prism.actionlibs.ActionMessage;
import me.botsko.prism.actionlibs.ActionsQuery;
import me.botsko.prism.actionlibs.QueryParameters;
import me.botsko.prism.actionlibs.QueryResult;
import me.botsko.prism.actions.Handler;
import me.botsko.prism.appliers.PrismProcessType;
import me.botsko.prism.commandlibs.CallInfo;
import me.botsko.prism.commandlibs.Flag;
import me.botsko.prism.commandlibs.PreprocessArgs;
import me.botsko.prism.commandlibs.SubHandler;
import me.botsko.prism.utils.MiscUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LookupCommand implements SubHandler {

	private static ConcurrentMap<CommandSender,BukkitTask> currentQuery = new ConcurrentHashMap<>();
	/**
	 * 
	 */
	private final Prism plugin;

	/**
	 * 
	 * @param plugin
	 * @return
	 */
	public LookupCommand(Prism plugin) {
		this.plugin = plugin;
	}

	/**
	 * Handle the command
	 */
	@Override
	public void handle(final CallInfo call) {
		if(currentQuery.containsKey(call.getSender())){
			call.getSender().sendMessage("You have a current query running ");
			call.getSender().sendMessage("To cancel this query run this command with the 'cancel' arguement" );
			call.getSender().sendMessage("eg /pr l cancel" );

		}
		// Process and validate all of the arguments
		final QueryParameters parameters = PreprocessArgs.process(plugin, call.getSender(), call.getArgs(),
				PrismProcessType.LOOKUP, 1, !plugin.getConfig().getBoolean("prism.queries.never-use-defaults"));
		if (parameters == null) {
			return;
		}
		if(parameters.isCancelled()){
			if(currentQuery.containsKey(call.getSender())){
				BukkitTask canc = currentQuery.get(call.getSender());
				if(canc.isCancelled())
					return;
				else{
					canc.cancel();
				}
		}}
		/*
		  Run the lookup itself in an async task so the lookup query isn't done on the
		  main thread however these can take a while ...we should allow players to cancel or interrupt a query
		 todo allow task to be cancelled or interrupted and report on time
		 */
		if(currentQuery.containsKey(call.getSender())){
			call.getSender().sendMessage("You have a current query running ");
		}
		BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

			// determine if defaults were used
			final ArrayList<String> defaultsUsed = parameters.getDefaultsUsed();
			StringBuilder defaultsReminder = new StringBuilder();
			if (!defaultsUsed.isEmpty()) {
				defaultsReminder.append("Using defaults:");
				for (final String d : defaultsUsed) {
					defaultsReminder.append(" ").append(d);
				}
			}

			final ActionsQuery aq = new ActionsQuery(plugin);
			final QueryResult results = aq.lookup(parameters, call.getSender());//this is the big delay/
			StringBuilder sharingWithPlayers = new StringBuilder();
			for (final CommandSender shareWith : parameters.getSharedPlayers()) {
				sharingWithPlayers.append(shareWith.getName()).append(", ");
			}
			sharingWithPlayers = new StringBuilder(sharingWithPlayers.substring(0,
					(sharingWithPlayers.length() == 0) ? 0 : sharingWithPlayers.length() - 2));

			// Add current sender
			parameters.addSharedPlayer(call.getSender());

			for (final CommandSender player : parameters.getSharedPlayers()) {

				final boolean isSender = player.getName().equals(call.getSender().getName());

				if (!isSender) {
					player.sendMessage(Prism.messenger
							.playerHeaderMsg(ChatColor.YELLOW + "" + ChatColor.ITALIC + call.getSender().getName()
									+ ChatColor.GOLD + " shared these Prism lookup logs with you:"));
				} else if (sharingWithPlayers.length() > 0) {
					player.sendMessage(
							Prism.messenger.playerHeaderMsg(ChatColor.GOLD + "Sharing results with players: "
									+ ChatColor.YELLOW + "" + ChatColor.ITALIC + sharingWithPlayers));
				}

				if (!results.getActionResults().isEmpty()) {
					player.sendMessage(Prism.messenger.playerHeaderMsg("Showing " + results.getTotalResults()
							+ " results. Page 1 of " + results.getTotal_pages()));
					if ((defaultsReminder.length() > 0) && isSender) {
						player.sendMessage(Prism.messenger.playerSubduedHeaderMsg(defaultsReminder.toString()));
					}
					final List<Handler> paginated = results.getPaginatedActionResults();
					if (paginated != null) {
						int result_count = results.getIndexOfFirstResult();
						for (final Handler a : paginated) {
							final ActionMessage am = new ActionMessage(a);
							if (parameters.allowsNoRadius() || parameters.hasFlag(Flag.EXTENDED)
									|| plugin.getConfig().getBoolean("prism.messenger.always-show-extended")) {
								am.showExtended();
							}
							am.setResultIndex(result_count);
							MiscUtils.sendClickableTPRecord(am, player);
							result_count++;
						}
						MiscUtils.sendPageButtons(results, player);
					} else {
						player.sendMessage(Prism.messenger
								.playerError("Pagination can't find anything. Do you have the right page number?"));
					}
					if (parameters.hasFlag(Flag.PASTE)) {
						StringBuilder paste = new StringBuilder();
						for (final Handler a : results.getActionResults()) {
							paste.append(new ActionMessage(a).getRawMessage()).append("\r\n");
						}
						player.sendMessage(MiscUtils.paste_results(plugin, paste.toString()));
					}
				} else {
					if (defaultsReminder.length() > 0) {
						if (isSender) {
							player.sendMessage(Prism.messenger.playerSubduedHeaderMsg(defaultsReminder.toString()));
						}
					}
					if (isSender) {
						player.sendMessage(Prism.messenger.playerError("Nothing found." + ChatColor.GRAY
								+ " Either you're missing something, or we are."));
					}
				}
			}

			// Flush timed data
			plugin.eventTimer.printTimeRecord();
			currentQuery.remove(call.getSender());
		});
		currentQuery.putIfAbsent(call.getSender(), task);
	}

	@Override
	public List<String> handleComplete(CallInfo call) {
		return PreprocessArgs.complete(call.getSender(), call.getArgs());
	}
}