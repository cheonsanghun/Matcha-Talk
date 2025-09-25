// backend/src/main/java/net/datasa/project01/domain/dto/PasswordResetRequest.java
package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PasswordResetRequest {
    @NotBlank @Email
    private String email;
}
