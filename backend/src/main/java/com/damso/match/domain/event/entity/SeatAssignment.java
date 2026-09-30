package com.damso.match.domain.event.entity;

import com.damso.match.global.common.Gender;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "seat_assignments",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"eventId", "round", "customerId"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    /** 1, 2, 3 (몇 차 자리인지) */
    @Column(name = "\"round\"", nullable = false)
    private Integer round;

    /** 1~9 좌석 번호 */
    @Column(nullable = false)
    private Integer seatNumber;

    /** 같은 자리 내 슬롯 (0 또는 1). 한 자리당 셀 2개. */
    @Column(nullable = false)
    private Integer slotIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private Long customerId;
}
