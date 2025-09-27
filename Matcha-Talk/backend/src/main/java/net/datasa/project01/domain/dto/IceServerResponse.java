package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IceServerResponse {
    private final String urls;
    private final String username;
    private final String credential;
}
