package org.nofdev.http.oauth2

import groovy.json.JsonBuilder
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.nofdev.servicefacade.UnhandledException
import spock.lang.Specification

/**
 * Created by Liutengfei on 2016/4/25.
 */
class OauthJsonProxySpec extends Specification {
    private ClientAndServer resourceServer
    private def resourceUrl

    private ClientAndServer tokenServer
    private def tokenServerUrl

    def setup() {
        TokenContext.instance.stopTime = 0 //保证每次测试重新获取 token TODO 要从内存中销毁 TokenContext 才合适

        //resource server
        resourceServer = ClientAndServer.startClientAndServer(2016)
        resourceUrl = "http://localhost:2016"

        //token server
        tokenServer = ClientAndServer.startClientAndServer(9527)
        tokenServerUrl = "http://localhost:9527/oauth/token"
    }

    def cleanup() {
        TokenContext.instance.stopTime = 0 //保证每次测试重新获取 token TODO 要从内存中销毁 TokenContext 才合适

        tokenServer.stop()
        resourceServer.stop()
    }


    def "测试入参和返回值"() {
        setup:
        tokenServer.when(HttpRequest.request().withURL("${tokenServerUrl}")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([access_token: token, token_type: "bearer", expires_in: expires_in]).toString())
        )

        resourceServer.when(HttpRequest.request().withURL("${resourceUrl}/facade/json/org.nofdev.http.oauth2/Demo/${method}")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([callId: UUID.randomUUID().toString(), val: val, err: null]).toString())
        )
        OAuthConfig oAuthConfig = new OAuthConfig();
        oAuthConfig.clientId = "test"
        oAuthConfig.clientSecret = "test"
        oAuthConfig.grantType = "client_credentials"
        oAuthConfig.authenticationServerUrl = "${tokenServerUrl}"

        def proxy = new OAuthJsonProxy(
                DemoFacade.class,
                oAuthConfig,
                resourceUrl
        )
        def testFacadeService = proxy.getObject()
        def returnResult = testFacadeService."${method}"(*args);
        expect:
        returnResult == exp
        where:
        method              | args                                | token       | expires_in | tokenExp    | val                                 | exp
        "method1"           | []                                  | '111111111' | 3600       | '111111111' | "hello world"                       | "hello world"
        "sayHello"          | []                                  | '222222222' | 3600       | '222222222' | null                                | null
        "getAllAttendUsers" | [new UserDTO(name: "tom", age: 10)] | '333333333' | 3600       | '222222222' | [new UserDTO(name: "tom", age: 10)] | [new UserDTO(name: "tom", age: 10)]
    }

    def "token过期时自动获取新token"() {
        setup:
        tokenServer.when(HttpRequest.request().withURL("${tokenServerUrl}")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([access_token: '111111111', token_type: "bearer", expires_in: 1]).toString())
        )

        resourceServer.when(HttpRequest.request().withURL("${resourceUrl}/facade/json/org.nofdev.http.oauth2/Demo/method1")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([callId: UUID.randomUUID().toString(), val: 'hello world', err: null]).toString())
        )
        OAuthConfig oAuthConfig = new OAuthConfig();
        oAuthConfig.clientId = "test"
        oAuthConfig.clientSecret = "test"
        oAuthConfig.grantType = "client_credentials"
        oAuthConfig.authenticationServerUrl = "${tokenServerUrl}"

        def proxy = new OAuthJsonProxy(
                DemoFacade.class,
                oAuthConfig,
                resourceUrl
        )
        def testFacadeService = proxy.getObject()
        testFacadeService.method1();
        def tokenResult1 = TokenContext.instance.getAccess_token()
        sleep(3000)
        tokenServer.reset()
        tokenServer.when(HttpRequest.request().withURL("${tokenServerUrl}")).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([access_token: '2222222222', token_type: "bearer", expires_in: 3600]).toString())
        )
        testFacadeService.method1();
        def tokenResult2 = TokenContext.instance.getAccess_token()
        expect:
        tokenResult1 == '111111111'
        tokenResult2 == '2222222222'
    }

    def "token不过期的时候还是使用之前的token"() {
        setup:
        tokenServer.when(HttpRequest.request().withURL("${tokenServerUrl}")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([access_token: '111111111', token_type: "bearer", expires_in: 3600]).toString())
        )
        resourceServer.when(HttpRequest.request().withURL("${resourceUrl}/facade/json/org.nofdev.http.oauth2/Demo/method1")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([callId: UUID.randomUUID().toString(), val: 'hello world', err: null]).toString())
        )
        OAuthConfig oAuthConfig = new OAuthConfig();
        oAuthConfig.clientId = "test"
        oAuthConfig.clientSecret = "test"
        oAuthConfig.grantType = "client_credentials"
        oAuthConfig.authenticationServerUrl = "${tokenServerUrl}"

        def proxy = new OAuthJsonProxy(
                DemoFacade.class,
                oAuthConfig,
                resourceUrl
        )
        def testFacadeService = proxy.getObject()
        testFacadeService.method1();
        def tokenResult1 = TokenContext.instance.getAccess_token()
        sleep(1)
        tokenServer.reset()
        tokenServer.when(HttpRequest.request().withURL("${tokenServerUrl}")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([access_token: '22222222', token_type: "bearer", expires_in: 3600]).toString())
        )
        testFacadeService.method1();
        def tokenResult2 = TokenContext.instance.getAccess_token()
        expect:
            tokenResult1=='111111111'
            tokenResult2=='111111111'

    }

    def "当client_id或client_secret验证错误时"() {
        setup:
        tokenServer.when(
                HttpRequest.request()
                        .withURL("${tokenServerUrl}")
                        .withBody("grant_type=client_credentials&client_secret=test&client_id=test")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(400)
                        .withBody("{'code':400,'error':'invalid_client','error_description':'Client credentials are invalid'}")
        )

        resourceServer.when(HttpRequest.request().withURL("${resourceUrl}/facade/json/org.nofdev.http.oauth2/Demo/sayHello")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([callId: UUID.randomUUID().toString(), val: 'hello world', err: null]).toString())
        )
        OAuthConfig oAuthConfig = new OAuthConfig();
        oAuthConfig.clientId = "test"
        oAuthConfig.clientSecret = "test"
        oAuthConfig.grantType = "client_credentials"
        oAuthConfig.authenticationServerUrl = "http://localhost:9527/oauth/token"

        def proxy = new OAuthJsonProxy(
                DemoFacade.class,
                oAuthConfig,
                resourceUrl
        )
        def testFacadeService = proxy.getObject()
        when:
        testFacadeService.sayHello()
        then:
        thrown(AuthenticationException)
    }

    def "当token服务器宕机的时候"() {
        setup:
        resourceServer.when(HttpRequest.request().withURL("${resourceUrl}/facade/json/org.nofdev.http.oauth2/Demo/sayHello")
        ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(new JsonBuilder([callId: UUID.randomUUID().toString(), val: 'hello world', err: null]).toString())
        )
        OAuthConfig oAuthConfig = new OAuthConfig();
        oAuthConfig.clientId = "test"
        oAuthConfig.clientSecret = "test"
        oAuthConfig.grantType = "client_credentials"
        oAuthConfig.authenticationServerUrl = "http://localhost:9527/oauth/token"

        def proxy = new OAuthJsonProxy(
                DemoFacade.class,
                oAuthConfig,
                resourceUrl
        )
        def testFacadeService = proxy.getObject()
        when:
        testFacadeService.sayHello()
        then:
        thrown(UnhandledException)
    }
}

