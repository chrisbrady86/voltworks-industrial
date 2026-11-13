package dev.voltworks.block;

import java.util.function.Supplier;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** A machine-style block that is part of a computer storage network (Drive Rack, Storage Terminal, Computer Crafter). */
public class ComputerBlock extends MachineBlock implements DataConnectable {
	public ComputerBlock(Supplier<BlockEntityType<?>> type, Properties properties) {
		super(type, properties);
	}
}
