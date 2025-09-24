package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FollowRequestCreateDto {

    @NotNull
    private Long roomId;
}
