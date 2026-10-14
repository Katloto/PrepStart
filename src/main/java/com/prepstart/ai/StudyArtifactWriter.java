package com.prepstart.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates real, downloadable PDFs and PowerPoint slides without paid conversion services. */
@Component
public class StudyArtifactWriter {
    public record Artifact(Path file, String title, String type, String extension) { }
    private record Line(String text, boolean heading) { }

    public List<Artifact> write(String sourceTitle, List<GeminiStudyClient.Section> sections,
                                Path directory) throws IOException {
        Files.createDirectories(directory);
        List<Artifact> result = new ArrayList<>();
        // Distinct chapter PDFs + two full-document PDFs + PPTX and slide-handout PDF.
        for (int i = 0; i < sections.size(); i++) {
            GeminiStudyClient.Section s = sections.get(i);
            List<Line> lines = new ArrayList<>();
            lines.add(new Line(s.title(), true));
            lines.add(new Line("AI generated from: " + sourceTitle + ". Verify against the original.", false));
            lines.add(new Line(s.summary(), false));
            for (var p : s.points()) {
                lines.add(new Line(p.title(), true));
                lines.add(new Line(p.explanation(), false));
                lines.add(new Line("Source excerpt: " + p.quote(), false));
            }
            String name = "Study Part " + (i + 1) + " - " + s.title();
            result.add(pdf(directory, name, lines));
        }
        List<Line> full = new ArrayList<>(), revision = new ArrayList<>(), slidesPdf = new ArrayList<>();
        full.add(new Line("Study Guide - " + sourceTitle, true));
        full.add(new Line("AI-generated learning aid. It may omit or misinterpret source content.", false));
        revision.add(new Line("Revision Questions and Flashcards - " + sourceTitle, true));
        slidesPdf.add(new Line("Lecture Slide Handout - " + sourceTitle, true));
        for (GeminiStudyClient.Section s : sections) {
            full.add(new Line(s.title(), true));
            full.add(new Line(s.summary(), false));
            slidesPdf.add(new Line(s.title(), true));
            for (var p : s.points()) {
                full.add(new Line(p.title(), true));
                full.add(new Line(p.explanation(), false));
                slidesPdf.add(new Line(p.title() + ": " + p.explanation(), false));
            }
            revision.add(new Line(s.title(), true));
            for (var f : s.cards()) {
                revision.add(new Line("Q: " + f.question(), false));
                revision.add(new Line("A: " + f.answer(), false));
            }
            for (var q : s.questions()) {
                revision.add(new Line("Practice: " + q.question(), true));
                for (int k = 0; k < 4; k++) revision.add(new Line((char)('A' + k) + ") " + q.options().get(k), false));
                revision.add(new Line("Answer: " + (char)('A' + q.correctIndex()), false));
            }
        }
        result.add(pdf(directory, "AI Complete Study Guide - " + sourceTitle, full));
        result.add(pdf(directory, "AI Revision Workbook - " + sourceTitle, revision));
        result.add(pdf(directory, "AI Slide Handout - " + sourceTitle, slidesPdf));
        result.add(powerPoint(directory, "AI Lecture Slides - " + sourceTitle, sections));
        return result;
    }

    private Artifact pdf(Path directory, String title, List<Line> content) throws IOException {
        Path path = privatePath(directory, ".pdf");
        try (PDDocument pdf = new PDDocument(); PdfCanvas canvas = new PdfCanvas(pdf)) {
            canvas.line(title, true);
            for (Line row : content) canvas.line(row.text(), row.heading());
            canvas.close();
            pdf.save(path.toFile());
        } catch (Exception ex) {
            Files.deleteIfExists(path);
            throw ex instanceof IOException e ? e : new IOException("Failed to generate PDF", ex);
        }
        return new Artifact(path, shortTitle(title), "AI_GENERATED", "pdf");
    }

