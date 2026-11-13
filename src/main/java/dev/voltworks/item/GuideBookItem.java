package dev.voltworks.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Voltworks Guide: right-click to read it in the vanilla book screen. The pages live in the language
 * file as "guide.voltworks.page.1", ".2", ... (generated from tools/guide.txt), so they can be translated.
 */
public class GuideBookItem extends Item {
	/** Set by the client initializer; opens the book screen. Null on a dedicated server. */
	public static Runnable opener;

	public GuideBookItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() && opener != null) {
			opener.run();
		}
		return InteractionResult.SUCCESS;
	}
}
