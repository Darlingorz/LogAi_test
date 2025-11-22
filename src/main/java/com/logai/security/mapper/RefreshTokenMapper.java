package com.logai.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.security.entity.RefreshToken;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    /**
     * 根据Token哈希查找有效的Refresh Token
     */
    @Select("""
            SELECT * FROM refresh_tokens 
            WHERE token_hash = #{tokenHash}
              AND is_revoked = false
              AND expires_at > #{now}
            """)
    RefreshToken findValidByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now
    );

    /**
     * 根据Token哈希和客户端ID查找有效的Refresh Token
     */
    @Select("""
            SELECT * FROM refresh_tokens 
            WHERE token_hash = #{tokenHash}
              AND client_id = #{clientId}
              AND is_revoked = false
              AND expires_at > #{now}
            """)
    RefreshToken findValidByTokenHashAndClientId(
            @Param("tokenHash") String tokenHash,
            @Param("clientId") String clientId,
            @Param("now") LocalDateTime now
    );

    /**
     * 根据用户ID查找所有有效的Refresh Token
     */
    @Select("""
            SELECT * FROM refresh_tokens 
            WHERE user_id = #{userId}
              AND is_revoked = false
              AND expires_at > #{now}
            """)
    List<RefreshToken> findValidByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    /**
     * 根据用户ID和设备ID查找有效Token
     */
    @Select("""
            SELECT * FROM refresh_tokens 
            WHERE user_id = #{userId}
              AND device_id = #{deviceId}
              AND is_revoked = false
              AND expires_at > #{now}
            """)
    RefreshToken findValidByUserIdAndDeviceId(
            @Param("userId") Long userId,
            @Param("deviceId") String deviceId,
            @Param("now") LocalDateTime now
    );

    /**
     * 废除用户所有 Refresh Token
     */
    @Update("""
            UPDATE refresh_tokens
            SET is_revoked = true,
                revoked_at = #{revokedAt},
                revoke_reason = #{reason}
            WHERE user_id = #{userId}
              AND is_revoked = false
            """)
    Integer revokeAllByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("reason") String reason
    );

    /**
     * 废除过期 Token
     */
    @Update("""
            UPDATE refresh_tokens
            SET is_revoked = true,
                revoked_at = #{revokedAt},
                revoke_reason = 'Expired'
            WHERE expires_at < #{expiredTime}
              AND is_revoked = false
            """)
    Integer revokeExpiredTokens(
            @Param("expiredTime") LocalDateTime expiredTime,
            @Param("revokedAt") LocalDateTime revokedAt
    );

    /**
     * 统计用户有效 Token
     */
    @Select("""
            SELECT COUNT(*) FROM refresh_tokens
            WHERE user_id = #{userId}
              AND is_revoked = false
              AND expires_at > #{now}
            """)
    Long countValidByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    /**
     * 查找即将过期的 Token
     */
    @Select("""
            SELECT * FROM refresh_tokens
            WHERE expires_at BETWEEN #{now} AND #{soon}
              AND is_revoked = false
            """)
    List<RefreshToken> findExpiringSoon(
            @Param("now") LocalDateTime now,
            @Param("soon") LocalDateTime soon
    );

    /**
     * 删除已废除且过期 Token
     */
    @Delete("""
            DELETE FROM refresh_tokens
            WHERE is_revoked = true
              AND revoked_at < #{cleanupTime}
            """)
    Integer deleteRevokedTokens(
            @Param("cleanupTime") LocalDateTime cleanupTime
    );

    /**
     * 根据ID加锁查询
     */
    @Select("""
            SELECT * FROM refresh_tokens 
            WHERE id = #{id}
            FOR UPDATE
            """)
    RefreshToken findByIdForUpdate(@Param("id") Long id);
}
