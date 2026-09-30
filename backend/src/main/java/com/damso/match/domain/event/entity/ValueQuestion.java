package com.damso.match.domain.event.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "value_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValueQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(nullable = false, length = 100)
    private String optionA;

    @Column(nullable = false, length = 100)
    private String optionB;
}
