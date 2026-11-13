package dev.voltworks.network;

import dev.voltworks.Voltworks;
import dev.voltworks.storage.StorageNetwork.StoredItem;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: what the storage network behind an open terminal / crafter holds. */
public record StorageViewPayload(int containerId, boolean online, long used, long capacity, List<StoredItem> items) implements CustomPacketPayload {
	public static final Type<StorageViewPayload> TYPE = new Type<>(Voltworks.id("storage_view"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StorageViewPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StorageViewPayload::containerId,
			ByteBufCodecs.BOOL, StorageViewPayload::online,
			ByteBufCodecs.VAR_LONG, StorageViewPayload::used,
			ByteBufCodecs.VAR_LONG, StorageViewPayload::capacity,
			StoredItem.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageViewPayload::items,
			StorageViewPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
