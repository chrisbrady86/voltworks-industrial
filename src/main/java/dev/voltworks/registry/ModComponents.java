package dev.voltworks.registry;

import com.mojang.serialization.Codec;
import dev.voltworks.Voltworks;
import dev.voltworks.storage.DriveContents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

/** Item data components: stored energy on power tools, and the drill's 3x3 mode. */
public final class ModComponents {
	public static final DataComponentType<Integer> ENERGY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Voltworks.id("energy"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
	public static final DataComponentType<Boolean> WIDE_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Voltworks.id("wide_mode"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	/** Wrench mode: false = rotate, true = configure sides. */
	public static final DataComponentType<Boolean> CONFIG_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Voltworks.id("config_mode"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	/** Remaining burn time of a reactor fuel rod, in ticks. */
	public static final DataComponentType<Integer> FUEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Voltworks.id("fuel"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** What a hard drive holds. */
	public static final DataComponentType<DriveContents> DRIVE_CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Voltworks.id("drive_contents"),
			DataComponentType.<DriveContents>builder().persistent(DriveContents.CODEC).networkSynchronized(DriveContents.STREAM_CODEC).build());

	private ModComponents() {
	}

	public static void init() {
	}
}
