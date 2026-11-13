package dev.voltworks.registry;

import dev.voltworks.Voltworks;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.menu.MachineMenu.Kind;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** One MenuType per GUI layout. */
public final class ModMenus {
	private static final Map<Kind, MenuType<MachineMenu>> TYPES = new EnumMap<>(Kind.class);

	static {
		for (Kind kind : Kind.values()) {
			TYPES.put(kind, Registry.register(BuiltInRegistries.MENU, Voltworks.id(kind.name().toLowerCase(Locale.ROOT)),
					new MenuType<>((id, inv) -> MachineMenu.client(kind, id, inv), FeatureFlags.VANILLA_SET)));
		}
	}

	private ModMenus() {
	}

	public static MenuType<MachineMenu> get(Kind kind) {
		return TYPES.get(kind);
	}

	public static void init() {
	}
}
