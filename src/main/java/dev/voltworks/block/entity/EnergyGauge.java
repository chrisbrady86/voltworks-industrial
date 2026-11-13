package dev.voltworks.block.entity;

/** A block entity whose stored energy is shown on the block's front gauge. */
public interface EnergyGauge {
	/** Highest gauge level (a full gauge). */
	int MAX_LEVEL = 6;

	int getStoredEnergy();

	int getMaxEnergy();

	/** 0 = empty, 6 = full. Any energy at all shows at least one bar. */
	default int gaugeLevel() {
		int stored = getStoredEnergy(), max = Math.max(1, getMaxEnergy());
		if (stored <= 0) return 0;
		return Math.max(1, Math.min(MAX_LEVEL, (int) Math.round((double) MAX_LEVEL * stored / max)));
	}
}
