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

// Draws timed subtitle text directly onto video frames after the other effects.
public class FrameSubtitler extends FrameProcessor {

    private final SubtitleState subtitleState;

    private BufferedImage textImage;
    private int previousHeight;

    private String previousText = "";

    public FrameSubtitler(SubtitleState subtitleState) {
        this.subtitleState = subtitleState;
    }

    @Override
    public void process(Frame frame, InfoFrame info) {

        String text = subtitleState.textAt(info.timestampUs());

        if (text.isBlank()
                || frame.image == null
                || frame.imageChannels != 3
                || !(frame.image[0] instanceof ByteBuffer pixels)) {
            return;
        }

        // Reuse the rendered text until the subtitle or frame height changes.
        if (textImage == null 
            || previousHeight != frame.imageHeight
            || !previousText.equals(text)) {
            createTextImage(text, frame.imageHeight);
            previousHeight = frame.imageHeight;
            previousText = text;
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

    private void createTextImage(String text, int frameHeight) {
        // Scale the font based on the video height.
        int fontSize = Math.max(18, frameHeight / 25);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);

        // Measure the text so the image is only as large as it needs to be.
        BufferedImage measurement = new BufferedImage(
                1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measure = measurement.createGraphics();
        measure.setFont(font);

        int width = measure.getFontMetrics().stringWidth(text) + 20;
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
                text, font, graphics.getFontRenderContext());

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