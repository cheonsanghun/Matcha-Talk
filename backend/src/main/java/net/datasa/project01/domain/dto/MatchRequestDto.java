package net.datasa.project01.domain.dto;

import lombok.Data;
import java.util.List;

/**
 * 매칭 요청 DTO.
 */
@Data
public class MatchRequestDto {
    private Long userPid;
    private String choiceGender;
    private int minAge;
    private int maxAge;
    private String regionCode;
    private List<String> interestsJson;
    private String status;
}