    private Artifact powerPoint(Path directory, String title,
                                List<GeminiStudyClient.Section> sections) throws IOException {
        Path path = privatePath(directory, ".pptx");
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            pptx.setPageSize(new Dimension(960, 540));
            slide(pptx, title, List.of("Automated draft - verify against the source"), 0);
            int n = 1;
            for (var section : sections) {
                List<String> bullets = new ArrayList<>();
                if (!section.summary().isBlank()) bullets.add(shorten(section.summary(), 230));
                for (var p : section.points()) {
                    bullets.add(p.title() + ": " + shorten(p.explanation(), 200));
                    if (bullets.size() == 5) {
                        slide(pptx, section.title(), bullets, n++);
                        bullets.clear();
                    }
                }
                if (!bullets.isEmpty()) slide(pptx, section.title(), bullets, n++);
            }
            try (OutputStream out = Files.newOutputStream(path)) { pptx.write(out); }
        } catch (Exception ex) {
            Files.deleteIfExists(path);
            throw ex instanceof IOException e ? e : new IOException("Failed to generate PPTX", ex);
        }
        return new Artifact(path, shortTitle(title), "AI_GENERATED", "pptx");
    }

    private static void slide(XMLSlideShow pptx, String heading, List<String> bullets, int number) {
        XSLFSlide slide = pptx.createSlide();
        slide.getBackground().setFillColor(new Color(242, 248, 255));
        XSLFTextBox title = slide.createTextBox();
        title.setAnchor(new Rectangle2D.Double(64, 52, 840, 100));
        title.clearText();
        XSLFTextRun tr = title.addNewTextParagraph().addNewTextRun();
        tr.setText(shorten(heading, 90));
        tr.setFontSize(34.0);
        tr.setBold(true);
        tr.setFontColor(new Color(29, 82, 145));
        XSLFTextBox list = slide.createTextBox();
        list.setAnchor(new Rectangle2D.Double(75, 160, 805, 330));
        list.clearText();
        for (String text : bullets) {
            XSLFTextParagraph para = list.addNewTextParagraph();
            para.setSpaceAfter(12.0);
            para.setBullet(true);
            XSLFTextRun run = para.addNewTextRun();
            run.setText(shorten(text, 250));
            run.setFontSize(19.0);
            run.setFontColor(new Color(35, 51, 79));
        }
        XSLFTextBox footer = slide.createTextBox();
        footer.setAnchor(new Rectangle2D.Double(65, 502, 825, 25));
        XSLFTextRun run = footer.addNewTextParagraph().addNewTextRun();
        run.setText("PrepStart | AI-generated - review before use | " + number);
        run.setFontColor(new Color(100, 112, 129));
        run.setFontSize(10.0);
    }

    private static String shortTitle(String input) {
        return input.length() > 240 ? input.substring(0, 240) : input;
    }
    private static String shorten(String s, int length) {
        return s.length() <= length ? s : s.substring(0, length - 3).strip() + "...";
    }
    private static Path privatePath(Path dir, String ext) {
        return dir.resolve(java.util.UUID.randomUUID().toString().replace("-", "") + ext);
    }

    private static class PdfCanvas implements AutoCloseable {
        private static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private static final PDRectangle SIZE = PDRectangle.A4;
        private final PDDocument pdf;
        private PDPageContentStream stream;
        private float y;
        PdfCanvas(PDDocument pdf) throws IOException { this.pdf = pdf; newPage(); }
        void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(SIZE); pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            y = SIZE.getHeight() - 56;
        }
        void line(String text, boolean heading) throws IOException {
            if (text == null || text.isBlank()) return;
            PDType1Font font = heading ? BOLD : NORMAL;
            float size = heading ? 13 : 10;
            if (heading) y -= 10;
            for (String paragraph : text.split("\\R", -1)) {
                if (paragraph.isBlank()) { y -= 7; continue; }
                StringBuilder part = new StringBuilder();
                for (String word : paragraph.split("\\s+")) {
                    String candidate = part.isEmpty() ? word : part + " " + word;
                    // Standard PDF fonts are WinAnsi only. Replace unsupported glyphs, never crash the pipeline.
                    candidate = toWinAnsi(candidate);
                    if (font.getStringWidth(candidate) / 1000 * size > SIZE.getWidth() - 108 && !part.isEmpty()) {
                        write(font, size, part.toString());
                        part.setLength(0);
                    }
                    if (!part.isEmpty()) part.append(' ');
                    part.append(word);
                }
                if (!part.isEmpty()) write(font, size, part.toString());
            }
            y -= 5;
        }
        private void write(PDType1Font font, float size, String text) throws IOException {
            if (y < 61) newPage();
            String printable = toWinAnsi(text);
            // Extremely long whitespace-free strings must be broken into smaller runs.
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < printable.length(); i++) {
                char ch = printable.charAt(i);
                String test = line.toString() + ch;
                if (!line.isEmpty() && font.getStringWidth(test) / 1000 * size > SIZE.getWidth() - 108) {
                    writeOne(font, size, line.toString()); line.setLength(0);
                }
                line.append(ch);
            }
            if (!line.isEmpty()) writeOne(font, size, line.toString());
        }
        private void writeOne(PDType1Font font, float size, String text) throws IOException {
            if (y < 61) newPage();
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(30, 47, 72);
            stream.newLineAtOffset(54, y);
            stream.showText(text);
            stream.endText();
            y -= size + 5;
        }
        private static String toWinAnsi(String value) {
            // Keep source originals untouched. This limitation applies only to generated PDF rendering.
            return value.replace('\u2014', '-').replace('\u2013', '-').replace('\u2022', '-')
                    .replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201c', '"').replace('\u201d', '"')
                    .replaceAll("[^\\x20-\\x7E]", "?");
        }
        public void close() throws IOException { if (stream != null) { stream.close(); stream = null; } }
    }
}
