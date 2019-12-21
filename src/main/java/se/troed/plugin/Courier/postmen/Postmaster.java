package se.troed.plugin.Courier.postmen;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import se.troed.plugin.Courier.Courier;

import java.util.List;
import java.util.logging.Level;

public class Postmaster {
	/**
	 * Picks a spot suitably in front of the player's eyes and checks to see if there's room
	 * for a postman to spawn in line-of-sight
	 *
	 * Currently this can fail badly not checking whether we're on the same Y ..
	 *
	 * Also: Should be extended to check at least a few blocks to the sides and not JUST direct line of sight
	 *
	 */
	public static Location findSpawnLocation(Courier courier, Player p) {
	    Location sLoc = null;

	    List<Block> blocks;
	    // http://dev.bukkit.org/server-mods/courier/tickets/67-task-of-courier-generated-an-exception/
	    // "@param maxDistance This is the maximum distance in blocks for the trace. Setting this value above 140 may lead to problems with unloaded chunks. A value of 0 indicates no limit"
	    try {
	        // o,o,o,o,o,o,x
	        blocks = p.getLineOfSight(null, courier.getCConfig().getSpawnDistance());
	    } catch (IllegalStateException e) {
	        blocks = null;
	        courier.getCConfig().clog(Level.WARNING, "caught IllegalStateException in getLineOfSight");
	    }
	    if(blocks != null && !blocks.isEmpty()) {
	        Block block = blocks.get(blocks.size()-1); // get last block
	        courier.getCConfig().clog(Level.FINE, "findSpawnLocation got lineOfSight");
	        if(!block.isEmpty() && blocks.size()>1) {
	            courier.getCConfig().clog(Level.FINE, "findSpawnLocation got non-air last block");
	            block = blocks.get(blocks.size()-2); // this SHOULD be an air block, then
	        }
	        if(block.isEmpty()) {
	            // find bottom
	            // http://dev.bukkit.org/server-mods/courier/tickets/62-first-letter-sent-and-received-crash/
	            courier.getCConfig().clog(Level.FINE, "findSpawnLocation air block");
	            while(block.getY() > 0 && block.getRelative(BlockFace.DOWN, 1).isEmpty()) {
	                courier.getCConfig().clog(Level.FINE, "findSpawnLocation going down ...");
	                block = block.getRelative(BlockFace.DOWN, 1);
	            }
	            // verify this is something we can stand on and that we fit
	            if(block.getY() > 0 && !block.getRelative(BlockFace.DOWN, 1).isLiquid()) {
	                if(Postman.getHeight(courier) > 2 && (!block.getRelative(BlockFace.UP, 1).isEmpty() || !block.getRelative(BlockFace.UP, 2).isEmpty())) {
	                    // Enderpostmen don't fit
	                } else if(Postman.getHeight(courier) > 1 && !block.getRelative(BlockFace.UP, 1).isEmpty()) {
	                    // "normal" height Creatures don't fit
	                } else {
	                    Location tLoc = block.getLocation();
	                    courier.getCConfig().clog(Level.FINE, "findSpawnLocation got location! [" + tLoc.getBlockX() + "," + tLoc.getBlockY() + "," + tLoc.getBlockZ() + "]");

	                    // make sure we spawn in the middle of the blocks, not at the corner
	                    sLoc = new Location(tLoc.getWorld(), tLoc.getBlockX()+0.5, tLoc.getBlockY(), tLoc.getBlockZ()+0.5);
	                }
	            }
	        }
	    }

	    // make a feeble attempt at not betraying vanished players
	    // the box will likely need to be a lot bigger
	    // just loop through all online players instead? ~300 checks max
	    // but that would mean vanished players can never receive mail
	    if(sLoc != null) {
	        int length = courier.getCConfig().getVanishDistance();
	        if(length > 0) {
	            List<Entity> entities = p.getNearbyEntities(length, 64, length);
	            for(Entity e : entities) {
	                if(e instanceof Player) {
	                    Player player = (Player) e;
	                    if(!player.canSee(p)) {
	                        sLoc = null; // it's enough that one Player nearby isn't supposed to see us
	                        courier.getCConfig().clog(Level.FINE, "Vanished player " + p.getName() + "'s postman could be seen by " + player.getName());
	                        break;
	                    }
	                }
	            }
	        }
	    }

	    if(sLoc == null) {
	        courier.getCConfig().clog(Level.FINE, "Didn't find room to spawn Postman");
	        // fail
	    }

	    return sLoc;
	}
}
