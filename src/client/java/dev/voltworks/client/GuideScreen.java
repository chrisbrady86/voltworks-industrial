package dev.voltworks.client;

import dev.voltworks.Voltworks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The Voltworks Guide: a contents page, then one page per topic with an optional picture, a row of item icons and
 * a few lines of text.
 *
 * <p>Pages come from the language file ("guide.voltworks.page.1", "...2", ..., generated from tools/guide.txt), so
 * they can be translated. A page's first line is its title. Lines starting with {@code @image name} (a picture in
 * {@code assets/voltworks/textures/gui/guide/name.png}) or {@code @items id id ...} (ids default to the voltworks
 * namespace) add pictures; the rest is the text. Addons add their pages with {@link #addPages(String)}.
 */
public class GuideScreen extends Screen {
	public static final int WIDTH = 260, HEIGHT = 230;
	private static final int PAD = 8, HEADER = 20, FOOTER = 22;
	/** Pictures are stored at twice this size and drawn scaled down, so they stay sharp at higher GUI scales. */
	public static final int IMAGE_W = 196, IMAGE_H = 110;
	private static final int FRAME = 0xFF23272B, COPPER = 0xFFB87333, PAPER = 0xFFEFE9DC, HEADER_BG = 0xFF33393F;
	private static final int TITLE = 0xFFFFB347, TEXT = 0xFF2A2A2A, LINK = 0xFF8A4A12, LINK_HOVER = 0xFFD9822B, MUTED = 0xFF7A7468;

	private static final List<String> PAGE_SOURCES = new ArrayList<>(List.of("guide." + Voltworks.MOD_ID + ".page."));
	private static int lastPage;

	/** Adds an addon's guide pages ("prefix1", "prefix2", ... in the language file) after the Voltworks pages. */
	public static void addPages(String keyPrefix) {
		if (!PAGE_SOURCES.contains(keyPrefix)) PAGE_SOURCES.add(keyPrefix);
	}

	/** One guide page: everything optional except the title. */
	public record Page(String title, @Nullable Identifier image, List<ItemStack> items, String body, List<String> unknownItems) {
		static Page parse(String raw) {
			String[] lines = raw.replace("\r\n", "\n").split("\n");
			Identifier image = null;
			List<ItemStack> items = new ArrayList<>();
			List<String> unknown = new ArrayList<>();
			StringBuilder body = new StringBuilder();
			for (int i = 1; i < lines.length; i++) {
				String line = lines[i];
				if (line.startsWith("@image ")) {
					image = Voltworks.id("textures/gui/guide/" + line.substring(7).strip() + ".png");
				} else if (line.startsWith("@items ")) {
					for (String id : line.substring(7).strip().split("\\s+")) {
						Identifier key = Identifier.tryParse(id.contains(":") ? id : Voltworks.MOD_ID + ":" + id);
						if (key != null && BuiltInRegistries.ITEM.containsKey(key)) items.add(new ItemStack(BuiltInRegistries.ITEM.getValue(key)));
						else unknown.add(id);
					}
				} else {
					body.append(line).append('\n');
				}
			}
			return new Page(lines[0].strip(), image, items, body.toString().strip(), unknown);
		}
	}

	/** All pages from the current language: the Voltworks pages, then any addon pages. */
	public static List<Page> loadPages() {
		List<Page> pages = new ArrayList<>();
		Language lang = Language.getInstance();
		for (String prefix : PAGE_SOURCES) {
			for (int i = 1; lang.has(prefix + i); i++) pages.add(Page.parse(lang.getOrDefault(prefix + i)));
		}
		return pages;
	}

	private final List<Page> pages = loadPages();
	/** -1 is the contents page. */
	private int page;
	private int left, top;

	public GuideScreen() {
		super(Component.translatable("item.voltworks.guide"));
		page = Math.min(lastPage, pages.size() - 1);
	}

	/** Opens the guide on a given page (-1 = contents). */
	public GuideScreen(int page) {
		this();
		this.page = Math.max(-1, Math.min(page, pages.size() - 1));
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		int y = top + HEIGHT - FOOTER + 3;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left + PAD, y, 20, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.voltworks.guide.contents"), b -> setPage(-1))
				.bounds(left + WIDTH / 2 - 35, y, 70, 16).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + WIDTH - PAD - 20, y, 20, 16).build());
	}

	private void turn(int delta) {
		setPage(Math.max(-1, Math.min(pages.size() - 1, page + delta)));
	}

	private void setPage(int p) {
		page = p;
		lastPage = p;
	}

	public int getPage() {
		return page;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isLeft() || event.key() == InputConstants.KEY_PAGEUP) {
			turn(-1);
			return true;
		}
		if (event.isRight() || event.key() == InputConstants.KEY_PAGEDOWN) {
			turn(1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0) turn(scrollY > 0 ? -1 : 1);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (page == -1) {
			int link = contentsLinkAt(event.x(), event.y());
			if (link >= 0) {
				setPage(link);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		g.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, FRAME);
		g.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, COPPER);
		g.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		g.fill(left, top, left + WIDTH, top + HEADER, HEADER_BG);
		g.fill(left, top + HEADER, left + WIDTH, top + HEADER + 1, COPPER);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		String title = page == -1 ? Component.translatable("gui.voltworks.guide.title").getString() : pages.get(page).title();
		g.text(font, title, left + PAD, top + 6, TITLE, true);
		String number = page == -1 ? "" : (page + 1) + " / " + pages.size();
		g.text(font, number, left + WIDTH - PAD - font.width(number), top + 6, 0xFFB8B0A0, false);
		if (page == -1) {
			drawContents(g, mouseX, mouseY);
		} else {
			drawPage(g, pages.get(page), mouseX, mouseY);
		}
	}

	private void drawContents(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = top + HEADER + 6;
		g.textWithWordWrap(font, Component.translatable("gui.voltworks.guide.intro"), left + PAD, y, WIDTH - 2 * PAD, MUTED, false);
		for (int i = 0; i < pages.size(); i++) {
			int[] r = contentsLink(i);
			boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + 10;
			g.text(font, pages.get(i).title(), r[0], r[1], hover ? LINK_HOVER : LINK, false);
		}
	}

	/** Contents link {x, y, width} for page i: two columns under the intro. */
	private int[] contentsLink(int i) {
		int perColumn = (pages.size() + 1) / 2;
		int col = i / perColumn, row = i % perColumn;
		int colWidth = (WIDTH - 2 * PAD) / 2;
		return new int[]{left + PAD + col * colWidth, top + HEADER + 34 + row * 11, colWidth - 4};
	}

	private int contentsLinkAt(double x, double y) {
		for (int i = 0; i < pages.size(); i++) {
			int[] r = contentsLink(i);
			if (x >= r[0] && x < r[0] + Math.min(r[2], font.width(pages.get(i).title())) && y >= r[1] - 1 && y < r[1] + 10) return i;
		}
		return -1;
	}

	private void drawPage(GuiGraphicsExtractor g, Page p, int mouseX, int mouseY) {
		int x = left + PAD, y = top + HEADER + 6;
		ItemStack hovered = ItemStack.EMPTY;
		if (p.image() != null) {
			g.fill(x - 1, y - 1, x + IMAGE_W + 1, y + IMAGE_H + 1, FRAME);
			g.blit(RenderPipelines.GUI_TEXTURED, p.image(), x, y, 0, 0, IMAGE_W, IMAGE_H, IMAGE_W * 2, IMAGE_H * 2, IMAGE_W * 2, IMAGE_H * 2);
			// Items in a column beside the picture
			int ix = x + IMAGE_W + 12;
			for (int i = 0; i < p.items().size() && i < 5; i++) {
				int iy = y + 2 + i * 22;
				g.item(p.items().get(i), ix, iy);
				if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) hovered = p.items().get(i);
			}
			y += IMAGE_H + 8;
		} else if (!p.items().isEmpty()) {
			for (int i = 0; i < p.items().size(); i++) {
				int ix = x + i * 22;
				g.item(p.items().get(i), ix, y);
				if (mouseX >= ix && mouseX < ix + 16 && mouseY >= y && mouseY < y + 16) hovered = p.items().get(i);
			}
			y += 24;
		}
		g.textWithWordWrap(font, FormattedText.of(p.body()), x, y, WIDTH - 2 * PAD, TEXT, false);
		if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered.getHoverName(), mouseX, mouseY);
	}

	/** Text lines that fit on a page with this picture/item layout. */
	private static int maxLines(Font font, Page p) {
		int used = HEADER + 6 + (p.image() != null ? IMAGE_H + 8 : p.items().isEmpty() ? 0 : 24);
		return (HEIGHT - FOOTER - used - 2) / font.lineHeight;
	}

	/** Problems with the current pages: text running into the buttons, too many icons, missing pictures. For the test. */
	public static List<String> problems() {
		Minecraft mc = Minecraft.getInstance();
		List<String> problems = new ArrayList<>();
		List<Page> pages = loadPages();
		for (Page p : pages) {
			List<FormattedCharSequence> lines = mc.font.split(FormattedText.of(p.body()), WIDTH - 2 * PAD);
			if (lines.size() > maxLines(mc.font, p)) {
				problems.add("Guide page '" + p.title() + "' has " + lines.size() + " lines of text, room for " + maxLines(mc.font, p));
			}
			if (p.image() != null && mc.getResourceManager().getResource(p.image()).isEmpty()) {
				problems.add("Guide page '" + p.title() + "' is missing its picture " + p.image());
			}
			if (!p.unknownItems().isEmpty()) problems.add("Guide page '" + p.title() + "' names unknown items " + p.unknownItems());
			if (p.items().size() > (p.image() != null ? 5 : (WIDTH - 2 * PAD) / 22)) problems.add("Guide page '" + p.title() + "' has too many items");
		}
		if ((pages.size() + 1) / 2 * 11 + HEADER + 34 > HEIGHT - FOOTER) problems.add("Guide contents don't fit: " + pages.size() + " pages");
		return problems;
	}
}
