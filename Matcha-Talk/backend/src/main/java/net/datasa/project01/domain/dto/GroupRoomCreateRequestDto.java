package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record GroupRoomCreateRequestDto(
        @Size(max = 50) String name,
        @Size(max = 3) List<Long> memberUserPids
) {
}
