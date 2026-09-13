package com.pradeep.finance.importing;

import java.io.IOException;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class StatementTextExtractor {
    public String extract(MultipartFile statement) throws IOException {
        String filename = statement.getOriginalFilename() == null ? "" : statement.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".csv")) {
            return new String(statement.getBytes());
        }
        if (filename.endsWith(".pdf")) {
            try (var document = Loader.loadPDF(statement.getBytes())) {
                return new PDFTextStripper().getText(document);
            }
        }
        throw new IllegalArgumentException("Only CSV and text-based PDF statements are supported.");
    }
}
