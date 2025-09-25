package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.UserInquiry;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserInquiryCreateDto {

    private String category;
    private String title;
    private String content;

    public UserInquiry toEntity(User user) {
        return UserInquiry.builder()
                .user(user)
                .category(this.category)
                .title(this.title)
                .content(this.content)
                .build(); // Status defaults to OPEN in the entity
    }
}
