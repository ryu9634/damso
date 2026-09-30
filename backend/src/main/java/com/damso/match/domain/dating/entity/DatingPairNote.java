package com.damso.match.domain.dating.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 두 회원(페어)의 만남 약속 + 관리자 메모.
 *  - 손님이 앱에서 직접 제안·확정할 수도 있고(제안자 = proposedByProfileId),
 *    관리자가 대신 잡아줄 수도 있다. 두 경로가 같은 행을 공유한다.
 *  - profileAId < profileBId 로 정규화해 저장한다. 그래야 A→B 선택과 B→A 선택이
 *    같은 메모/약속을 공유한다(쌍방일 때 두 벌로 갈라지지 않게).
 *  - dateHour 는 19~23 (한 시간 단위)만 허용한다.
 *  - 손님이 제안할 때는 '날짜+시간' 후보를 최대 3개까지 담는다(preferredSlots).
 *    상대가 그중 하나를 고르면 dateDay/dateHour 로 확정된다.
 */
@Entity
@Table(
        name = "dating_pair_notes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"profileaid", "profilebid"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatingPairNote {

    /** 데이트 시간으로 고를 수 있는 시(hour) 범위. */
    public static final int MIN_HOUR = 19;
    public static final int MAX_HOUR = 23;

    /** 제안할 때 고를 수 있는 후보(날짜+시간) 개수. */
    public static final int MAX_PREFERRED_SLOTS = 3;

    /** 만날 날짜와 시각 한 벌. 제안 후보이자 확정 값의 단위다. */
    public record Slot(LocalDate day, int hour) {
        /** 저장 형태 "2026-09-05 19" */
        String store() {
            return day + " " + hour;
        }

        static Slot parse(String text) {
            String[] parts = text.trim().split(" ");
            if (parts.length != 2) return null;
            try {
                return new Slot(LocalDate.parse(parts[0]), Integer.parseInt(parts[1]));
            } catch (RuntimeException ignored) {
                return null;   // 저장이 어긋난 값은 없는 것으로 본다
            }
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileAId;

    @Column(nullable = false)
    private Long profileBId;

    /** 관리자만 보는 메모. 손님 화면에는 절대 내려보내지 않는다. */
    @Column(length = 500)
    private String memo;

    /** 데이트 날짜. 미정이면 NULL. */
    @Column
    private LocalDate dateDay;

    /** 확정된 데이트 시각(시). 19~23. 상대가 고르기 전이면 NULL. */
    @Column
    private Integer dateHour;

    /**
     * 예전 방식(날짜 하나 + 시간 후보 "19,20,21")으로 잡힌 약속. 새로 쓰지는 않고 읽기만 한다.
     */
    @Column(length = 30)
    private String preferredHours;

    /**
     * 제안한 사람이 고른 후보 (예: "2026-09-05 19,2026-09-06 20"). 최대 3개.
     * 상대가 이 중 하나를 고르면 그 날짜·시각이 dateDay/dateHour 로 확정된다.
     */
    @Column(length = 120)
    private String preferredSlots;

    /** 만날 장소. 미정이면 NULL. */
    @Column(length = 100)
    private String place;

    /** 약속을 제안한 사람의 프로필 id. 관리자가 잡아준 약속이면 NULL. */
    @Column
    private Long proposedByProfileId;

    /** 약속 상태. 기존 데이터는 NULL 이라 읽을 때 NONE 으로 본다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MeetingStatus appointmentStatus;

    @Column
    private LocalDateTime updatedAt;

    @Transient
    public MeetingStatus statusOrDefault() {
        return MeetingStatus.orDefault(appointmentStatus);
    }

    /**
     * 후보 목록. 없으면 빈 목록.
     * 예전 방식으로 잡힌 약속은 날짜 하나에 시간 후보가 붙어 있으므로 그때 형태로 만들어 준다.
     */
    @Transient
    public java.util.List<Slot> preferredSlotList() {
        java.util.List<Slot> out = new java.util.ArrayList<>();
        if (preferredSlots != null && !preferredSlots.isBlank()) {
            for (String part : preferredSlots.split(",")) {
                Slot slot = Slot.parse(part);
                if (slot != null) out.add(slot);
            }
            return out;
        }
        if (dateDay != null && preferredHours != null && !preferredHours.isBlank()) {
            for (String part : preferredHours.split(",")) {
                try {
                    out.add(new Slot(dateDay, Integer.parseInt(part.trim())));
                } catch (NumberFormatException ignored) {
                    // 저장이 어긋난 값은 없는 것으로 본다
                }
            }
        }
        return out;
    }

    /** 후보 목록을 저장 형태로. 예전 방식 값은 함께 비운다. */
    public void setPreferredSlotList(java.util.List<Slot> slots) {
        preferredHours = null;
        preferredSlots = (slots == null || slots.isEmpty()) ? null
                : slots.stream().map(Slot::store).collect(java.util.stream.Collectors.joining(","));
    }

    /** 두 id 를 정렬해 [작은 값, 큰 값] 으로 반환. */
    public static long[] normalize(Long x, Long y) {
        return x <= y ? new long[]{x, y} : new long[]{y, x};
    }
}
