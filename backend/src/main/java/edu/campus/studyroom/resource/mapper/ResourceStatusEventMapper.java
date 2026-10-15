package edu.campus.studyroom.resource.mapper;

import edu.campus.studyroom.resource.domain.ResourceStatusEvent;
import edu.campus.studyroom.resource.domain.RetainedService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ResourceStatusEventMapper {

    int insert(ResourceStatusEvent event);

    List<ResourceStatusEvent> selectByRoomId(@Param("roomId") Long roomId);

    List<ResourceStatusEvent> selectBySeatId(@Param("seatId") Long seatId);

    /** Latest event at or before the given instant (snapshot reconstruction). */
    ResourceStatusEvent selectLatestForRoomAt(@Param("roomId") Long roomId,
                                              @Param("at") LocalDateTime at);

    ResourceStatusEvent selectLatestForSeatAt(@Param("seatId") Long seatId,
                                              @Param("at") LocalDateTime at);

    List<ResourceStatusEvent> selectRoomEventsBetween(@Param("roomId") Long roomId,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to);
}
