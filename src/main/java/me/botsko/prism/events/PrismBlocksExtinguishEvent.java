package me.botsko.prism.events;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PrismBlocksExtinguishEvent extends Event {

	/**
	 * Required by bukkit for proper event handling.
	 */
	private static final HandlerList handlers = new HandlerList();

	/**
	 * 
	 */
	private final ArrayList<BlockStateChange> blockStateChanges;

	/**
	 * 
	 */
	private final Player onBehalfOf;

	/**
	 * 
	 */
	protected final int radius;

	/**
	 * 
	 * @param blockStateChanges
	 * @param onBehalfOf
	 * @param radius
	 */
	public PrismBlocksExtinguishEvent(ArrayList<BlockStateChange> blockStateChanges, Player onBehalfOf, int radius) {
		this.blockStateChanges = blockStateChanges;
		this.onBehalfOf = onBehalfOf;
		this.radius = radius;
	}

	/**
	 * @return the originalBlock
	 */
	public ArrayList<BlockStateChange> getBlockStateChanges() {
		return blockStateChanges;
	}

	/**
	 * @return the onBehalfOf
	 */
	public Player onBehalfOf() {
		return onBehalfOf;
	}

	/**
	 * 
	 * @return
	 */
	public int getRadius() {
		return radius;
	}

	/**
	 * Required by bukkit for proper event handling.
	 */
	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	/**
	 * Required by bukkit for proper event handling.
	 * 
	 * @return
	 */
	public static HandlerList getHandlerList() {
		return handlers;
	}
}
