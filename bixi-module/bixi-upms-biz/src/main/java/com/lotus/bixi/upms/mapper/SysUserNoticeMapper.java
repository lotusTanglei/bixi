package com.lotus.bixi.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.UserNoticeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户消息关联表 Mapper 接口
 *
 * @author bixi
 * @date 2025-01-01
 */
@Mapper
public interface SysUserNoticeMapper extends BaseMapper<SysUserNotice> {

	/**
	 * Finds unread, published notices that should be replayed to a reconnecting
	 * SSE client.  The current tenant context is deliberately left to the
	 * MyBatis tenant interceptor; the user id is still bound explicitly so a
	 * reconnect cannot ask for another user's backlog.
	 */
	@Select("""
			SELECT un.id, un.notice_id, un.user_id
			  FROM sys_user_notice un
			  JOIN sys_notice n ON n.id = un.notice_id
			 WHERE un.user_id = #{userId}
			   AND un.is_read = '0'
			   AND un.del_flag = '0'
			   AND n.status = '1'
			   AND n.del_flag = '0'
			 ORDER BY un.create_time ASC
			 LIMIT #{limit}
			""")
	List<SysUserNotice> selectUnreadPublishedForSse(@Param("userId") Long userId,
			@Param("limit") int limit);

    /**
     * 分页查询用户通知
     * @param page 分页对象
     * @param userNotice 查询条件
     * @return
     */
    IPage<UserNoticeVO> selectUserNoticePage(Page page, @Param("query") UserNoticeVO userNotice,
            @Param("publishedOnly") boolean publishedOnly);

    /**
     * 通过ID查询用户通知
     * @param id ID
     * @return
     */
    UserNoticeVO selectUserNoticeById(Long id);

    /**
     * Claim one recipient for an SSE attempt. The attempt count is part of the
     * compare-and-set fence; an old worker cannot complete a reclaimed attempt.
     */
    @Update("""
            UPDATE sys_user_notice
               SET delivery_status = 'IN_FLIGHT',
                   delivery_attempts = COALESCE(delivery_attempts, 0) + 1,
                   delivery_last_error = NULL,
                   delivery_last_attempt_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND del_flag = '0'
               AND COALESCE(delivery_attempts, 0) = #{expectedAttempts}
               AND (delivery_status IS NULL OR delivery_status IN ('PENDING', 'FAILED')
                    OR (delivery_status = 'IN_FLIGHT'
                        AND (delivery_last_attempt_at IS NULL OR delivery_last_attempt_at < #{staleBefore})))
            """)
    int claimDelivery(@Param("id") Long id, @Param("staleBefore") LocalDateTime staleBefore,
            @Param("expectedAttempts") int expectedAttempts);

    /** Mark a claimed recipient as delivered only if its attempt is still current. */
    @Update("""
            UPDATE sys_user_notice
               SET delivery_status = 'DELIVERED',
                   delivery_last_error = NULL,
                   delivery_delivered_at = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND del_flag = '0'
               AND delivery_status = 'IN_FLIGHT'
               AND delivery_attempts = #{expectedAttempts}
            """)
    int markDeliveryDelivered(@Param("id") Long id, @Param("expectedAttempts") int expectedAttempts);

    /** Persist a bounded failure for the current attempt if its fence is still current. */
    @Update("""
            UPDATE sys_user_notice
               SET delivery_status = 'FAILED',
                   delivery_last_error = #{error}
             WHERE id = #{id}
               AND del_flag = '0'
               AND delivery_status = 'IN_FLIGHT'
               AND delivery_attempts = #{expectedAttempts}
            """)
    int markDeliveryFailed(@Param("id") Long id, @Param("expectedAttempts") int expectedAttempts,
            @Param("error") String error);

    /** Make failed or stale in-flight recipients eligible for the next explicit retry. */
    @Update("""
            UPDATE sys_user_notice
               SET delivery_status = 'PENDING',
                   delivery_last_error = NULL,
                   delivery_delivered_at = NULL
             WHERE notice_id = #{noticeId}
               AND del_flag = '0'
               AND (delivery_status = 'FAILED'
                    OR (delivery_status = 'IN_FLIGHT'
                        AND (delivery_last_attempt_at IS NULL OR delivery_last_attempt_at < #{staleBefore})))
            """)
    int resetFailedDeliveries(@Param("noticeId") Long noticeId,
            @Param("staleBefore") LocalDateTime staleBefore);

    /** Resolve a provider callback when the sender only retained notice/user IDs. */
    @Select("""
            SELECT * FROM sys_user_notice
             WHERE notice_id = #{noticeId}
               AND user_id = #{userId}
               AND del_flag = '0'
            LIMIT 1
            """)
    SysUserNotice selectDeliveryByNoticeAndUser(@Param("noticeId") Long noticeId,
            @Param("userId") Long userId);

    /**
     * Apply one provider receipt. Terminal DELIVERED receipts fence off late
     * failures; repeated callbacks return zero and are treated as idempotent.
     */
    @Update("""
            UPDATE sys_user_notice
               SET delivery_receipt_status = #{receiptStatus},
                   delivery_receipt_code = #{receiptCode},
                   delivery_receipt_at = CURRENT_TIMESTAMP,
                   delivery_status = CASE
                       WHEN #{receiptStatus} = 'FAILED' THEN 'FAILED'
                       WHEN #{receiptStatus} = 'DELIVERED' THEN 'DELIVERED'
                       ELSE delivery_status END,
                   delivery_last_error = CASE
                       WHEN #{receiptStatus} = 'FAILED' THEN COALESCE(#{receiptCode}, 'provider receipt FAILED')
                       WHEN #{receiptStatus} = 'DELIVERED' THEN NULL
                       ELSE delivery_last_error END
             WHERE id = #{id}
               AND notice_id = #{noticeId}
               AND del_flag = '0'
               AND (delivery_receipt_status IS NULL
                    OR delivery_receipt_status = 'PENDING'
                    OR (delivery_receipt_status = 'FAILED'
                        AND #{receiptStatus} IN ('FAILED', 'DELIVERED'))
                    OR (delivery_receipt_status = 'DELIVERED'
                        AND #{receiptStatus} = 'DELIVERED'))
            """)
    int applyDeliveryReceipt(@Param("id") Long id, @Param("noticeId") Long noticeId,
            @Param("receiptStatus") String receiptStatus, @Param("receiptCode") String receiptCode);

}
