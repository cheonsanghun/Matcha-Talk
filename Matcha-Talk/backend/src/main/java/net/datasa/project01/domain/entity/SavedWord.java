package net.datasa.project01.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "saved_words")
public class SavedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "word_id")
    private Long wordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_pid", nullable = false)
    private User user;

    @Column(name = "source_text", length = 500, nullable = false)
    private String sourceText;

    @Column(name = "translated_text", length = 500, nullable = false)
    private String translatedText;

    @Column(name = "source_lang", length = 2, nullable = false)
    private String sourceLang;

    @Column(name = "target_lang", length = 2, nullable = false)
    private String targetLang;

    @Column(length = 500)
    private String context;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
