package com.ruoyi.system.service;

import com.ruoyi.system.domain.ArchiveFile;
import com.ruoyi.system.mapper.ArchiveFileTextMapper;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.ScratchFile;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ArchiveAttachmentIndexer {
    private static final Logger log = LoggerFactory.getLogger(ArchiveAttachmentIndexer.class);
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_WORD_BYTES = 32L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 1_000_000;
    private final ArchiveFileTextMapper index;
    private final Path fileRoot;

    public ArchiveAttachmentIndexer(ArchiveFileTextMapper index, @Value("${archive.file-root}") String fileRoot) {
        this.index = index;
        this.fileRoot = Path.of(fileRoot).toAbsolutePath().normalize();
    }

    public boolean isAvailable() {
        return index.indexExists();
    }

    @Scheduled(fixedDelayString = "${archive.search.index-delay-ms:15000}", initialDelay = 1000)
    public void indexPendingFiles() {
        if (!isAvailable()) return;
        for (ArchiveFile file : index.pendingFiles()) {
            String content = "";
            boolean retry = false;
            try {
                content = readText(file);
            } catch (NoSuchFileException e) {
                retry = true;
                log.warn("附件正文暂未入索引，找不到附件 {}", file.getFileId());
            } catch (IOException | RuntimeException e) {
                log.warn("附件正文读取失败，附件 {} ({})", file.getFileId(), e.getClass().getSimpleName());
            }
            index.saveText(file, content, retry);
        }
    }

    private String readText(ArchiveFile file) throws IOException {
        if (file.getFileSize() > MAX_FILE_BYTES) return "";
        Path root = fileRoot.toRealPath();
        Path relative = root.resolve(file.getStoreKey()).normalize();
        if (!relative.startsWith(root)) return "";
        Path path = relative.toRealPath();
        if (!path.startsWith(root) || !Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES) return "";

        String text = switch (file.getFileType()) {
            case "pdf" -> readPdf(path);
            case "docx" -> readDocx(path);
            case "doc" -> {
                try (var input = Files.newInputStream(path); var extractor = new WordExtractor(input)) {
                    yield extractor.getText();
                }
            }
            default -> "";
        };
        int end = Math.min(text.length(), MAX_TEXT_LENGTH);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end).replaceAll("(?U)\\s+", " ").strip();
    }

    private String readPdf(Path path) throws IOException {
        try (var document = Loader.loadPDF(path.toFile(),
                () -> new ScratchFile(MemoryUsageSetting.setupTempFileOnly(128L * 1024 * 1024)))) {
            if (document.isEncrypted()) return "";
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(2000);
            StringBuilder text = new StringBuilder();
            try {
                stripper.writeText(document, new Writer() {
                    @Override
                    public void write(char[] chars, int offset, int length) throws IOException {
                        int remaining = MAX_TEXT_LENGTH - text.length();
                        text.append(chars, offset, Math.min(remaining, length));
                        if (length > remaining) throw new TextLimitReached();
                    }

                    @Override public void flush() {}
                    @Override public void close() {}
                });
            } catch (TextLimitReached ignored) {
                // The buffer already contains the searchable prefix.
            }
            return text.toString();
        }
    }

    private String readDocx(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            long expanded = 0;
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                long size = entries.nextElement().getSize();
                if (size < 0 || size > MAX_WORD_BYTES - expanded) return "";
                expanded += size;
            }
            if (zip.size() > 1024) return "";
        }
        try (var input = Files.newInputStream(path);
             var document = new XWPFDocument(input);
             var extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static final class TextLimitReached extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
