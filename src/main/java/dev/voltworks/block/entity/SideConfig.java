package dev.voltworks.block.entity;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * Item input/output configuration for each side of a machine. Sides are stored relative to the machine's
 * facing (front, back, left, right, top, bottom), so rotating a machine with the wrench keeps its setup.
 * Left and right are as seen when looking at the machine's front.
 */
public final class SideConfig {
	public enum Mode {
		NONE(false, false), INPUT(true, false), OUTPUT(false, true), BOTH(true, true);

		public final boolean input, output;

		Mode(boolean input, boolean output) {
			this.input = input;
			this.output = output;
		}

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public Component label() {
			return Component.translatable("side_mode.voltworks." + name().toLowerCase(Locale.ROOT));
		}
	}

	public enum Side {
		FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM;

		public Component label() {
			return Component.translatable("side.voltworks." + name().toLowerCase(Locale.ROOT));
		}

		public static Side of(Direction facing, Direction dir) {
			if (dir == Direction.UP) return TOP;
			if (dir == Direction.DOWN) return BOTTOM;
			if (dir == facing) return FRONT;
			if (dir == facing.getOpposite()) return BACK;
			return dir == facing.getClockWise() ? LEFT : RIGHT;
		}
	}

	private final Mode[] modes = new Mode[Side.values().length];

	public SideConfig(java.util.function.Function<Side, Mode> defaults) {
		for (Side s : Side.values()) modes[s.ordinal()] = defaults.apply(s);
	}

	public Mode get(Side side) {
		return modes[side.ordinal()];
	}

	public void set(Side side, Mode mode) {
		modes[side.ordinal()] = mode;
	}

	/** Packs all six modes into one int (2 bits each) for saving. */
	public int pack() {
		int v = 0;
		for (int i = 0; i < modes.length; i++) v |= modes[i].ordinal() << (i * 2);
		return v;
	}

	public void unpack(int v) {
		for (int i = 0; i < modes.length; i++) modes[i] = Mode.values()[(v >> (i * 2)) & 3];
	}
}
