package dev.voltworks.client;

import dev.voltworks.item.HardDriveItem;
import dev.voltworks.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** GUI drawn entirely from colored rectangles, so no GUI textures are needed. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int SLOT_BG = 0xFF8B8B8B;
	private static final int DARK = 0xFF373737;
	private static final int ENERGY = 0xFFE03C28;
	private static final int ENERGY_TOP = 0xFFFF8A5C;
	private static final int PROGRESS = 0xFF4FC34F;
	private static final int FLAME = 0xFFFFA020;

	private static final int BAR_X = 12, BAR_Y = 17, BAR_W = 10, BAR_H = 52;
	private static final int HEAT_X = 154;

	public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = leftPos, y = topPos;

		// Panel with bevel
		g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
		g.fill(x, y, x + imageWidth - 1, y + 1, LIGHT);
		g.fill(x, y, x + 1, y + imageHeight - 1, LIGHT);
		g.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, SHADOW);
		g.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, SHADOW);

		for (Slot slot : menu.slots) {
			int sx = x + slot.x, sy = y + slot.y;
			g.fill(sx - 1, sy - 1, sx + 17, sy + 17, DARK);
			g.fill(sx, sy, sx + 17, sy + 17, LIGHT);
			g.fill(sx, sy, sx + 16, sy + 16, slot instanceof MachineMenu.UpgradeSlot ? 0xFF7A8AA0 : SLOT_BG);
		}

		// Energy bar (fills from the bottom)
		g.fill(x + BAR_X - 1, y + BAR_Y - 1, x + BAR_X + BAR_W + 1, y + BAR_Y + BAR_H + 1, DARK);
		g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFF1E1E1E);
		int filled = Math.round(menu.getEnergyFraction() * BAR_H);
		if (filled > 0) {
			int top = y + BAR_Y + BAR_H - filled;
			g.fill(x + BAR_X, top, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, ENERGY);
			g.fill(x + BAR_X, top, x + BAR_X + BAR_W, top + 1, ENERGY_TOP);
		}

		switch (menu.kind) {
			case GENERATOR -> {
				// Flame gauge above the fuel slot, shrinking as fuel burns
				int fx = x + 81, fy = y + 25, size = 14;
				g.fill(fx, fy, fx + size, fy + size, DARK);
				int h = (int) Math.ceil(menu.getProgressFraction(0) * size);
				if (h > 0) {
					g.fill(fx + 1, fy + size - h, fx + size - 1, fy + size, FLAME);
				}
			}
			case PROCESSING, ALLOY -> arrow(g, x + 80, y + 39, menu.getProgressFraction(0));
			case REACTOR -> {
				// Heat gauge on the right, coloured by danger level
				float heat = menu.getProgressFraction(0);
				int hx = x + HEAT_X, hy = y + BAR_Y;
				g.fill(hx - 1, hy - 1, hx + BAR_W + 1, hy + BAR_H + 1, DARK);
				g.fill(hx, hy, hx + BAR_W, hy + BAR_H, 0xFF1E1E1E);
				int h = Math.round(heat * BAR_H);
				int color = heat < 0.5F ? 0xFF4FC34F : heat < 0.7F ? 0xFFFFA020 : 0xFFE02020;
				if (h > 0) {
					g.fill(hx, hy + BAR_H - h, hx + BAR_W, hy + BAR_H, color);
				}
				g.fill(hx - 3, hy + BAR_H * 3 / 10, hx, hy + BAR_H * 3 / 10 + 1, 0xFFE02020); // 70% danger mark
				g.text(font, menu.getValue(4) + " J/t", x + 120, y + 6, 0xFF404040, false);
			}
			case CHARGER -> {
				// Charge level of the tool in the slot
				int cx = x + 58, cy = y + 54, w = 60;
				g.fill(cx - 1, cy - 1, cx + w + 1, cy + 7, DARK);
				g.fill(cx, cy, cx + w, cy + 6, 0xFF1E1E1E);
				int fillW = Math.round(menu.getProgressFraction(0) * w);
				if (fillW > 0) {
					g.fill(cx, cy, cx + fillW, cy + 6, 0xFF40C0FF);
				}
			}
			case DRIVE_RACK -> {
				// A fill bar under each drive, and whether the rack is powered (= its drives are online)
				for (int i = 0; i < menu.kind.slotCount(); i++) {
					Slot slot = menu.slots.get(i);
					int bx = x + slot.x, by = y + slot.y + 18;
					g.fill(bx, by, bx + 16, by + 3, DARK);
					if (slot.getItem().getItem() instanceof HardDriveItem drive) {
						float fill = (float) HardDriveItem.contents(slot.getItem()).total() / drive.size.capacity;
						int w = Math.max(1, Math.round(fill * 16));
						g.fill(bx, by, bx + w, by + 3, fill < 0.75F ? 0xFF40C0FF : fill < 0.95F ? 0xFFFFB020 : 0xFFFF4040);
					}
				}
				boolean online = menu.getProgressFraction(0) > 0;
				Component status = Component.translatable(online ? "gui.voltworks.rack.online" : "gui.voltworks.rack.offline");
				g.text(font, status, x + imageWidth - 8 - font.width(status), y + 6, online ? 0xFF2A7A2A : 0xFFB02020, false);
			}
			case DUAL_PROCESSING -> {
				arrow(g, x + 80, y + 25, menu.getProgressFraction(0));
				arrow(g, x + 80, y + 53, menu.getProgressFraction(1));
			}
		}
	}

	/** Progress bar with an arrow head, between an input and output slot. */
	private static void arrow(GuiGraphicsExtractor g, int ax, int ay, float p) {
		g.fill(ax, ay, ax + 24, ay + 8, DARK);
		g.fill(ax + 24, ay - 3, ax + 26, ay + 11, DARK);
		int w = Math.round(p * 24);
		if (w > 0) {
			g.fill(ax, ay + 1, ax + w, ay + 7, PROGRESS);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int bx = leftPos + BAR_X, by = topPos + BAR_Y;
		if (mouseX >= bx && mouseX < bx + BAR_W && mouseY >= by && mouseY < by + BAR_H) {
			g.setTooltipForNextFrame(Component.translatable("gui.voltworks.energy", menu.getEnergy(), menu.getCapacity()), mouseX, mouseY);
		}
		int hx = leftPos + HEAT_X;
		if (menu.kind == MachineMenu.Kind.REACTOR && mouseX >= hx && mouseX < hx + BAR_W && mouseY >= by && mouseY < by + BAR_H) {
			g.setTooltipForNextFrame(Component.translatable("gui.voltworks.heat", menu.getValue(2), menu.getValue(3)), mouseX, mouseY);
		}
	}
}
