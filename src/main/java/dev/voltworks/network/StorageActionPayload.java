package dev.voltworks.network;

import dev.voltworks.Voltworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Client -> server: the player clicked in the storage list of an open terminal / crafter. {@code item} is the entry
 * clicked (empty when clicking an empty cell), {@code action} one of the NetworkMenu ACTION_ constants, and
 * {@code slot} an extra argument (the crafter's pattern slot).
 */
public record StorageActionPayload(int containerId, ItemStack item, int action, int slot) implements CustomPacketPayload {
	public static final Type<StorageActionPayload> TYPE = new Type<>(Voltworks.id("storage_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StorageActionPayload::containerId,
			ItemStack.OPTIONAL_STREAM_CODEC, StorageActionPayload::item,
			ByteBufCodecs.VAR_INT, StorageActionPayload::action,
			ByteBufCodecs.VAR_INT, StorageActionPayload::slot,
			StorageActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
