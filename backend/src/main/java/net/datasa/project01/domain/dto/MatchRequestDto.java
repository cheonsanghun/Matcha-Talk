package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import java.util.List;

/**
 * 매칭 요청 DTO.
 */
@Data
public class MatchRequestDto {
    @JsonProperty("choice_gender")
    private String choiceGender;

    @JsonProperty("min_age")
    private int minAge;

    @JsonProperty("max_age")
    private int maxAge;

    @JsonProperty("region_code")
    private String regionCode;

    @JsonProperty("interests_json")
    private List<String> interestsJson;

}
