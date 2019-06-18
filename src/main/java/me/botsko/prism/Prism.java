package me.botsko.prism;

import au.com.addstar.dripreporter.DripReporterApi;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;

import io.papermc.lib.PaperLib;
import me.botsko.prism.database.PrismDataSource;
import me.botsko.prism.database.PrismDatabaseFactory;
import me.botsko.prism.managers.PlayerManager;
import me.botsko.prism.utils.MaterialAliases;
import me.botsko.prism.actionlibs.*;
import me.botsko.prism.appliers.PreviewSession;
import me.botsko.prism.bridge.PrismBlockEditHandler;
import me.botsko.prism.commands.PrismCommands;
import me.botsko.prism.commands.WhatCommand;
import me.botsko.prism.listeners.*;
import me.botsko.prism.listeners.self.PrismMiscEvents;
import me.botsko.prism.measurement.QueueStats;
import me.botsko.prism.measurement.TimeTaken;
import me.botsko.prism.monitors.OreMonitor;
import me.botsko.prism.monitors.UseMonitor;
import me.botsko.prism.parameters.*;
import me.botsko.prism.players.PrismPlayer;
import me.botsko.prism.purge.PurgeManager;
import me.botsko.prism.wands.Wand;

import org.apache.commons.lang.StringUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Prism extends JavaPlugin {

	public static PrismDataSource getPrismDataSource() {
		return prismDataSource;
	}

	/**
	 * Connection Pool
	 */
	private static PrismDataSource prismDataSource = null;

	/**
	 * Protected/private
	 */
	private static String plugin_name;
	private String plugin_version;
	private boolean debug;
	private static MaterialAliases items;
	// private Language language = null;
	private static Logger log = Logger.getLogger("Minecraft");
	private final ArrayList<String> enabledPlugins = new ArrayList<>();
	private static ActionRegistry actionRegistry;
	private static HandlerRegistry handlerRegistry;
	private static Ignore ignore;
	protected static List<Material> illegalBlocks;
	protected static List<EntityType> illegalEntities;
	protected static HashMap<String, String> alertedOres = new HashMap<>();
	private static HashMap<String, PrismParameterHandler> paramHandlers = new HashMap<>();
	
	public ScheduledThreadPoolExecutor getSchedulePool() {
		return schedulePool;
	}
	
	private final ScheduledThreadPoolExecutor schedulePool = new ScheduledThreadPoolExecutor(1);
	private final ScheduledThreadPoolExecutor recordingMonitorTask = new ScheduledThreadPoolExecutor(1);
	// private ScheduledFuture<?> scheduledPurgeExecutor;
	private PurgeManager purgeManager;
	
	private static Prism instance;

	/**
	 * Public
	 */
	public static Messenger messenger;
	public static FileConfiguration config;
	public static WorldEditPlugin plugin_worldEdit = null;
	public ActionsQuery actionsQuery;
	public OreMonitor oreMonitor;
	public UseMonitor useMonitor;
	public static ConcurrentHashMap<String, Wand> playersWithActiveTools = new ConcurrentHashMap<>();
	public ConcurrentHashMap<String, PreviewSession> playerActivePreviews = new ConcurrentHashMap<>();
	public ConcurrentHashMap<String, ArrayList<Block>> playerActiveViews = new ConcurrentHashMap<>();
	public ConcurrentHashMap<String, QueryResult> cachedQueries = new ConcurrentHashMap<>();
	public ConcurrentHashMap<Location, Long> alertedBlocks = new ConcurrentHashMap<>();
	public TimeTaken eventTimer;
	public QueueStats queueStats;
	public BukkitTask recordingTask;
	public int total_records_affected = 0;

	public DripReporterApi getMonitor() {
		return monitor;
	}

	private DripReporterApi monitor;
	private boolean monitoring;

	/**
	 * DB Foreign key caches
	 */
	public static HashMap<String, Integer> prismWorlds = new HashMap<>();
	public static HashMap<UUID, PrismPlayer> prismPlayers = new HashMap<>();
	public static HashMap<String, Integer> prismActions = new HashMap<>();

	public PlayerManager getpManager() {
		return pManager;
	}

	private PlayerManager pManager;
	/**
	 * We store a basic index of hanging entities we anticipate will fall, so that
	 * when they do fall we can attribute them to the player who broke the original
	 * block.
	 */
	public ConcurrentHashMap<String, String> preplannedBlockFalls = new ConcurrentHashMap<>();

	/**
	 * VehicleCreateEvents do not include the player/entity that created it, so we
	 * need to track players right-clicking rails with minecart vehicles, or water
	 * for boats
	 */
	public ConcurrentHashMap<String, String> preplannedVehiclePlacement = new ConcurrentHashMap<>();
	
	public Prism() {
		instance = this;
	}

	public boolean isDebug() {
		return debug;
	}
	/**
	 * Enables the plugin and activates our player listeners
	 */
	@Override
	public void onEnable() {

		plugin_name = this.getDescription().getName();
		plugin_version = this.getDescription().getVersion();

		log("Initializing Prism " + plugin_version + ". By Viveleroi.");
		PaperLib.suggestPaper(this);
		// Load configuration, or install if new
		loadConfig();
		debug = config.getBoolean("prism.debug");
		if (getConfig().getBoolean("prism.allow-metrics")) {
			Prism.log("Configuring Bstats to provide developer feedback");
			Metrics metrics = new Metrics(this);
		}

		// init db async then call back to complete enable.
		Bukkit.getScheduler().runTaskAsynchronously(instance, () -> {
			Prism.log("Configuring DataSource" + StringUtils.defaultIfEmpty(config.getString("datasource")," !ERROR!"));
			prismDataSource = PrismDatabaseFactory.createDataSource(config);
			Connection test_conn = null;
			if (prismDataSource != null) {
				try {
					test_conn = prismDataSource.getConnection();
					if (test_conn != null) {
						test_conn.close();
					}
				} catch (final SQLException e) {
					prismDataSource.handleDataSourceException(e);
				}
				}
			if (this.getServer().getPluginManager().getPlugin("DripReporter") != null && this.getServer().getPluginManager().getPlugin("DripReporter").isEnabled()) {
				monitor = (DripReporterApi) getServer().getPluginManager().getPlugin("DripReporter");
				if(monitor != null){
					monitoring = true;
				}
			}
			if (prismDataSource == null || test_conn == null) {
				final String[] dbDisabled = new String[4];
				dbDisabled[0] = "Prism will disable itself because it couldn't connect to a database.";
				dbDisabled[1] = "If you're using MySQL, check your config. Be sure MySQL is running.";
				dbDisabled[2] = "If you're using Derby, you will need to ensure the library is on the servers Classpath.";
				dbDisabled[3] = "For help - try http://discover-prism.com/wiki/view/troubleshooting/";
				logSection(dbDisabled);
				Bukkit.getScheduler().runTask(instance, () -> instance.onDisable());
				return;
			}
			Bukkit.getScheduler().runTask(instance, ()-> instance.enabled());
		});
	}
	private void enabled(){
			if (isEnabled()) {
				Bukkit.getScheduler().runTaskAsynchronously(instance, new Runnable() {
					@Override
					public void run() {
						// Info needed for setup, init these here
						handlerRegistry = new HandlerRegistry();
						actionRegistry = new ActionRegistry();

						// Setup databases
						prismDataSource.setupDatabase(actionRegistry);

						// Cache world IDs
						prismDataSource.cacheWorldPrimaryKeys(prismWorlds);
						pManager = new PlayerManager();
						pManager.cacheAllOnlinePlayer();

						// ensure current worlds are added
						for (final World w : getServer().getWorlds()) {
							if (!Prism.prismWorlds.containsKey(w.getName())) {
								prismDataSource.addWorldName(w.getName());
							}
						}

						// Apply any updates
						final Updater up = new Updater(Prism.getInstance());
						up.apply_updates();
					}
				});

				eventTimer = new TimeTaken(this);
				queueStats = new QueueStats();
				ignore = new Ignore(this);

				// Plugins we use
				checkPluginDependancies();

				// Assign event listeners
				getServer().getPluginManager().registerEvents(new PrismBlockEvents(this), this);
				getServer().getPluginManager().registerEvents(new PrismEntityEvents(this), this);
				getServer().getPluginManager().registerEvents(new PrismWorldEvents(), this);
				getServer().getPluginManager().registerEvents(new PrismPlayerEvents(this), this);
				getServer().getPluginManager().registerEvents(new PrismInventoryEvents(this), this);
				getServer().getPluginManager().registerEvents(new PrismVehicleEvents(this), this);

				// InventoryMoveItem
				if (getConfig().getBoolean("prism.track-hopper-item-events") && Prism.getIgnore().event("item-insert")) {
					getServer().getPluginManager().registerEvents(new PrismInventoryMoveItemEvent(), this);
				}

				if (getConfig().getBoolean("prism.tracking.api.enabled")) {
					getServer().getPluginManager().registerEvents(new PrismCustomEvents(this), this);
				}

				// Assign listeners to our own events
				// getServer().getPluginManager().registerEvents(new
				// PrismRollbackEvents(), this);
				getServer().getPluginManager().registerEvents(new PrismMiscEvents(), this);

				// Add commands
				getCommand("prism").setExecutor(new PrismCommands(this));
				getCommand("prism").setTabCompleter(new PrismCommands(this));
				getCommand("what").setExecutor(new WhatCommand(this));

				// Register official parameters
				registerParameter(new ActionParameter());
				registerParameter(new BeforeParameter());
				registerParameter(new BlockParameter());
				registerParameter(new EntityParameter());
				registerParameter(new FlagParameter());
				registerParameter(new IdParameter());
				registerParameter(new KeywordParameter());
				registerParameter(new PlayerParameter());
				registerParameter(new RadiusParameter());
				registerParameter(new SinceParameter());
				registerParameter(new WorldParameter());

				// Init re-used classes
				messenger = new Messenger(plugin_name);
				actionsQuery = new ActionsQuery(instance);
				oreMonitor = new OreMonitor(instance);
				useMonitor = new UseMonitor(instance);

				// Init async tasks
				actionRecorderTask();

				// Init scheduled events
				endExpiredQueryCaches();
				endExpiredPreviews();
				removeExpiredLocations();

				// Delete old data based on config
				launchScheduledPurgeManager();

				// Keep watch on db connections, other sanity
				launchInternalAffairs();

				if (config.getBoolean("prism.preload-materials")) {
					config.set("prism.preload-materials", false);
					saveConfig();
					Prism.log("Preloading materials - This will take a while!");

					items.initAllMaterials();
					Prism.log("Preloading complete!");
				}

				items.initMaterials(Material.AIR);
			}
	}

	/**
	 * 
	 * @return
	 */
	public static String getPrismName() {
		return plugin_name;
	}

	/**
	 * 
	 * @return
	 */
	public String getPrismVersion() {
		return this.plugin_version;
	}

	/**
	 * Load configuration and language files
	 */
	public void loadConfig() {
		final PrismConfig mc = new PrismConfig(this);
		config = mc.getConfig();

		// Cache config arrays we check constantly
		illegalBlocks = getConfig().getStringList("prism.appliers.never-place-block").stream()
				.map(Material::matchMaterial).filter(Objects::nonNull).collect(Collectors.toList());
		illegalEntities = getConfig().getStringList("prism.appliers.never-spawn-entity").stream().map(s -> {
			try {
				return EntityType.valueOf(s.toUpperCase());
			} catch (Exception ignored) {
			}

			return null;
		}).filter(Objects::nonNull).collect(Collectors.toList());

		final ConfigurationSection alertBlocks = getConfig().getConfigurationSection("prism.alerts.ores.blocks");
		alertedOres.clear();
		if (alertBlocks != null) {
			for (final String key : alertBlocks.getKeys(false)) {
				alertedOres.put(key, alertBlocks.getString(key));
			}
		}

		// Load language files
		// language = new Language( mc.getLang() );
		// Load items db
		items = new MaterialAliases();
	}

	/*

	  @return
	 */
	/*
	 * public Language getLang() { return this.language; }
	 */

	/**
	 * 
	 */
	public void checkPluginDependancies() {

		// WorldEdit
		final Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
		if (we != null) {
			plugin_worldEdit = (WorldEditPlugin) we;
			enabledPlugins.add(we.getName());
			// Easier and foolproof way.
			try {
				WorldEdit.getInstance().getEventBus().register(new PrismBlockEditHandler());
				log("WorldEdit found. Associated features enabled.");
			}
			catch (Throwable error) {
				log("Required WorldEdit version is 6.0.0 or greater! Certain optional features of Prism disabled.");
			}

		}
		else {
			log("WorldEdit not found. Certain optional features of Prism disabled.");
		}
	}

	/**
	 * 
	 * @return
	 */
	public boolean dependencyEnabled(String pluginName) {
		return enabledPlugins.contains(pluginName);
	}

	/**
	 * 
	 * @return
	 */
	public static List<Material> getIllegalBlocks() {
		return illegalBlocks;
	}

	/**
	 * 
	 */
	public static List<EntityType> getIllegalEntities() {
		return illegalEntities;
	}

	/**
	 * 
	 */
	public static HashMap<String, String> getAlertedOres() {
		return alertedOres;
	}

	/**
	 * 
	 * @return
	 */
	public static MaterialAliases getItems() {
		return items;
	}

	/**
	 * 
	 * @return
	 */
	public static ActionRegistry getActionRegistry() {
		return actionRegistry;
	}

	/**
	 * 
	 * @return
	 */
	public static HandlerRegistry getHandlerRegistry() {
		return handlerRegistry;
	}

	/**
	 * 
	 * @return
	 */
	public static Ignore getIgnore() {
		return ignore;
	}

	/**
	 * 
	 * @return
	 */
	public PurgeManager getPurgeManager() {
		return purgeManager;
	}

	/**
	 * Registers a parameter and a handler. Example:
	 * 
	 * pr l a:block-break. The "a" is an action, and the action handler will process
	 * what "block-break" refers to.
	 * 
	 * @param handler
	 */
	public static void registerParameter(PrismParameterHandler handler) {
		paramHandlers.put(handler.getName().toLowerCase(), handler);
	}

	/**
	 * 
	 * @return
	 */
	public static HashMap<String, PrismParameterHandler> getParameters() {
		return paramHandlers;
	}

	/**
	 * 
	 * @return
	 */
	public static PrismParameterHandler getParameter(String name) {
		return paramHandlers.get(name);
	}

	/**
	 * 
	 */
	public void endExpiredQueryCaches() {
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			final java.util.Date date = new java.util.Date();
			for (final Entry<String, QueryResult> query : cachedQueries.entrySet()) {
				final QueryResult result = query.getValue();
				final long diff = (date.getTime() - result.getQueryTime()) / 1000;
				if (diff >= 120) {
					cachedQueries.remove(query.getKey());
				}
			}
		}, 2400L, 2400L);
	}

	/**
	 * 
	 */
	public void endExpiredPreviews() {
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			final java.util.Date date = new java.util.Date();
			for (final Entry<String, PreviewSession> query : playerActivePreviews.entrySet()) {
				final PreviewSession result = query.getValue();
				final long diff = (date.getTime() - result.getQueryTime()) / 1000;
				if (diff >= 60) {
					// inform player

					final Player player = result.getPlayer();
					if (player.isOnline()) {
						player.sendMessage(Prism.messenger.playerHeaderMsg("Canceling forgotten preview."));
					}
					playerActivePreviews.remove(query.getKey());
				}
			}
		}, 1200L, 1200L);
	}

	/**
	 * 
	 */
	public void removeExpiredLocations() {
		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			final java.util.Date date = new java.util.Date();
			// Remove locations logged over five minute ago.
			for (final Entry<Location, Long> entry : alertedBlocks.entrySet()) {
				final long diff = (date.getTime() - entry.getValue()) / 1000;
				if (diff >= 300) {
					alertedBlocks.remove(entry.getKey());
				}
			}
		}, 1200L, 1200L);
	}

	/**
	 * 
	 */
	public void actionRecorderTask() {
		int recorder_tick_delay = getConfig().getInt("prism.queue-empty-tick-delay");
		if (recorder_tick_delay < 1) {
			recorder_tick_delay = 3;
		}
		// we schedule it once, it will reschedule itself
		recordingTask = getServer().getScheduler().runTaskLaterAsynchronously(this, new RecordingTask(this),
				recorder_tick_delay);
	}

	/**
	 * 
	 */
	public void launchScheduledPurgeManager() {
		final List<String> purgeRules = getConfig().getStringList("prism.db-records-purge-rules");
		purgeManager = new PurgeManager(this, purgeRules);
		// scheduledPurgeExecutor =
		schedulePool.scheduleAtFixedRate(purgeManager, 0, 12, TimeUnit.HOURS);
		// scheduledPurgeExecutor.cancel();
	}

	/**
	 * 
	 */
	public void launchInternalAffairs() {
		final InternalAffairs recordingMonitor = new InternalAffairs(this);
		recordingMonitorTask.scheduleAtFixedRate(recordingMonitor, 0, 5, TimeUnit.MINUTES);
	}

	/**
	 * 
	 * @param msg
	 */
	public void alertPlayers(Player player, String msg) {
		for (final Player p : getServer().getOnlinePlayers()) {
			if (!p.equals(player) || getConfig().getBoolean("prism.alerts.alert-player-about-self")) {
				if (p.hasPermission("prism.alerts")) {
					p.sendMessage(messenger.playerMsg(ChatColor.RED + "[!] " + msg));
				}
			}
		}
	}

	/**
	 * Inform the player of missing arguments
	 * @return
	 */
	public String msgMissingArguments() {
		return messenger.playerError("Missing arguments. Check /prism ? for help.");
	}

	/**
	 * Inform the player of invalid arguments
	 * @return
	 */
	public String msgInvalidArguments() {
		return messenger.playerError("Invalid arguments. Check /prism ? for help.");
	}

	/**
	 * Inform the player of an invalid command
	 * @return
	 */
	public String msgInvalidSubcommand() {
		return messenger.playerError("Prism doesn't have that command. Check /prism ? for help.");
	}

	/**
	 * Inform the player of a missing permission
	 * @return
	 */
	public String msgNoPermission() {
		return messenger.playerError("You don't have permission to perform this action.");
	}

	/**
	 * Report nearby players
	 * @param player
	 * @param msg
	 */
	public void notifyNearby(Player player, int radius, String msg) {
		if (!getConfig().getBoolean("prism.appliers.notify-nearby.enabled")) {
			return;
		}
		for (final Player p : player.getServer().getOnlinePlayers()) {
			if (!p.equals(player)) {
				if (player.getWorld().equals(p.getWorld())) {
					if (player.getLocation().distance(p.getLocation()) <= (radius
							+ config.getInt("prism.appliers.notify-nearby.additional-radius"))) {
						p.sendMessage(messenger.playerHeaderMsg(msg));
					}
				}
			}
		}
	}

	/**
	 * Log a message
	 * @param message
	 */
	public static void log(String message) {
		log.info("[" + getPrismName() + "]: " + message);
	}

	/**
	 * Log a warning
	 * @param message
	 */
	public static void warn(String message) {
		log.warning("[" + getPrismName() + "]: " + message);
	}

	/**
	 * Log a series of messages, precedent by a header
	 * @param messages
	 */
	public static void logSection(String[] messages) {
		if (messages.length > 0) {
			log("--------------------- ## Important ## ---------------------");
			for (final String msg : messages) {
				log(msg);
			}
			log("--------------------- ## ========= ## ---------------------");
		}
	}

	/**
	 * Log a debug message if config.yml has debug: true
	 * @param message
	 */
	public static void debug(String message) {
		if (Prism.getInstance().isDebug()) {
			log.info("[" + plugin_name + "]: " + message);
		}
	}

	/**
	 * Log the current location as a debug message
	 * @param loc
	 */
	public static void debug(Location loc) {
		debug("Location: " + loc.getX() + " " + loc.getY() + " " + loc.getZ());
	}
	
	public static Prism getInstance() {
		return instance;
	}

	/**
	 * Disable the plugin
	 */
	public void disablePlugin() {
		this.setEnabled(false);
	}

	/**
	 * Shutdown
	 */
	@Override
	public void onDisable() {
		if (getConfig().getBoolean("prism.database.force-write-queue-on-shutdown")) {
			final QueueDrain drainer = new QueueDrain(this);
			drainer.forceDrainQueue();
		}
		Bukkit.getScheduler().cancelTasks(this);
		// Close prismDataSource connections when plugin disables
		if (prismDataSource != null) {
			prismDataSource.dispose();
		}

		log("Closing plugin.");
		super.onDisable();
	}
}
