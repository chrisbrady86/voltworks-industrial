package dev.voltworks.client;

import dev.voltworks.menu.NetworkMenu;
import dev.voltworks.network.StorageActionPayload;
import dev.voltworks.storage.StorageNetwork.StoredItem;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared GUI for the Storage Terminal and Computer Crafter: a search box and a scrollable grid of everything in the
 * network with counts. Click to take a stack, right-click for half, shift-click straight to your inventory; click while
 * holding an item to store it (right-click stores one). Typing filters by item name or id.
 */
public abstract class StorageScreen<M extends NetworkMenu> extends AbstractContainerScreen<M> {
	protected static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, DARK = 0xFF373737, SLOT_BG = 0xFF8B8B8B;
	protected static final int COLS = 9;

	private final int gridX, gridY, rows;
	protected EditBox search;
	private int scroll;

	protected StorageScreen(M menu, Inventory inventory, Component title, int height, int gridX, int gridY, int rows) {
		super(menu, inventory, title, 176, height);
		this.gridX = gridX;
		this.gridY = gridY;
		this.rows = rows;
	}

	/** Where the search box goes, relative to the panel: {x, y, width}. */
	protected abstract int[] searchBox();

	@Override
	protected void init() {
		super.init();
		int[] s = searchBox();
		search = new EditBox(font, leftPos + s[0], topPos + s[1], s[2], 12, Component.translatable("gui.voltworks.search"));
		search.setHint(Component.translatable("gui.voltworks.search"));
		search.setMaxLength(50);
		search.setResponder(text -> scroll = 0);
		addRenderableWidget(search);
	}

	/** The network's items matching the search text. */
	protected List<StoredItem> filtered() {
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return menu.view;
		return menu.view.stream().filter(s -> s.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
				|| BuiltInRegistries.ITEM.getKey(s.item().getItem()).getPath().contains(q)).toList();
	}

	private int maxScroll() {
		return Math.max(0, (filtered().size() + COLS - 1) / COLS - rows);
	}

	/** Index into {@link #filtered()} of the grid cell under the mouse, -1 if the mouse isn't on a cell. */
	private int cellAt(double mx, double my) {
		int cx = (int) Math.floor((mx - leftPos - gridX) / 18), cy = (int) Math.floor((my - topPos - gridY) / 18);
		if (mx < leftPos + gridX || my < topPos + gridY || cx >= COLS || cy >= rows) return -1;
		return (scroll + cy) * COLS + cx;
	}

	protected void drawPanel(GuiGraphicsExtractor g) {
		int x = leftPos, y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
		g.fill(x, y, x + imageWidth - 1, y + 1, LIGHT);
		g.fill(x, y, x + 1, y + imageHeight - 1, LIGHT);
		g.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, SHADOW);
		g.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, SHADOW);
		for (Slot slot : menu.slots) drawSlot(g, x + slot.x, y + slot.y, SLOT_BG);
	}

	protected static void drawSlot(GuiGraphicsExtractor g, int sx, int sy, int color) {
		g.fill(sx - 1, sy - 1, sx + 17, sy + 17, DARK);
		g.fill(sx, sy, sx + 17, sy + 17, LIGHT);
		g.fill(sx, sy, sx + 16, sy + 16, color);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		drawPanel(g);
		List<StoredItem> items = filtered();
		scroll = Math.min(scroll, maxScroll());
		int hovered = cellAt(mouseX, mouseY);
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < COLS; c++) {
				int sx = leftPos + gridX + c * 18 + 1, sy = topPos + gridY + r * 18 + 1;
				int index = (scroll + r) * COLS + c;
				drawSlot(g, sx, sy, index == hovered ? 0xFFA8B4C8 : 0xFF6E7A8C);
				if (index < items.size()) {
					StoredItem s = items.get(index);
					g.item(s.item(), sx, sy);
					g.itemDecorations(font, s.item(), sx, sy, compact(s.count()));
				}
			}
		}
		// Scroll position
		if (maxScroll() > 0) {
			int barX = leftPos + gridX + COLS * 18 + 2, top = topPos + gridY + 1, h = rows * 18 - 2;
			g.fill(barX, top, barX + 3, top + h, DARK);
			int knob = Math.max(8, h / (maxScroll() + 1));
			int ky = top + (h - knob) * scroll / maxScroll();
			g.fill(barX, ky, barX + 3, ky + knob, 0xFFE0E0E0);
		}
		Component status = !menu.online ? Component.translatable("gui.voltworks.storage.offline")
				: Component.translatable("gui.voltworks.storage.usage", String.format("%,d", menu.used), String.format("%,d", menu.capacity), menu.view.size());
		g.text(font, status, leftPos + gridX + 1, topPos + gridY + rows * 18 + 3, menu.online ? 0xFF404040 : 0xFFB02020, false);
	}

	/** 999, 3k, 64k, 2M ... at most 3-4 characters, so counts fit in a slot (the tooltip has the exact number). */
	public static String compact(long n) {
		if (n < 1_000) return Long.toString(n);
		if (n < 1_000_000) return (n / 1000) + "k";
		if (n < 1_000_000_000) return (n / 1_000_000) + "M";
		return (n / 1_000_000_000) + "G";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int cell = cellAt(mouseX, mouseY);
		List<StoredItem> items = filtered();
		if (cell >= 0 && cell < items.size() && menu.getCarried().isEmpty()) {
			StoredItem s = items.get(cell);
			List<Component> lines = new java.util.ArrayList<>(getTooltipFromItem(net.minecraft.client.Minecraft.getInstance(), s.item()));
			lines.add(Component.translatable("gui.voltworks.storage.count", String.format("%,d", s.count())).withStyle(net.minecraft.ChatFormatting.AQUA));
			g.setTooltipForNextFrame(font, lines, s.item().getTooltipImage(), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int cell = cellAt(event.x(), event.y());
		if (cell >= 0) {
			List<StoredItem> items = filtered();
			ItemStack item = cell < items.size() ? items.get(cell).item() : ItemStack.EMPTY;
			onGridClick(item, event.button(), event.hasShiftDown());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** A click on the storage grid ({@code item} is empty for an empty cell). */
	protected void onGridClick(ItemStack item, int button, boolean shift) {
		if (!menu.getCarried().isEmpty()) {
			send(item, button == 1 ? NetworkMenu.ACTION_INSERT_ONE : NetworkMenu.ACTION_INSERT_HELD, 0);
		} else if (!item.isEmpty()) {
			send(item, shift ? NetworkMenu.ACTION_TAKE_TO_INV : button == 1 ? NetworkMenu.ACTION_TAKE_HALF : NetworkMenu.ACTION_TAKE, 0);
		}
	}

	protected void send(ItemStack item, int action, int slot) {
		ClientPlayNetworking.send(new StorageActionPayload(menu.containerId, item, action, slot));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (cellAt(mx, my) >= 0 || maxScroll() > 0 && mx >= leftPos + gridX && mx < leftPos + gridX + COLS * 18 + 6
				&& my >= topPos + gridY && my < topPos + gridY + rows * 18) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	/** While typing in the search box, keys (like E) go to the box instead of closing the screen. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search != null && search.isFocused() && !event.isEscape()) {
			search.keyPressed(event);
			return true;
		}
		return super.keyPressed(event);
	}
}
