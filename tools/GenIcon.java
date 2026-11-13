import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the Voltworks logo: a yellow lightning bolt over a gear on a blue steel plate.
 * Usage: java tools/GenIcon.java <project root>
 * Writes src/main/resources/assets/voltworks/icon.png (128px, mod list) and docs/images/logo.png (400px, CurseForge).
 */
public class GenIcon {
	public static void main(String[] args) throws Exception {
		File root = new File(args.length > 0 ? args[0] : ".");
		BufferedImage big = draw(400);
		write(big, new File(root, "docs/images/logo.png"));
		write(scale(big, 128), new File(root, "src/main/resources/assets/voltworks/icon.png"));
		System.out.println("done");
	}

	static BufferedImage draw(int s) {
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		double u = s / 100.0; // design on a 100x100 grid

		// Plate: rounded square with a vertical blue-steel gradient and a bevel
		RoundRectangle2D plate = new RoundRectangle2D.Double(3 * u, 3 * u, 94 * u, 94 * u, 22 * u, 22 * u);
		g.setPaint(new GradientPaint(0, (float) (3 * u), new Color(78, 96, 122), 0, (float) (97 * u), new Color(34, 42, 56)));
		g.fill(plate);
		g.setStroke(new BasicStroke((float) (3 * u)));
		g.setColor(new Color(20, 24, 32));
		g.draw(plate);
		g.setStroke(new BasicStroke((float) (1.2 * u)));
		g.setColor(new Color(255, 255, 255, 50));
		g.draw(new RoundRectangle2D.Double(6 * u, 6 * u, 88 * u, 88 * u, 18 * u, 18 * u));

		// Hazard stripe along the bottom
		Shape oldClip = g.getClip();
		g.setClip(new RoundRectangle2D.Double(6 * u, 80 * u, 88 * u, 11 * u, 6 * u, 6 * u));
		g.setColor(new Color(25, 25, 25));
		g.fill(new Rectangle2D.Double(0, 80 * u, s, 11 * u));
		g.setColor(new Color(240, 180, 40));
		for (int i = -2; i < 14; i++) {
			Path2D stripe = new Path2D.Double();
			double x = i * 9 * u;
			stripe.moveTo(x, 91 * u);
			stripe.lineTo(x + 4.5 * u, 91 * u);
			stripe.lineTo(x + 15.5 * u, 80 * u);
			stripe.lineTo(x + 11 * u, 80 * u);
			stripe.closePath();
			g.fill(stripe);
		}
		g.setClip(oldClip);

		// Gear behind the bolt
		Area gear = new Area(new Ellipse2D.Double(22 * u, 18 * u, 56 * u, 56 * u));
		for (int i = 0; i < 10; i++) {
			double a = Math.toRadians(i * 36);
			AffineTransform t = AffineTransform.getRotateInstance(a, 50 * u, 46 * u);
			gear.add(new Area(t.createTransformedShape(new Rectangle2D.Double(45 * u, 12 * u, 10 * u, 10 * u))));
		}
		gear.subtract(new Area(new Ellipse2D.Double(36 * u, 32 * u, 28 * u, 28 * u)));
		g.setColor(new Color(20, 24, 32, 140));
		g.fill(AffineTransform.getTranslateInstance(1.5 * u, 1.5 * u).createTransformedShape(gear));
		g.setPaint(new GradientPaint(0, (float) (14 * u), new Color(150, 160, 175), 0, (float) (78 * u), new Color(95, 104, 118)));
		g.fill(gear);
		g.setColor(new Color(20, 24, 32));
		g.setStroke(new BasicStroke((float) (1.6 * u)));
		g.draw(gear);

		// Lightning bolt
		Path2D bolt = new Path2D.Double();
		bolt.moveTo(57 * u, 10 * u);
		bolt.lineTo(31 * u, 50 * u);
		bolt.lineTo(48 * u, 50 * u);
		bolt.lineTo(40 * u, 82 * u);
		bolt.lineTo(70 * u, 38 * u);
		bolt.lineTo(52 * u, 38 * u);
		bolt.closePath();
		g.setColor(new Color(0, 0, 0, 110));
		g.fill(AffineTransform.getTranslateInstance(2 * u, 2 * u).createTransformedShape(bolt));
		g.setPaint(new GradientPaint((float) (40 * u), (float) (10 * u), new Color(255, 240, 120), (float) (55 * u), (float) (80 * u), new Color(250, 170, 20)));
		g.fill(bolt);
		g.setStroke(new BasicStroke((float) (2.6 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(60, 35, 0));
		g.draw(bolt);

		g.dispose();
		return img;
	}

	static BufferedImage scale(BufferedImage src, int size) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, size, size, null);
		g.dispose();
		return out;
	}

	static void write(BufferedImage img, File f) throws Exception {
		f.getParentFile().mkdirs();
		ImageIO.write(img, "png", f);
	}
}
