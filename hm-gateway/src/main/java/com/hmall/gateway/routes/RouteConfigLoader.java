package com.hmall.gateway.routes;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class RouteConfigLoader {

    private final NacosConfigManager configManager;

    private final RouteDefinitionWriter writer;

    private final static String DATA_ID = "gateway-route.json";

    private final static String GROUP = "DEFAULT_GROUP";

    private Set<String> routeIds = new HashSet<>();

    // PostConstruct注解的方法将会在依赖注入完成后被自动调用
    @PostConstruct
    public void initRouteConfiguration() throws NacosException {

        // 1. 第一次启动时, 拉取路由表, 并且添加监听器
        String configInfo = configManager.getConfigService().getConfigAndSignListener(DATA_ID, GROUP, 1000, new Listener() {
            @Override
            public Executor getExecutor() {
                return Executors.newSingleThreadExecutor();
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                // 监听到路由变更, 更新路由表
                updateRouteConfigInfo(configInfo);
            }
        });
        // 2. 写入路由表
        updateRouteConfigInfo(configInfo);
    }

    private void updateRouteConfigInfo(String configInfo) {
        // 1. 解析路由信息
        List<RouteDefinition> routeDefinitions = JSONUtil.toList(configInfo, RouteDefinition.class);

        // 2. 删除旧的路由表
        for (String routeId : routeIds) {
            writer.delete(Mono.just(routeId)).subscribe();
        }

        // 3. 判断是否有新路由
        if (routeDefinitions == null || routeDefinitions.isEmpty()) {
            // 无新路由, 直接返回
            return;
        }
        routeIds.clear();

        // 4. 更新路由表
        for (RouteDefinition routeDefinition : routeDefinitions) {
            // 4.1 写入路由表
            writer.save(Mono.just(routeDefinition)).subscribe();
            // 4.2 记录路由id
            routeIds.add(routeDefinition.getId());
        }
    }
}
