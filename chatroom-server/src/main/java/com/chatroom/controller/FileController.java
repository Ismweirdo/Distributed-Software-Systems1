package com.chatroom.controller;

import com.chatroom.common.Result;
import com.chatroom.file.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            FileStorageService.StoredFile storedFile = fileStorageService.store(file);

            return Result.ok(Map.of(
                "filename", storedFile.getStoredName(),
                "originalName", storedFile.getOriginalName(),
                "url", storedFile.getPublicUrl(),
                "isImage", storedFile.isImage(),
                "size", storedFile.getSize(),
                "resourceKey", storedFile.getResourceKey(),
                "bucket", storedFile.getBucket(),
                "metadataUrl", storedFile.getMetadataUrl(),
                "contentType", storedFile.getContentType()
            ));
        } catch (IOException e) {
            log.error("File upload failed", e);
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/resources/{bucket}/{filename}")
    public Result<Map<String, Object>> metadata(@PathVariable String bucket,
                                                @PathVariable String filename) throws IOException {
        FileStorageService.StoredFileMetadata metadata = fileStorageService.getMetadata(bucket, filename);
        return Result.ok(Map.of(
                "bucket", metadata.getBucket(),
                "filename", metadata.getStoredName(),
                "resourceKey", metadata.getResourceKey(),
                "url", metadata.getPublicUrl(),
                "size", metadata.getSize(),
                "isImage", metadata.isImage(),
                "contentType", metadata.getContentType()
        ));
    }

    @GetMapping("/public/{bucket}/{filename}")
    public ResponseEntity<Resource> downloadPublic(@PathVariable String bucket,
                                                   @PathVariable String filename) throws IOException {
        FileStorageService.StoredFileView storedFile = fileStorageService.loadPublicFile(bucket, filename);
        return buildFileResponse(storedFile, filename);
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> downloadLegacy(@PathVariable String filename) throws IOException {
        FileStorageService.StoredFileView storedFile = fileStorageService.loadLegacyFile(filename);
        return buildFileResponse(storedFile, filename);
    }

    private ResponseEntity<Resource> buildFileResponse(FileStorageService.StoredFileView storedFile,
                                                       String downloadName) {
        Resource resource = new FileSystemResource(storedFile.getPath());
        MediaType mediaType = MediaType.parseMediaType(storedFile.getContentType());
        ContentDisposition disposition = storedFile.isImage()
                ? ContentDisposition.inline().filename(downloadName).build()
                : ContentDisposition.attachment().filename(downloadName).build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
