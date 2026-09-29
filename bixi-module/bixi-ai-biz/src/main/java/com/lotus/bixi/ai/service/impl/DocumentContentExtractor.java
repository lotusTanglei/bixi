package com.lotus.bixi.ai.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts bounded, normalized text from the document formats exposed by the
 * AI upload endpoint. The extractor is deliberately independent from the
 * persistence layer so an invalid or hostile upload is rejected before a row
 * is written.
 */
final class DocumentContentExtractor {

    static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 512;
    private static final long MAX_EXPANDED_ZIP_BYTES = 50L * 1024 * 1024;

    private DocumentContentExtractor() {
    }

    static ExtractedContent extract(String filename, byte[] bytes) throws IOException {
        Objects.requireNonNull(filename, "filename is required");
        Objects.requireNonNull(bytes, "bytes are required");
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("AI document is too large");
        }

        String extension = extension(filename);
        return switch (extension) {
            case "txt", "md", "markdown" -> new ExtractedContent(decodeUtf8(bytes), "text/plain");
            case "pdf" -> new ExtractedContent(extractPdf(bytes), "application/pdf");
            case "docx" -> new ExtractedContent(extractDocx(bytes),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> new ExtractedContent(extractDoc(bytes), "application/msword");
            default -> throw new IllegalArgumentException("Unsupported document type: " + extension);
        };
    }

    static List<String> chunk(String text, int maxCharacters, int overlap) {
        if (maxCharacters < 1) {
            throw new IllegalArgumentException("maxCharacters must be positive");
        }
        if (overlap < 0 || overlap >= maxCharacters) {
            throw new IllegalArgumentException("overlap must be between zero and maxCharacters - 1");
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int step = maxCharacters - overlap;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + maxCharacters);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == normalized.length()) {
                break;
            }
            start += step;
        }
        return List.copyOf(chunks);
    }

    private static String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return normalize(stripper.getText(document));
        }
    }

    private static String extractDocx(byte[] bytes) throws IOException {
        validateZip(bytes);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return normalize(extractor.getText());
        }
    }

    private static String extractDoc(byte[] bytes) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return normalize(extractor.getText());
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return normalize(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Document is not valid UTF-8", e);
        }
    }

    private static void validateZip(byte[] bytes) throws IOException {
        long expanded = 0;
        int entries = 0;
        try (InputStream input = new ByteArrayInputStream(bytes);
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("Document archive has too many entries");
                }
                String name = entry.getName();
                if (name == null || name.startsWith("/") || name.contains("..")) {
                    throw new IllegalArgumentException("Document archive contains an unsafe path");
                }
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_ZIP_BYTES) {
                        throw new IllegalArgumentException("Document archive expands beyond the limit");
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String extension(String filename) {
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 && dot + 1 < normalized.length() ? normalized.substring(dot + 1) : "";
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    record ExtractedContent(String text, String mediaType) {
        ExtractedContent {
            text = normalize(text);
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Document contains no readable text");
            }
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
                throw new IllegalArgumentException("AI document is too large");
            }
        }
    }
}
