package com.example.opcua;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.entity.OpcUaAddress;
import com.example.mapper.OpcUaAddressMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedDataItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.example.constant.DptConstant;
import org.example.datapoint.DpValueItem;
import org.example.rabbitmq.RabbitmqService;
import org.example.redis.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OpcClient implements ApplicationRunner {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RabbitmqService rabbitmqService;

    @Autowired
    private OpcUaAddressMapper opcUaAddressMapper;

    private static List<OpcUaAddress> opcUaAddressList = new ArrayList<>();

    private static final HashMap<String, OpcUaAddress> urlMap = new HashMap<>();

    private final Double defaultSamplingInterval = 1000D;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        opcUaAddressList = opcUaAddressMapper.selectList(new LambdaQueryWrapper<OpcUaAddress>());

        opcUaAddressList.forEach(e -> {
            if (!urlMap.containsKey(e.getUrl())) {
                urlMap.put(e.getUrl(), e);
            }
        });

        if (!urlMap.isEmpty()) {
            urlMap.forEach((key, value) -> {
                new Thread(() -> {
                    start(value);
                }).start();
            });
        }

    }

    public void start(OpcUaAddress opcUaAddress) {
        // 开启连接
        try {
            OpcUaClient opcUaClient = createClient(opcUaAddress);
            opcUaClient.connect().get();

            List<NodeId> subscriptionNodeList = setNodeLists(opcUaAddress.getUrl());
            // 订阅
            if (subscriptionNodeList.size() != 0) {
                new Thread(() -> {
                    final CountDownLatch eventLatch = new CountDownLatch(1);

                    // 添加订阅监听器，用于处理断线重连后的订阅问题
                    OpcUaSubscriptionManager subscriptionManager = opcUaClient.getSubscriptionManager();
                    subscriptionManager.addSubscriptionListener(new CustomSubscriptionListener(opcUaClient));
                    // 批量订阅
                    managedSubscriptionEvent(opcUaClient, subscriptionNodeList);

                    //持续监听
                    try {
                        eventLatch.await();
                    } catch (InterruptedException e) {
                        log.info("{} 订阅入口线程退出", getClass().getSimpleName(), e);
                    }
                    log.info("{} 下的节点订阅执行完成", getClass().getSimpleName());
                }).start();
            }
        } catch (Exception e) {
            log.error("{} start failed, url {}", getClass().getSimpleName(), opcUaAddress.getUrl(), e);
        }
    }

    /**
     * 创建OPC UA客户端
     *
     * @return
     * @throws Exception
     */
    private OpcUaClient createClient(OpcUaAddress opcUaAddress) throws Exception {
        Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security");
        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new Exception("unable to create security dir: " + securityTempDir);
        }
        return OpcUaClient.create(opcUaAddress.getUrl(),
                endpoints ->
                        endpoints.stream()
                                .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
                                .findFirst(),
                configBuilder ->
                        configBuilder
                                .setApplicationName(LocalizedText.english("milo opc-ua client"))
                                .setApplicationUri(opcUaAddress.getUrl())
                                //.setIdentityProvider(new UsernameProvider(opcUaAddress.getUserName(), opcUaAddress.getPassword()))
                                .setIdentityProvider(new AnonymousProvider())
                                .setRequestTimeout(UInteger.valueOf(5000))
                                .build()
        );
    }

    /**
     * 根据ip筛选节点
     */
    public List<NodeId> setNodeLists(String url) {
        List<NodeId> nodeIdList = new ArrayList<>();
        List<OpcUaAddress> addressList = opcUaAddressList.stream().filter(s -> s.getUrl().equals(url)).collect(Collectors.toList());
        for (OpcUaAddress address : addressList) {
            nodeIdList.add(new NodeId(3, address.getAddress()));
        }
        return nodeIdList;
    }

    /**
     * 监听数据变化后的操作
     *
     * @param
     */
    private void doAfterSubscriptionChange(ManagedDataItem item, DataValue value) {
        String nodeName = item.getReadValueId().getNodeId().getIdentifier().toString();
        String url = item.getClient().getConfig().getApplicationUri();
        if (value.getValue() == null || value.getValue().getValue() == null) {
            log.warn("...OpcUa Received null: {}", nodeName);
            return;
        }
        String nodeValue = value.getValue().getValue().toString();
        String keyRedis = DataPointService.getDataPointByUrlAndName(url, nodeName);
        String dpValue = JSON.toJSONString(new DpValueItem(keyRedis, nodeValue, value.getSourceTime().getJavaDate()), SerializerFeature.WriteDateUseDateFormat);
        log.info("...OpcUa Received: {}", dpValue);

        //redisCache.set(RedisKeyPrefix.DATA_POINT + keyRedis, dpValue); //原子操作，不判断是否成功
        rabbitmqService.sendMessage(DptConstant.EXCHANGE, DptConstant.DPT_PREDIX + (keyRedis.split(":"))[0], dpValue);
    }

    /**
     * 批量订阅
     */
    private void managedSubscriptionEvent(OpcUaClient client, List<NodeId> nodeList) {
        try {
            if (CollectionUtils.isEmpty(nodeList)) {
                return;
            }

            ManagedSubscription subscription = ManagedSubscription.create(client);
            if (null != defaultSamplingInterval && defaultSamplingInterval > 0) {
                subscription.setDefaultSamplingInterval(defaultSamplingInterval);
            }

            List<ManagedDataItem> dataItemList = subscription.createDataItems(nodeList);
            for (ManagedDataItem managedDataItem : dataItemList) {
                String nodeName = managedDataItem.getReadValueId().getNodeId().toString();
                if (managedDataItem.getStatusCode().isGood()) {
                    log.info("{} item created success for nodeId {}", getClass().getSimpleName(), nodeName);
                } else {
                    log.error("{} item created failed for nodeId {}, status {}", getClass().getSimpleName(), nodeName, managedDataItem.getStatusCode());
                }
                managedDataItem.addDataValueListener(this::doAfterSubscriptionChange);
            }
            log.info("{} subscriptions finish...", getClass().getSimpleName());
        } catch (Exception e) {
            log.error("{} 批量订阅数据节点发生异常", getClass().getSimpleName(), e);
        }
    }

    /**
     * 自定义订阅监听
     */
    private class CustomSubscriptionListener implements UaSubscriptionManager.SubscriptionListener {

        private OpcUaClient client;

        CustomSubscriptionListener(OpcUaClient client) {
            this.client = client;
        }

        public void onKeepAlive(UaSubscription subscription, DateTime publishTime) {
        }

        public void onStatusChanged(UaSubscription subscription, StatusCode status) {
            log.info("onStatusChanged : {}", subscription);
        }

        public void onPublishFailure(UaException exception) {
            log.info("onPublishFailure : {}", exception);
        }

        public void onNotificationDataLost(UaSubscription subscription) {
            log.info("onNotificationDataLost : {}", subscription);
        }

        /**
         * 重连时 尝试恢复之前的订阅失败时 会调用此方法
         */
        public void onSubscriptionTransferFailed(UaSubscription uaSubscription, StatusCode statusCode) {
            log.info("恢复订阅失败 需要重新订阅");
            //删除老订阅，创建新订阅
            client.getSubscriptionManager().deleteSubscription(uaSubscription.getSubscriptionId());
            //在回调方法中重新订阅
            managedSubscriptionEvent(client, setNodeLists(client.getConfig().getApplicationUri()));
        }
    }
}
