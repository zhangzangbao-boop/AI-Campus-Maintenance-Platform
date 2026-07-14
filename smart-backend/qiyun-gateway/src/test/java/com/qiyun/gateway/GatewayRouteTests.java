package com.qiyun.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway路由配置测试
 * 验证路由规则正确配置
 */
@SpringBootTest
@ActiveProfiles("test")
class GatewayRouteTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    @DisplayName("user-auth-route存在且配置正确")
    void userAuthRoute_exists() {
        List<Route> routes = collectRoutes();

        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        assertThat(userAuthRoute).as("user-auth-route应存在").isNotNull();
        assertThat(userAuthRoute.getUri().toString()).isEqualTo("lb://qiyun-user-service");
    }

    @Test
    @DisplayName("user-auth-route目标URI为lb://qiyun-user-service")
    void userAuthRoute_uriCorrect() {
        List<Route> routes = collectRoutes();

        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        assertThat(userAuthRoute).isNotNull();
        assertThat(userAuthRoute.getUri().toString()).isEqualTo("lb://qiyun-user-service");
    }

    @Test
    @DisplayName("user-auth-route匹配/api/auth/**")
    void userAuthRoute_matchesAuthPath() {
        List<Route> routes = collectRoutes();

        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        assertThat(userAuthRoute).isNotNull();

        // 验证路由配置包含/api/auth/**路径断言
        String predicateString = userAuthRoute.getPredicate().toString();
        assertThat(predicateString).contains("/api/auth/");
    }

    @Test
    @DisplayName("ai-service-route仍指向lb://qiyun-ai-service")
    void aiServiceRoute_uriCorrect() {
        List<Route> routes = collectRoutes();

        Route aiRoute = routes.stream()
                .filter(r -> r.getId().equals("ai-service-route"))
                .findFirst()
                .orElse(null);

        assertThat(aiRoute).as("ai-service-route应存在").isNotNull();
        assertThat(aiRoute.getUri().toString()).isEqualTo("lb://qiyun-ai-service");
    }

    @Test
    @DisplayName("biz-service-route仍指向lb://qiyun-biz-service")
    void bizServiceRoute_uriCorrect() {
        List<Route> routes = collectRoutes();

        Route bizRoute = routes.stream()
                .filter(r -> r.getId().equals("biz-service-route"))
                .findFirst()
                .orElse(null);

        assertThat(bizRoute).as("biz-service-route应存在").isNotNull();
        assertThat(bizRoute.getUri().toString()).isEqualTo("lb://qiyun-biz-service");
    }

    @Test
    @DisplayName("user-auth-route优先级高于biz-service-route")
    void userAuthRoute_higherPriority() {
        List<Route> routes = collectRoutes();

        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        Route bizRoute = routes.stream()
                .filter(r -> r.getId().equals("biz-service-route"))
                .findFirst()
                .orElse(null);

        assertThat(userAuthRoute).isNotNull();
        assertThat(bizRoute).isNotNull();

        // order值越小优先级越高
        assertThat(userAuthRoute.getOrder()).isLessThan(bizRoute.getOrder());
    }

    @Test
    @DisplayName("ai-service-route优先级高于user-auth-route")
    void aiServiceRoute_higherPriority() {
        List<Route> routes = collectRoutes();

        Route aiRoute = routes.stream()
                .filter(r -> r.getId().equals("ai-service-route"))
                .findFirst()
                .orElse(null);

        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        assertThat(aiRoute).isNotNull();
        assertThat(userAuthRoute).isNotNull();

        // order值越小优先级越高
        assertThat(aiRoute.getOrder()).isLessThan(userAuthRoute.getOrder());
    }

    @Test
    @DisplayName("/api/users/**没有被精确转到user-service")
    void usersApi_notRoutedToUserService() {
        List<Route> routes = collectRoutes();

        // /api/users/** 应该匹配biz-service-route（order: 10），不是user-auth-route
        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        assertThat(userAuthRoute).isNotNull();

        // 验证user-auth-route的路径断言只匹配/api/auth/**
        String predicateString = userAuthRoute.getPredicate().toString();
        assertThat(predicateString).doesNotContain("/api/users/");
    }

    @Test
    @DisplayName("/api/admin/users/**没有被精确转到user-service")
    void adminUsersApi_notRoutedToUserService() {
        List<Route> routes = collectRoutes();

        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        assertThat(userAuthRoute).isNotNull();

        // 验证user-auth-route的路径断言不包含/api/admin/users
        String predicateString = userAuthRoute.getPredicate().toString();
        assertThat(predicateString).doesNotContain("/api/admin/");
    }

    @Test
    @DisplayName("不存在重复route id")
    void noDuplicateRouteIds() {
        List<Route> routes = collectRoutes();

        List<String> routeIds = routes.stream()
                .map(Route::getId)
                .toList();

        Map<String, Integer> countMap = new HashMap<>();
        for (String id : routeIds) {
            countMap.merge(id, 1, Integer::sum);
        }

        List<String> duplicates = countMap.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        assertThat(duplicates).as("不应存在重复的route id").isEmpty();
    }

    @Test
    @DisplayName("路由顺序正确：ai(0) < auth(1) < biz(10)")
    void routeOrderCorrect() {
        List<Route> routes = collectRoutes();

        Route aiRoute = routes.stream()
                .filter(r -> r.getId().equals("ai-service-route"))
                .findFirst()
                .orElse(null);

        Route userAuthRoute = routes.stream()
                .filter(r -> r.getId().equals("user-auth-route"))
                .findFirst()
                .orElse(null);

        Route bizRoute = routes.stream()
                .filter(r -> r.getId().equals("biz-service-route"))
                .findFirst()
                .orElse(null);

        assertThat(aiRoute).isNotNull();
        assertThat(userAuthRoute).isNotNull();
        assertThat(bizRoute).isNotNull();

        // order: ai=0, auth=1, biz=10
        assertThat(aiRoute.getOrder()).isEqualTo(0);
        assertThat(userAuthRoute.getOrder()).isEqualTo(1);
        assertThat(bizRoute.getOrder()).isEqualTo(10);
    }

    @Test
    @DisplayName("三个路由全部存在")
    void allRoutesExist() {
        List<Route> routes = collectRoutes();

        List<String> routeIds = routes.stream()
                .map(Route::getId)
                .toList();

        assertThat(routeIds).contains("ai-service-route", "user-auth-route", "biz-service-route");
    }

    private List<Route> collectRoutes() {
        List<Route> routes = new ArrayList<>();
        routeLocator.getRoutes()
                .doOnNext(routes::add)
                .blockLast();
        return routes;
    }
}