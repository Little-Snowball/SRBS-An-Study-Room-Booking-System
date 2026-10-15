package edu.campus.studyroom.resource.mapper;

import edu.campus.studyroom.resource.domain.Seat;
import edu.campus.studyroom.resource.domain.SeatStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SeatMapper {

    Seat selectById(@Param("id") Long id);

    Seat selectByIdForUpdate(@Param("id") Long id);

    List<Seat> selectByRoomId(@Param("roomId") Long roomId);

    Seat selectByRoomAndCode(@Param("roomId") Long roomId, @Param("code") String code);

    Seat selectByRoomRowCol(@Param("roomId") Long roomId,
                            @Param("rowNo") int rowNo,
                            @Param("colNo") int colNo);

    long countByRoomId(@Param("roomId") Long roomId);

    /** Count of a room's seats in one status (NORMAL/MAINTENANCE). */
    long countByRoomIdAndStatus(@Param("roomId") Long roomId, @Param("status") SeatStatus status);

    /** Total seat count across all rooms; the global cap is 1000. */
    long countAll();

    /** Max row_no among the room's seats (any status); null when the room has no seat. */
    Integer selectMaxRowNo(@Param("roomId") Long roomId);

    /** Max col_no among the room's seats (any status); null when the room has no seat. */
    Integer selectMaxColNo(@Param("roomId") Long roomId);

    int insert(Seat seat);

    int insertBatch(@Param("seats") List<Seat> seats);

    int updateLabels(@Param("id") Long id,
                     @Param("hasPower") Boolean hasPower,
                     @Param("nearWindow") Boolean nearWindow,
                     @Param("quietZone") Boolean quietZone,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("now") LocalDateTime now);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("status") SeatStatus status,
                     @Param("now") LocalDateTime now);

    List<Seat> selectByIds(@Param("ids") List<Long> ids);

    /**
     * Seats of a room matching the tag filters that have no effective reservation
     * overlapping [startAt, endAt). Used for the room list match counts.
     */
    List<Seat> selectMatchingFree(@Param("roomId") Long roomId,
                                  @Param("startAt") LocalDateTime startAt,
                                  @Param("endAt") LocalDateTime endAt,
                                  @Param("hasPower") Boolean hasPower,
                                  @Param("nearWindow") Boolean nearWindow,
                                  @Param("quietZone") Boolean quietZone);
}
