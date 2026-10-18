package com.sky.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用户端「获取营业状态」接口的集成测试(通过 HttpClient 发起真实 HTTP 请求)
 *
 * 目标接口:GET /user/shop/status
 * 该接口属于 user 端,不在 JwtTokenAdminInterceptor 拦截范围内(/admin/**),无需携带 token。
 *
 * 说明:@SpringBootTest(webEnvironment = RANDOM_PORT) 会在测试时自动启动内嵌 Tomcat
 * (随机端口),无需另外单独启动后端 jar。MySQL 与 Redis 需处于运行状态
 * (本接口读取 Redis 中的 Shop_status,未设置时默认返回 0=已打烊)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
public class HttpClientTest {

    //Spring 注入内嵌 Tomcat 实际监听的随机端口
    @LocalServerPort
    private int port;

    @Test
    public void testGetShopStatus() throws Exception {
        //1. 构建请求:用户端获取店铺营业状态
        String url = "http://localhost:" + port + "/user/shop/status";
        System.out.println("==========================================");
        System.out.println("请求方式: GET");
        System.out.println("请求地址: " + url);
        System.out.println("==========================================");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        //2. 发送请求并获取响应
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        //3. 输出结果
        System.out.println("HTTP 状态码: " + response.statusCode());
        System.out.println("响应体: " + response.body());
        System.out.println("==========================================");

        //4. 解析统一返回结果 Result{code,msg,data}
        JsonNode root = new ObjectMapper().readTree(response.body());
        int code = root.path("code").asInt();
        assertEquals(1, code, "接口返回 code 应为 1(成功),完整响应: " + response.body());

        int status = root.path("data").asInt();
        System.out.println(">>> 店铺营业状态:" + (status == 1 ? "营业中" : "已打烊")
                + "  (data=" + status + ", 1=营业中, 0=已打烊)");
    }
}
