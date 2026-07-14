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
    @DisplayName("users-route存在且指向user-service")
    void usersRoute_exists() {
        List<Route> routes = collectRoutes();

        Route usersRoute = routes.stream()
                .filter(r -> r.getId().equals("users-route"))
                .findFirst()
                .orElse(null);

        assertThat(usersRoute).as("users-route应存在").isNotNull();
        assertThat(usersRoute.getUri().toString()).isEqualTo("lb://qiyun-user-service");
    }

    @Test
    @DisplayName("admin-users-route存在且指向user-service")
    void adminUsersRoute_exists() {
        List<Route> routes = collectRoutes();

        Route adminUsersRoute = routes.stream()
                .filter(r -> r.getId().equals("admin-users-route"))
                .findFirst()
                .orElse(null);

        assertThat(adminUsersRoute).as("admin-users-route应存在").isNotNull();
        assertThat(adminUsersRoute.getUri().toString()).isEqualTo("lb://qiyun-user-service");
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
    @DisplayName("路由优先级正确：ai(0) < auth(1) < admin-users(2) < users(3) < biz(10)")
    void routePriorityCorrect() {
        List<Route> routes = collectRoutes();

        Route aiRoute = routes.stream().filter(r -> r.getId().equals("ai-service-route")).findFirst().orElse(null);
        Route authRoute = routes.stream().filter(r -> r.getId().equals("user-auth-route")).findFirst().orElse(null);
        Route adminUsersRoute = routes.stream().filter(r -> r.getId().equals("admin-users-route")).findFirst().orElse(null);
        Route usersRoute = routes.stream().filter(r -> r.getId().equals("users-route")).findFirst().orElse(null);
        Route bizRoute = routes.stream().filter(r -> r.getId().equals("biz-service-route")).findFirst().orElse(null);

        assertThat(aiRoute).isNotNull();
        assertThat(authRoute).isNotNull();
        assertThat(adminUsersRoute).isNotNull();
        assertThat(usersRoute).isNotNull();
        assertThat(bizRoute).isNotNull();

        // 验证优先级顺序
        assertThat(aiRoute.getOrder()).isEqualTo(0);
        assertThat(authRoute.getOrder()).isEqualTo(1);
        assertThat(adminUsersRoute.getOrder()).isEqualTo(2);
        assertThat(usersRoute.getOrder()).isEqualTo(3);
        assertThat(bizRoute.getOrder()).isEqualTo(10);

        // 验证优先级关系
        assertThat(aiRoute.getOrder()).isLessThan(authRoute.getOrder());
        assertThat(authRoute.getOrder()).isLessThan(adminUsersRoute.getOrder());
        assertThat(adminUsersRoute.getOrder()).isLessThan(usersRoute.getOrder());
        assertThat(usersRoute.getOrder()).isLessThan(bizRoute.getOrder());
    }

    @Test
    @DisplayName("/api/admin/stats不转user-service")
    void adminStats_notRoutedToUserService() {
        List<Route> routes = collectRoutes();

        Route adminUsersRoute = routes.stream()
                .filter(r -> r.getId().equals("admin-users-route"))
                .findFirst()
                .orElse(null);

        assertThat(adminUsersRoute).isNotNull();

        // 验证admin-users-route只匹配/api/admin/users/**
        String predicateString = adminUsersRoute.getPredicate().toString();
        assertThat(predicateString).contains("/api/admin/users/");
        assertThat(predicateString).doesNotContain("/api/admin/stats");
    }

    @Test
    @DisplayName("/api/admin/repair-orders不转user-service")
    void adminRepairOrders_notRoutedToUserService() {
        List<Route> routes = collectRoutes();

        Route adminUsersRoute = routes.stream()
                .filter(r -> r.getId().equals("admin-users-route"))
                .findFirst()
                .orElse(null);

        assertThat(adminUsersRoute).isNotNull();

        String predicateString = adminUsersRoute.getPredicate().toString();
        assertThat(predicateString).doesNotContain("/api/admin/repair-orders");
    }

    @Test
    @DisplayName("/api/admin/feedbacks不转user-service")
    void adminFeedbacks_notRoutedToUserService() {
        List<Route> routes = collectRoutes();

        Route adminUsersRoute = routes.stream()
                .filter(r -> r.getId().equals("admin-users-route"))
                .findFirst()
                .orElse(null);

        assertThat(adminUsersRoute).isNotNull();

        String predicateString = adminUsersRoute.getPredicate().toString();
        assertThat(predicateString).doesNotContain("/api/admin/feedbacks");
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
    @DisplayName("/internal/**路径不经过Gateway")
    void internalPathNotRoutedThroughGateway() {
        List<Route> routes = collectRoutes();

        // 检查所有路由的predicate，确保没有匹配/internal/**
        for (Route route : routes) {
            String predicateString = route.getPredicate().toString();
            assertThat(predicateString)
                .as("路由 %s 不应匹配 /internal/** 路径", route.getId())
                .doesNotContain("/internal/");
        }
    }

    @Test
    @DisplayName("admin-ops-route路由统计和评价路径")
    void adminOpsRoute_matchesStatsAndFeedbacks() {
        List<Route> routes = collectRoutes();

        Route adminOpsRoute = routes.stream()
            .filter(r -> r.getId().equals("admin-ops-route"))
            .findFirst()
            .orElse(null);

        assertThat(adminOpsRoute).as("admin-ops-route应存在").isNotNull();
        assertThat(adminOpsRoute.getUri().toString()).isEqualTo("lb://qiyun-ops-service");

        // 验证predicate包含统计和评价路径
        String predicateString = adminOpsRoute.getPredicate().toString();
        assertThat(predicateString).contains("/api/admin/stats");
        assertThat(predicateString).contains("/api/admin/feedbacks");
    }

    @Test
    @DisplayName("所有路由全部存在")
    void allRoutesExist() {
        List<Route> routes = collectRoutes();

        List<String> routeIds = routes.stream()
            .map(Route::getId)
            .toList();

        assertThat(routeIds).contains(
            "ai-service-route",
            "user-auth-route",
            "admin-users-route",
            "users-route",
            "repair-service-route",
            "admin-repair-route",
            "notification-route",
            "knowledge-base-route",
            "admin-ops-route",
            "biz-service-route"
        );
    }

    @Test
    @DisplayName("notification-route存在且指向ops-service")
    void notificationRoute_exists() {
        List<Route> routes = collectRoutes();

        Route notificationRoute = routes.stream()
            .filter(r -> r.getId().equals("notification-route"))
            .findFirst()
            .orElse(null);

        assertThat(notificationRoute).as("notification-route应存在").isNotNull();
        assertThat(notificationRoute.getUri().toString()).isEqualTo("lb://qiyun-ops-service");
    }

    @Test
    @DisplayName("knowledge-base-route存在且指向ops-service")
    void knowledgeBaseRoute_exists() {
        List<Route> routes = collectRoutes();

        Route knowledgeBaseRoute = routes.stream()
            .filter(r -> r.getId().equals("knowledge-base-route"))
            .findFirst()
            .orElse(null);

        assertThat(knowledgeBaseRoute).as("knowledge-base-route应存在").isNotNull();
        assertThat(knowledgeBaseRoute.getUri().toString()).isEqualTo("lb://qiyun-ops-service");
    }

    @Test
    @DisplayName("admin-ops-route存在且指向ops-service")
    void adminOpsRoute_exists() {
        List<Route> routes = collectRoutes();

        Route adminOpsRoute = routes.stream()
            .filter(r -> r.getId().equals("admin-ops-route"))
            .findFirst()
            .orElse(null);

        assertThat(adminOpsRoute).as("admin-ops-route应存在").isNotNull();
        assertThat(adminOpsRoute.getUri().toString()).isEqualTo("lb://qiyun-ops-service");
    }

    @Test
    @DisplayName("repair-service-route存在且指向repair-service")
    void repairServiceRoute_exists() {
        List<Route> routes = collectRoutes();

        Route repairRoute = routes.stream()
            .filter(r -> r.getId().equals("repair-service-route"))
            .findFirst()
            .orElse(null);

        assertThat(repairRoute).as("repair-service-route应存在").isNotNull();
        assertThat(repairRoute.getUri().toString()).isEqualTo("lb://qiyun-repair-service");
    }

    @Test
    @DisplayName("admin-repair-route存在且指向repair-service")
    void adminRepairRoute_exists() {
        List<Route> routes = collectRoutes();

        Route adminRepairRoute = routes.stream()
            .filter(r -> r.getId().equals("admin-repair-route"))
            .findFirst()
            .orElse(null);

        assertThat(adminRepairRoute).as("admin-repair-route应存在").isNotNull();
        assertThat(adminRepairRoute.getUri().toString()).isEqualTo("lb://qiyun-repair-service");
    }

    private List<Route> collectRoutes() {
        List<Route> routes = new ArrayList<>();
        routeLocator.getRoutes()
                .doOnNext(routes::add)
                .blockLast();
        return routes;
    }
}