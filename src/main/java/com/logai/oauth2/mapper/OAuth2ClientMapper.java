package com.logai.oauth2.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.oauth2.entity.OAuth2Client;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OAuth2ClientMapper extends BaseMapper<OAuth2Client> {
    @Select("SELECT * FROM oauth2_client WHERE client_id = #{clientId}")
    OAuth2Client findByClientId(@Param("clientId") String clientId);

    @Select("""
            SELECT * FROM oauth2_client
            WHERE client_name = #{clientName}
              AND JSON_CONTAINS(redirect_uris, #{redirectUrisJson})
              AND JSON_LENGTH(redirect_uris) = JSON_LENGTH(#{redirectUrisJson})
              AND JSON_CONTAINS(grant_types, #{grantTypesJson})
              AND JSON_LENGTH(grant_types) = JSON_LENGTH(#{grantTypesJson})
              AND JSON_CONTAINS(response_types, #{responseTypesJson})
              AND JSON_LENGTH(response_types) = JSON_LENGTH(#{responseTypesJson})
            LIMIT 1
            """)
    OAuth2Client findExistingClient(
            @Param("clientName") String clientName,
            @Param("redirectUrisJson") String redirectUrisJson,
            @Param("grantTypesJson") String grantTypesJson,
            @Param("responseTypesJson") String responseTypesJson
    );
}