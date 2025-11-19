package com.logai.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.security.entity.RefreshToken;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    /**
     * 根据Token哈希查找有效的Refresh Token
     */
    @Select("SELECT * FROM refresh_tokens WHERE token_hash = :tokenHash AND is_revoked = false AND expires_at > :now")
    RefreshToken findValidByTokenHash(String tokenHash, LocalDateTime now);

    /**
     * 根据Token哈希和客户端ID查找有效的Refresh Token
     */
    @Select("SELECT * FROM refresh_tokens WHERE token_hash = :tokenHash AND client_id = :clientId AND is_revoked = false AND expires_at > :now")
    RefreshToken findValidByTokenHashAndClientId(String tokenHash, String clientId, LocalDateTime now);

    /**
     * 根据用户ID查找所有有效的Refresh Token
     */
    @Select("SELECT * FROM refresh_tokens WHERE user_id = :userId AND is_revoked = false AND expires_at > :now")
    List<RefreshToken> findValidByUserId(Long userId, LocalDateTime now);

    /**
     * 根据用户ID和设备ID查找有效的Refresh Token
     */
    @Select("SELECT * FROM refresh_tokens WHERE user_id = :userId AND device_id = :deviceId AND is_revoked = false AND expires_at > :now")
    RefreshToken findValidByUserIdAndDeviceId(Long userId, String deviceId, LocalDateTime now);

    /**
     * 废除用户的所有Refresh Token
     */
    @Update("UPDATE refresh_tokens SET is_revoked = true, revoked_at = :revokedAt, revoke_reason = :reason WHERE user_id = :userId AND is_revoked = false")
    Integer revokeAllByUserId(Long userId, LocalDateTime revokedAt, String reason);

    /**
     * 废除过期的Refresh Token
     */
    @Update("UPDATE refresh_tokens SET is_revoked = true, revoked_at = :revokedAt, revoke_reason = 'Expired' WHERE expires_at < :expiredTime AND is_revoked = false")
    Integer revokeExpiredTokens(LocalDateTime expiredTime, LocalDateTime revokedAt);

    /**
     * 统计用户有效的Refresh Token数量
     */
    @Select("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = :userId AND is_revoked = false AND expires_at > :now")
    Long countValidByUserId(Long userId, LocalDateTime now);

    /**
     * 查找即将过期的Refresh Token（用于提醒）
     */
    @Select("SELECT * FROM refresh_tokens WHERE expires_at BETWEEN :now AND :soon AND is_revoked = false")
    List<RefreshToken> findExpiringSoon(LocalDateTime now, LocalDateTime soon);

    /**
     * 删除已废除且过期的Token（清理任务）
     */
    @Delete("DELETE FROM refresh_tokens WHERE is_revoked = true AND revoked_at < :cleanupTime")
    Integer deleteRevokedTokens(LocalDateTime cleanupTime);

    /**
     * 根据ID查找Token（带悲观锁）
     */
    @Select("SELECT * FROM refresh_tokens WHERE id = :id FOR UPDATE")
    RefreshToken findByIdForUpdate(Long id);
}
