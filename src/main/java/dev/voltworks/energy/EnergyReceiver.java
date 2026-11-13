package dev.voltworks.energy;

/** A block entity that can accept energy (J) from the network. */
public interface EnergyReceiver {
	/** @return the amount actually accepted. */
	int receiveEnergy(int maxAmount, boolean simulate);

	/** Storage blocks (batteries) receive leftover power after machines, and never feed each other. */
	default boolean isStorage() {
		return false;
	}
}
