package edu.campus.studyroom.resource.mapper;

import edu.campus.studyroom.resource.domain.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StudyRoomMapper {

    StudyRoom selectById(@Param("id") Long id);

    StudyRoom selectByIdForUpdate(@Param("id") Long id);

    StudyRoom selectByCode(@Param("code") String code);

    List<StudyRoom> selectPage(@Param("campus") String campus,
                               @Param("building") String building,
                               @Param("status") RoomStatus status,
                               @Param("offset") int offset,
                               @Param("size") int size);

    long countPage(@Param("campus") String campus,
                   @Param("building") String building,
                   @Param("status") RoomStatus status);

    List<StudyRoom> selectAll();

    int insert(StudyRoom room);

    /** Optimistic update of editable basics (name/campus/building/floor/hours/rows/cols). */
    int updateBasics(@Param("id") Long id,
                     @Param("name") String name,
                     @Param("campus") String campus,
                     @Param("building") String building,
                     @Param("floorNo") Integer floorNo,
                     @Param("openTime") java.time.LocalTime openTime,
                     @Param("closeTime") java.time.LocalTime closeTime,
                     @Param("rowsCount") Integer rowsCount,
                     @Param("colsCount") Integer colsCount,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("now") LocalDateTime now);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("status") RoomStatus status,
                     @Param("now") LocalDateTime now);

    int updateCheckinCode(@Param("id") Long id,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("checkinCodeHash") String checkinCodeHash,
                          @Param("now") LocalDateTime now);

    /** Rooms whose seats are queryable for availability (used by room list). */
    List<StudyRoom> selectByIds(@Param("ids") List<Long> ids);
}
