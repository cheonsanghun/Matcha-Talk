package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomCreateRequestDto {

    @NotBlank(message = "방 제목은 필수입니다.")
    private String title;

    // 향후 그룹방의 최대 인원 수 등을 DTO에 추가
}