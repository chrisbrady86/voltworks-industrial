import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Turns the game test's guide screenshots ("NNNN_guide-NAME.png", taken by GuideScenes) into the guide's pictures:
 * assets/voltworks/textures/gui/guide/NAME.png at 392x220 (twice the size they're drawn at in GuideScreen).
 *
 * <pre>java tools/GuideImages.java build/run/clientGameTest/screenshots src/main/resources</pre>
 */
public class GuideImages {
	static final int W = 392, H = 220;
	static final Pattern NAME = Pattern.compile("\\d+_guide-([a-z_]+)\\.png");

	public static void main(String[] args) throws Exception {
		Path shots = Path.of(args[0]);
		Path out = Path.of(args[1], "assets/voltworks/textures/gui/guide");
		Files.createDirectories(out);
		int n = 0;
		try (var files = Files.list(shots)) {
			for (Path p : files.sorted().toList()) {
				Matcher m = NAME.matcher(p.getFileName().toString());
				if (!m.matches()) continue; // "guide-ui-..." shots of the guide screen itself contain a '-' and are skipped
				BufferedImage src = ImageIO.read(p.toFile());
				// Crop to the picture's aspect ratio around the centre, then scale down in two smooth steps.
				int cw = src.getWidth(), ch = cw * H / W;
				if (ch > src.getHeight()) {
					ch = src.getHeight();
					cw = ch * W / H;
				}
				BufferedImage crop = src.getSubimage((src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2, cw, ch);
				BufferedImage img = scale(scale(crop, W * 2, H * 2), W, H);
				ImageIO.write(img, "png", new File(out.toFile(), m.group(1) + ".png"));
				System.out.println(m.group(1) + ".png");
				n++;
			}
		}
		System.out.println(n + " guide pictures written to " + out);
	}

	static BufferedImage scale(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return dst;
	}
}
