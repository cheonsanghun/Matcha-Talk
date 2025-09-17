package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserReportUpdateDto {
    // "REVIEWING", "ACTIONED", "DISMISSED" 등
    private String status;
}
