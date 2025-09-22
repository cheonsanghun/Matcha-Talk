package net.datasa.project01.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path storageLocation;

    public FileStorageService(@Value("${chat.file-storage-path:uploads}") String storagePath) {
        this.storageLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 디렉터리를 생성할 수 없습니다.", e);
        }
    }

    public StoredFile store(MultipartFile multipartFile, String subDirectory) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 존재하지 않습니다.");
        }

        String originalFileName = StringUtils.cleanPath(
                multipartFile.getOriginalFilename() != null ? multipartFile.getOriginalFilename() : "uploaded-file"
        );

        String extension = "";
        int lastDot = originalFileName.lastIndexOf('.');
        if (lastDot != -1) {
            extension = originalFileName.substring(lastDot);
        }

        String storedFileName = UUID.randomUUID().toString().replaceAll("-", "") + extension;
        Path targetDirectory = this.storageLocation;
        if (subDirectory != null && !subDirectory.isBlank()) {
            targetDirectory = this.storageLocation.resolve(subDirectory).normalize();
            try {
                Files.createDirectories(targetDirectory);
            } catch (IOException e) {
                throw new IllegalStateException("하위 디렉터리를 생성할 수 없습니다.", e);
            }
        }

        Path targetLocation = targetDirectory.resolve(storedFileName).normalize();
        if (!targetLocation.startsWith(this.storageLocation)) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        }

        try {
            Files.copy(multipartFile.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("파일을 저장할 수 없습니다.", e);
        }

        Path relativePath = this.storageLocation.relativize(targetLocation);
        String normalizedRelativePath = relativePath.toString().replace('\\', '/');

        return new StoredFile(
                originalFileName,
                storedFileName,
                normalizedRelativePath,
                multipartFile.getContentType(),
                multipartFile.getSize()
        );
    }

    public Resource loadAsResource(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("파일 경로가 비어 있습니다.");
        }
        Path filePath = this.storageLocation.resolve(relativePath).normalize();
        if (!filePath.startsWith(this.storageLocation)) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new IllegalArgumentException("파일을 읽을 수 없습니다.");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다.", e);
        }
    }

    @Getter
    public static class StoredFile {
        private final String originalFileName;
        private final String storedFileName;
        private final String relativePath;
        private final String contentType;
        private final long size;

        public StoredFile(String originalFileName, String storedFileName, String relativePath, String contentType, long size) {
            this.originalFileName = originalFileName;
            this.storedFileName = storedFileName;
            this.relativePath = relativePath;
            this.contentType = contentType;
            this.size = size;
        }
    }
}
