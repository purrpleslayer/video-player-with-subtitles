package comp3011;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import org.bytedeco.javacv.Frame;

// Draws subtitle text directly onto video frames after the other effects.
// Currently uses test text to check that rendering works.
public class FrameSubtitler extends FrameProcessor {

    private static final String TEST_TEXT = "TEST TEST TEST";

    private BufferedImage textImage;
    private int previousHeight;

    @Override
    public void process(Frame frame, InfoFrame info) {
        // Only process frames with the expected three-channel pixel format.
        if (frame.image == null
                || frame.imageChannels != 3
                || !(frame.image[0] instanceof ByteBuffer pixels)) {
            return;
        }

        // Create the text image once and reuse it unless the frame height changes.
        if (textImage == null || previousHeight != frame.imageHeight) {
            createTextImage(frame.imageHeight);
            previousHeight = frame.imageHeight;
        }

        // Centre the subtitle near the bottom of the video frame.
        int startX = (frame.imageWidth - textImage.getWidth()) / 2;
        int startY = frame.imageHeight - textImage.getHeight()
                - Math.max(12, frame.imageHeight / 20);

        // Copy the subtitle pixels onto the frame without going outside its bounds.
        for (int y = 0; y < textImage.getHeight(); y++) {
            int frameY = startY + y;
            if (frameY < 0 || frameY >= frame.imageHeight) {
                continue;
            }

            for (int x = 0; x < textImage.getWidth(); x++) {
                int frameX = startX + x;
                if (frameX < 0 || frameX >= frame.imageWidth) {
                    continue;
                }

                int argb = textImage.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;

                // Skip transparent pixels so the original video stays visible.
                if (alpha == 0) {
                    continue;
                }

                int offset = frameY * frame.imageStride + frameX * 3;

                // Blend the subtitle colours with the frame's BGR pixel data.
                for (int channel = 0; channel < 3; channel++) {
                    int original = pixels.get(offset + channel) & 0xff;
                    int textColor = (argb >>> (channel * 8)) & 0xff;

                    int blended = (original * (255 - alpha)
                            + textColor * alpha) / 255;

                    pixels.put(offset + channel, (byte) blended);
                }
            }
        }
    }

    private void createTextImage(int frameHeight) {
        // Scale the font based on the video height.
        int fontSize = Math.max(18, frameHeight / 25);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);

        // Measure the text so the image is only as large as it needs to be.
        BufferedImage measurement = new BufferedImage(
                1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measure = measurement.createGraphics();
        measure.setFont(font);

        int width = measure.getFontMetrics().stringWidth(TEST_TEXT) + 20;
        int height = measure.getFontMetrics().getHeight() + 20;
        measure.dispose();

        // Use a transparent image to hold the rendered subtitle.
        textImage = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = textImage.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Turn the text into a shape so an outline can be drawn around it.
        TextLayout layout = new TextLayout(
                TEST_TEXT, font, graphics.getFontRenderContext());

        Shape outline = layout.getOutline(
                AffineTransform.getTranslateInstance(
                        10, 10 + layout.getAscent()));

        // Draw the black outline first then fill the letters in white.
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(3f));
        graphics.draw(outline);

        graphics.setColor(Color.WHITE);
        graphics.fill(outline);

        graphics.dispose();
    }
}