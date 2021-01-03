package se.troed.plugin.Courier.postmen;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import se.troed.plugin.Courier.Courier;

public class EnderPostman extends Postman {

    EnderPostman(Courier plug, Player p, int id, EntityType t) {
        super(plug, p, id, t);
    }

    public void spawn(Location l) {
        postman = (Enderman) player.getWorld().spawnEntity(l, EntityType.ENDERMAN);
        uuid = postman.getUniqueId();
        postman.setCustomName("Postman");
        postman.setCustomNameVisible(true);
        // gah, item vs block ...
        // MaterialData material = new MaterialData(Material.PAPER);
        ((Enderman)postman).setCarriedBlock(Material.BOOKSHELF.createBlockData());
    }

    @Override
    public void drop() {
        ((Enderman)postman).setCarriedBlock(Material.AIR.createBlockData());
        super.drop();
    }

}
