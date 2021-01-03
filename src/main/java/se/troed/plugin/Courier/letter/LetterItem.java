package se.troed.plugin.Courier.letter;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

import se.troed.plugin.Courier.Courier;
import se.troed.plugin.Courier.CourierConfig;
import se.troed.plugin.Courier.Letter;

public class LetterItem {

	private static short STATE_OPEN = 1;
	private static short STATE_CLOSED = 2;

	private int id;
	private short state;
	private CourierConfig config;
	private List<String> lore = new ArrayList<String>();

	public LetterItem(int id) {
		this.id = id;
		this.state = LetterItem.STATE_OPEN;
		this.config = Courier.getInstance().getCConfig();
	}

	public LetterItem(Letter letter) {
		this.id = letter.getId();
		if (letter.getRead()) {
			this.state = LetterItem.STATE_OPEN;
		} else {
			this.state = LetterItem.STATE_CLOSED;
		}
		this.config = Courier.getInstance().getCConfig();
	}

	public void setOpen() {
		this.state = LetterItem.STATE_OPEN;
	}
	public void setClosed() {
		this.state = LetterItem.STATE_CLOSED;
	}

	public void setLore(List<String> lore) {
		this.lore = lore;
	}

	public void addLore(String line) {
		this.lore.add(line);
	}

	public void clearLore() {
		this.lore.clear();
	}

	public ItemStack getItem() {
		CourierConfig config = Courier.getInstance().getCConfig();
		ItemStack letterItem = new ItemStack(Material.FILLED_MAP, 1);
		letterItem.addUnsafeEnchantment(Enchantment.DURABILITY, id);
		MapMeta letterMeta = (MapMeta) letterItem.getItemMeta();
		
		if (letterMeta != null) {
			letterMeta.setMapId(Courier.getInstance().getCourierDB().getCourierMapId());
			letterMeta.setDisplayName(config.getLetterDisplayName());
			letterMeta.setLore(lore);

			int customModelData = this.getCustomModelDataValue();
			if (customModelData != 0) {
				letterMeta.setCustomModelData(customModelData);
			}

			letterItem.setItemMeta(letterMeta);
		}

		return letterItem;
	}

	private int getCustomModelDataValue() {
		if (this.state == LetterItem.STATE_OPEN) {
			return this.config.getOpenedLetterCustomModelData();
		}
		else if (this.state == LetterItem.STATE_CLOSED) {
			return this.config.getClosedLetterCustomModelData();
		}
		return 0;
	}
}
