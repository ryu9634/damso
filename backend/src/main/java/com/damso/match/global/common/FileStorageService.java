package com.damso.match.global.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 업로드 이미지 저장 (소개팅 사진 · 술 메뉴 사진 공용).
 *  - 서버 디스크(damso.uploads)에 저장하고 /uploads/** 로 정적 제공한다.
 *  - 파일명은 UUID 로 새로 만든다. 원본 이름은 쓰지 않는다.(경로 조작 방지)
 */
@Service
public class FileStorageService {

    /** 업로드 허용 최대 크기. application.yml 의 max-file-size 와 맞춘다. */
    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    @Value("${damso.uploads:./uploads}")
    private String uploadDir;

    /** 이미지 한 장을 저장하고 앱에서 쓸 수 있는 경로(/uploads/xxx.jpg)를 돌려준다. */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("사진 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("사진은 8MB 이하만 업로드할 수 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "")
                    + extensionOf(file.getOriginalFilename(), contentType);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
            return "/uploads/" + filename;
        } catch (IOException e) {
            throw new IllegalStateException("사진 저장에 실패했습니다.");
        }
    }

    private static String extensionOf(String originalName, String contentType) {
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0 && dot < originalName.length() - 1) {
                String ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
                if (ext.matches("\\.(jpg|jpeg|png|gif|webp|heic)")) return ext;
            }
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
