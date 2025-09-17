package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpdateDto {
    // 'ACCEPTED' 또는 'REJECTED'
    private String status;
}
