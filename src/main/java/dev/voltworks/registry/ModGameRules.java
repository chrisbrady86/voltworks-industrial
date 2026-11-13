package dev.voltworks.registry;

import dev.voltworks.Voltworks;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** World settings server operators can change with {@code /gamerule}. */
public final class ModGameRules {
	/** What happens when a Reactor Chamber reaches 100% heat. Lowercase because the names are the {@code /gamerule} values. */
	public enum Meltdown {
		/** A large explosion that destroys blocks and starts fires (the default). */
		destructive,
		/** The reactor and its fuel are destroyed and nearby entities are hurt, but no blocks break. */
		contained,
		/** No meltdown: the reactor shuts itself down until it cools to 90% heat. */
		off
	}

	/** {@code /gamerule voltworks:reactor_meltdown destructive|contained|off} */
	public static final GameRule<Meltdown> REACTOR_MELTDOWN = GameRuleBuilder.forEnum(Meltdown.destructive)
			.category(GameRuleCategory.MISC)
			.buildAndRegister(Voltworks.id("reactor_meltdown"));

	private ModGameRules() {
	}

	/** Loads the class so the rules are registered during mod init. */
	public static void init() {
	}
}
