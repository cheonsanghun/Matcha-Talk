package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatMessagePageResponseDto {

    private final List<ChatMessageResponseDto> messages;
    private final boolean hasMore;
}
