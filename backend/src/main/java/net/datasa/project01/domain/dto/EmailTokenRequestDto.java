package net.datasa.project01.domain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailTokenRequestDto {
    private String email;
    private String token;
}
