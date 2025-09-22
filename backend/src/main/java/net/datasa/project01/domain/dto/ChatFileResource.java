package net.datasa.project01.domain.dto;

import org.springframework.core.io.Resource;

/**
 * 채팅 파일 다운로드 시 응답에 필요한 리소스와 메타데이터를 묶어 반환하기 위한 DTO.
 */
public record ChatFileResource(Resource resource, String fileName, String mimeType) {
}
