package com.account.serviceImpl.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

/**
 * Renders Thymeleaf HTML output to PDF bytes (OpenHTMLtoPDF).
 *
 * Put NotoSans-Regular.ttf and NotoSans-Bold.ttf in src/main/resources/fonts/
 * so "₹" renders. Without them the PDF still generates, with "Rs." instead.
 */
@Component
public class HtmlPdfRenderer {

    private static final Logger log = LogManager.getLogger(HtmlPdfRenderer.class);

    private static final String FONT_FAMILY = "Noto Sans";
    private static final String FONT_REGULAR = "/fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/NotoSans-Bold.ttf";

    public byte[] render(String html) {
        boolean fontsAvailable = resourceExists(FONT_REGULAR) && resourceExists(FONT_BOLD);
        if (!fontsAvailable) {
            log.warn("PDF fonts missing ({} / {}); printing '₹' as 'Rs.'", FONT_REGULAR, FONT_BOLD);
            html = html.replace("₹", "Rs. ");
        }

        org.w3c.dom.Document document = new W3CDom().fromJsoup(Jsoup.parse(html));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            if (fontsAvailable) {
                builder.useFont(() -> getClass().getResourceAsStream(FONT_REGULAR),
                        FONT_FAMILY, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
                builder.useFont(() -> getClass().getResourceAsStream(FONT_BOLD),
                        FONT_FAMILY, 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            }
            builder.withW3cDocument(document, baseUri());
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed: " + e.getMessage(), e);
        }
    }

    private boolean resourceExists(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String baseUri() {
        URL root = getClass().getResource("/");
        return root != null ? root.toExternalForm() : "file:/";
    }
}
