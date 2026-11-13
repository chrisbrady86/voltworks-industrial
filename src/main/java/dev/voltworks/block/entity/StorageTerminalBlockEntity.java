package dev.voltworks.block.entity;

import dev.voltworks.menu.StorageTerminalMenu;
import dev.voltworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Storage Terminal: opens a searchable view of the whole storage network it's connected to. */
public class StorageTerminalBlockEntity extends BlockEntity implements MenuProvider {
	public StorageTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.STORAGE_TERMINAL, pos, state);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.voltworks.storage_terminal");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new StorageTerminalMenu(containerId, inventory, ContainerLevelAccess.create(level, worldPosition));
	}
}
