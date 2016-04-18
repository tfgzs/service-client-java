package org.nofdev.http.oauth2

import groovy.transform.CompileStatic
import org.apache.oltu.oauth2.client.OAuthClient
import org.apache.oltu.oauth2.client.URLConnectionClient
import org.apache.oltu.oauth2.client.request.OAuthClientRequest
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse
import org.apache.oltu.oauth2.common.exception.OAuthProblemException
import org.apache.oltu.oauth2.common.message.types.GrantType
import org.nofdev.http.DefaultProxyStrategyImpl
import org.nofdev.http.ExceptionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by HouDongQiang on 2016/3/16.
 * A StrategyImpl class for OAuth2 oauth2. OAuth2client的 增强实现
 */
@CompileStatic
class OAuthProxyStrategyImpl extends DefaultProxyStrategyImpl {

    private static final Logger logger = LoggerFactory.getLogger(OAuthProxyStrategyImpl.class)
    private String baseURL
    private OAuthDTO oAuthDTO

    public OAuthProxyStrategyImpl(String baseURL, OAuthDTO oAuthDTO) {
        super(baseURL)
        this.baseURL = baseURL
        this.oAuthDTO = oAuthDTO
    }

    /**
     * get accessToken. 获取一个accessToken
     * @return accessToken
     * @throws OAuthProblemException
     */

    /*  ---------------------
        client_credentials
        ---------------------
        post body
        client_id       d712fa37-e965-4d37-9714-a87af8e6991b
        client_secret   52f0a3bb-6411-4312-97fa-e2cf692e2c09
        grant_type      client_credentials
        url:      http://10.32.150.121:3000/oauth/token
    */

    public String getAccessToken() throws OAuthProblemException {

        //假设所有需要token的信息都正确不检测
        def accessToken

        //todo 默认为client_credentials方式 待改造成4种都支持的

        if (GrantType.CLIENT_CREDENTIALS.toString().equals(oAuthDTO.grantType)) {
            OAuthClientRequest request = OAuthClientRequest
                    .tokenLocation(oAuthDTO.authenticationServerUrl)
                    .setGrantType(GrantType.CLIENT_CREDENTIALS)
                    .setClientId(oAuthDTO.getClientId())
                    .setClientSecret(oAuthDTO.getClientSecret())
                    .buildBodyMessage();

            OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
            OAuthJSONAccessTokenResponse oAuthResponse = oAuthClient.accessToken(request, OAuthJSONAccessTokenResponse.class);
            accessToken = oAuthResponse.getAccessToken()

        } else {
            logger.error("现在支持" + GrantType.CLIENT_CREDENTIALS.toString() + "方式")
            throw new Exception("grant_type not support!")
        }

        return accessToken
    }

    /**
     * AcessToen isexpire  是否过期?
     * @param accessToken
     * @return
     */

    boolean isExpire(String accessToken) {

        //todo 是否自己实现检测expireIn


    }

    /**
     *  refresh accessToken 刷新token
     * @return
     */
    public String refreshAccessToken() {
        //todo 暂时重新获取token
        getAccessToken()
    }


}