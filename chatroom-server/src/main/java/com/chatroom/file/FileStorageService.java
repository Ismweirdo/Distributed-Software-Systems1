package com.chatroom.file;

import com.chatroom.config.GatewayRoutingProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp");

    @Value("${app.upload-dir:./data/uploads}")
    private String uploadDir;

    private final FileStorageProperties properties;
    private final GatewayRoutingProperties gatewayRoutingProperties;

    public StoredFile store(MultipartFile file) throws IOException {
        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "file";
        String extension = extractExtension(originalName);
        boolean isImage = isImageFile(file.getContentType(), extension);
        String bucket = isImage ? properties.getImageBucket() : properties.getAttachmentBucket();
        String storedName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + extension;

        Path bucketDir = Paths.get(uploadDir).resolve(bucket).normalize();
        Files.createDirectories(bucketDir);

        Path destination = bucketDir.resolve(storedName).normalize();
        ensureWithinBase(bucketDir, destination);
        file.transferTo(destination);

        String contentType = probeContentType(destination, file.getContentType(), isImage);
        String resourceKey = bucket + "/" + storedName;
        return StoredFile.builder()
                .bucket(bucket)
                .storedName(storedName)
                .originalName(originalName)
                .size(file.getSize())
                .image(isImage)
                .contentType(contentType)
                .resourceKey(resourceKey)
                .publicUrl(buildPublicUrl(bucket, storedName))
                .metadataUrl(buildMetadataUrl(bucket, storedName))
                .build();
    }

    public StoredFileView loadPublicFile(String bucket, String storedName) throws IOException {
        Path file = resolveBucketFile(bucket, storedName);
        String contentType = probeContentType(file, null, properties.getImageBucket().equals(bucket));
        return new StoredFileView(file, contentType, properties.getImageBucket().equals(bucket));
    }

    public StoredFileView loadLegacyFile(String storedName) throws IOException {
        Path baseDir = Paths.get(uploadDir).normalize();
        Path file = baseDir.resolve(storedName).normalize();
        ensureWithinBase(baseDir, file);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IOException("文件不存在");
        }
        String contentType = probeContentType(file, null, isImageFile(null, extractExtension(storedName)));
        return new StoredFileView(file, contentType, isImageFile(null, extractExtension(storedName)));
    }

    public StoredFileMetadata getMetadata(String bucket, String storedName) throws IOException {
        Path file = resolveBucketFile(bucket, storedName);
        boolean isImage = properties.getImageBucket().equals(bucket);
        String contentType = probeContentType(file, null, isImage);
        return StoredFileMetadata.builder()
                .bucket(bucket)
                .storedName(storedName)
                .resourceKey(bucket + "/" + storedName)
                .publicUrl(buildPublicUrl(bucket, storedName))
                .size(Files.size(file))
                .image(isImage)
                .contentType(contentType)
                .build();
    }

    private Path resolveBucketFile(String bucket, String storedName) throws IOException {
        validateBucket(bucket);
        Path bucketDir = Paths.get(uploadDir).resolve(bucket).normalize();
        Path file = bucketDir.resolve(storedName).normalize();
        ensureWithinBase(bucketDir, file);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IOException("文件不存在");
        }
        return file;
    }

    private void validateBucket(String bucket) {
        if (!properties.getImageBucket().equals(bucket) && !properties.getAttachmentBucket().equals(bucket)) {
            throw new IllegalArgumentException("不支持的文件资源桶");
        }
    }

    private void ensureWithinBase(Path base, Path target) {
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法文件路径");
        }
    }

    private String extractExtension(String originalName) {
        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private boolean isImageFile(String contentType, String extension) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    private String probeContentType(Path file, String fallbackContentType, boolean image) throws IOException {
        String contentType = Files.probeContentType(file);
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        if (StringUtils.hasText(fallbackContentType)) {
            return fallbackContentType;
        }
        return image ? "image/png" : "application/octet-stream";
    }

    private String buildPublicUrl(String bucket, String storedName) {
        String relativePath = trimTrailingSlash(gatewayRoutingProperties.getPublicFilesPrefix()) + "/" + bucket + "/" + storedName;
        String baseUrl = trimTrailingSlash(gatewayRoutingProperties.getExternalBaseUrl());
        return StringUtils.hasText(baseUrl) ? baseUrl + relativePath : relativePath;
    }

    private String buildMetadataUrl(String bucket, String storedName) {
        return trimTrailingSlash(gatewayRoutingProperties.getApiPrefix()) + "/files/resources/" + bucket + "/" + storedName;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Getter
    public static class StoredFileView {
        private final Path path;
        private final String contentType;
        private final boolean image;

        public StoredFileView(Path path, String contentType, boolean image) {
            this.path = path;
            this.contentType = contentType;
            this.image = image;
        }
    }

    @Getter
    @Builder
    public static class StoredFile {
        private final String bucket;
        private final String storedName;
        private final String originalName;
        private final long size;
        private final boolean image;
        private final String contentType;
        private final String resourceKey;
        private final String publicUrl;
        private final String metadataUrl;
    }

    @Getter
    @Builder
    public static class StoredFileMetadata {
        private final String bucket;
        private final String storedName;
        private final String resourceKey;
        private final String publicUrl;
        private final long size;
        private final boolean image;
        private final String contentType;
    }
}
