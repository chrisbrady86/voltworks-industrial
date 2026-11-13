package dev.voltworks.registry;

import dev.voltworks.Voltworks;
import dev.voltworks.menu.ComputerCrafterMenu;
import dev.voltworks.menu.FilterMenu;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.menu.MachineMenu.Kind;
import dev.voltworks.menu.StorageTerminalMenu;
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
	public static final MenuType<FilterMenu> FILTER = Registry.register(BuiltInRegistries.MENU, Voltworks.id("filter_pipe"),
			new MenuType<>(FilterMenu::client, FeatureFlags.VANILLA_SET));
	public static final MenuType<StorageTerminalMenu> STORAGE_TERMINAL = Registry.register(BuiltInRegistries.MENU, Voltworks.id("storage_terminal"),
			new MenuType<>(StorageTerminalMenu::client, FeatureFlags.VANILLA_SET));
	public static final MenuType<ComputerCrafterMenu> COMPUTER_CRAFTER = Registry.register(BuiltInRegistries.MENU, Voltworks.id("computer_crafter"),
			new MenuType<>(ComputerCrafterMenu::client, FeatureFlags.VANILLA_SET));

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
