package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.domain.entity.Rating;
import com.ligong.reportingcenter.dto.RatingDto;
import com.ligong.reportingcenter.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;

    @Transactional(readOnly = true)
    public List<RatingDto> listAll() {
        try {
            List<Rating> ratings = ratingRepository.findAllWithDetails();
            log.info("查询到 {} 条评价记录", ratings.size());

            List<RatingDto> result = ratings.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            log.info("成功转换 {} 条评价DTO", result.size());
            return result;
        } catch (Exception e) {
            log.error("查询评价列表失败", e);
            throw e;
        }
    }

    @Transactional
    public void delete(Long ratingId) {
        log.info("删除评价 ID: {}", ratingId);
        ratingRepository.deleteById(ratingId);
    }

    private RatingDto toDto(Rating rating) {
        try {
            // 获取学生姓名
            String studentName = null;
            if (rating.getStudent() != null) {
                studentName = rating.getStudent().getNickname() != null ?
                    rating.getStudent().getNickname() : rating.getStudent().getUserId();
            }

            // 获取维修人员姓名
            String staffName = null;
            if (rating.getStaff() != null) {
                staffName = rating.getStaff().getNickname() != null ?
                    rating.getStaff().getNickname() : rating.getStaff().getUserId();
            }

            // 获取报修单ID
            Long repairOrderId = null;
            if (rating.getTicket() != null) {
                repairOrderId = rating.getTicket().getTicketId();
            }

            RatingDto dto = new RatingDto(
                    rating.getRatingId(),
                    rating.getScore(),
                    rating.getComment(),
                    rating.getStudent() != null ? rating.getStudent().getUserId() : null,
                    studentName,  // 新增：学生姓名
                    rating.getStaff() != null ? rating.getStaff().getUserId() : null,
                    staffName,    // 新增：维修人员姓名
                    repairOrderId, // 新增：报修单ID
                    rating.getSpeedRating(),
                    rating.getQualityRating(),
                    rating.getAttitudeRating(),
                    rating.getResolved(),
                    rating.getAnonymous(),
                    rating.getRatedAt()
            );

            log.debug("转换评价 DTO: ratingId={}, score={}, studentId={}, studentName={}, staffId={}, staffName={}, repairOrderId={}",
                    dto.ratingId(), dto.score(), dto.studentId(), dto.studentName(), dto.staffId(), dto.staffName(), dto.repairOrderId());
            return dto;
        } catch (Exception e) {
            log.error("转换评价 DTO 失败: ratingId={}", rating.getRatingId(), e);
            throw e;
        }
    }
}
