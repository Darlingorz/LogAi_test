package com.logai.security.oauth2.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logai.security.oauth2.entity.OAuth2Client;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OAuth2ClientMapper extends BaseMapper<OAuth2Client> {
    @Select("SELECT * FROM oauth2_client WHERE client_id = :clientId")
    OAuth2Client findByClientId(String clientId);

    @Select("""
            SELECT * FROM oauth2_client
            WHERE client_name = :clientName
              AND JSON_CONTAINS(redirect_uris, :redirectUrisJson)
              AND JSON_LENGTH(redirect_uris) = JSON_LENGTH(:redirectUrisJson)
              AND JSON_CONTAINS(grant_types, :grantTypesJson)
              AND JSON_LENGTH(grant_types) = JSON_LENGTH(:grantTypesJson)
              AND JSON_CONTAINS(response_types, :responseTypesJson)
              AND JSON_LENGTH(response_types) = JSON_LENGTH(:responseTypesJson)
            LIMIT 1
            """)
    OAuth2Client findExistingClient(
            String clientName,
            String redirectUrisJson,
            String grantTypesJson,
            String responseTypesJson
    );
}