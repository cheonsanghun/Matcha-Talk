package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotNull;

public record DirectChatRequestDto(@NotNull(message = "대상 사용자를 선택해주세요.") Long targetUserPid) {
}
