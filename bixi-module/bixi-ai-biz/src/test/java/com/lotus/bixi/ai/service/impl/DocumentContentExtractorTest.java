package com.lotus.bixi.ai.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentContentExtractorTest {

    @Test
    void extractsPdfTextInsteadOfTreatingPdfBytesAsUtf8() throws Exception {
        byte[] pdf = pdf("refund policy is thirty days");

        DocumentContentExtractor.ExtractedContent extracted =
                DocumentContentExtractor.extract("policy.pdf", pdf);

        assertThat(extracted.mediaType()).isEqualTo("application/pdf");
        assertThat(extracted.text()).contains("refund policy is thirty days");
    }

    @Test
    void extractsDocxParagraphsAndNormalizesWhitespace() throws Exception {
        byte[] docx = docx("first paragraph", "second paragraph");

        DocumentContentExtractor.ExtractedContent extracted =
                DocumentContentExtractor.extract("guide.docx", docx);

        assertThat(extracted.mediaType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(extracted.text()).isEqualTo("first paragraph\nsecond paragraph");
    }

    @Test
    void chunksContentWithStableOverlapAndNoEmptyChunks() {
        List<String> chunks = DocumentContentExtractor.chunk(
                "0123456789ABCDEFGHIJ", 10, 2);

        assertThat(chunks).containsExactly("0123456789", "89ABCDEFGH", "GHIJ");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isBetween(1, 10));
    }

    @Test
    void rejectsUnsupportedAndOversizedDocumentsBeforeParsing() {
        assertThatThrownBy(() -> DocumentContentExtractor.extract("malware.exe", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported document type");

        byte[] oversized = new byte[DocumentContentExtractor.MAX_DOCUMENT_BYTES + 1];
        assertThatThrownBy(() -> DocumentContentExtractor.extract("large.txt", oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document is too large");
    }

    @Test
    void rejectsExpandedTextThatExceedsTheBound() {
        String expanded = "中".repeat(DocumentContentExtractor.MAX_DOCUMENT_BYTES / 3 + 1);

        assertThatThrownBy(() -> new DocumentContentExtractor.ExtractedContent(expanded, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI document is too large");
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] docx(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(output);
            return output.toByteArray();
        }
    }
}
