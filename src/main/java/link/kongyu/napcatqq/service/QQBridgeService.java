package link.kongyu.napcatqq.service;

import link.kongyu.napcatqq.ai.Aixi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Luojun
 * @version v1.0.0
 * @since 2026/3/21
 */

@Service
@Slf4j
public class QQBridgeService {

    // 缓存用户的未发送消息：唯一键(senderId_groupId) -> StringBuilder
    private final Map<String, StringBuilder> messageBuffer = new ConcurrentHashMap<>();
    // 缓存用户的定时任务：唯一键 -> ScheduledFuture
    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();
    // 调度器（专门用来做倒计时任务）
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    // 防抖延迟时间（秒）。比如：3秒内没有新消息，就把缓存的多句话合并成一段发给艾夕
    private final int DEBOUNCE_DELAY_SECONDS = 5;
    @Autowired
    Aixi aixi;

    public void receiveMessage(Map<String, Object> payload) {
        String postType = (String) payload.get("post_type");
        if (postType == null) {
            log.warn("PostType为空");
            return;
        }
        switch (postType) {
            case "notice" -> noticeHandler(payload);
            case "message" -> messageHandler(payload);
            default -> log.warn("未知的PostType: {}", postType);
        }
    }

    private void messageHandler(Map<String, Object> payload) {
        String messageType = (String) payload.get("message_type");
        if (messageType == null) {
            log.warn("MessageType为空");
            return;
        }

        Map<String, Object> sender = (Map<String, Object>) payload.get("sender");
        long senderId = Long.parseLong(sender.get("user_id").toString());

        // TODO: 请替换为你的机器人真实 QQ 号，防止它自己回复自己导致死循环！
        if (senderId == Aixi.QQ_ID) {
            return;
        }

        switch (messageType) {
            case "private" -> messagePrivateHandler(payload, senderId);
            case "group" -> messageGroupHandler(payload, senderId);
            default -> log.warn("未知的MessageType: {}", messageType);
        }
    }

    private void messagePrivateHandler(Map<String, Object> payload, long senderId) {
        String rawMessage = (String) payload.get("raw_message");
        String messageType = (String) payload.get("message_type");
        log.info("[私聊接收] [{}]: {} (进入缓冲池等待拼接...)", senderId, rawMessage);

        debounceMessage(senderId, 0L, messageType, rawMessage);
    }

    private void messageGroupHandler(Map<String, Object> payload, long senderId) {
        long groupId = Long.parseLong(payload.get("group_id").toString());
        String rawMessage = (String) payload.get("raw_message");
        String messageType = (String) payload.get("message_type");

        // TODO: 替换为真实的机器人 QQ
        if (rawMessage.contains("艾夕") || rawMessage.contains("[CQ:at,qq="+Aixi.QQ_ID+"]")) {
            log.info("[群聊接收] [{}] {} (进入缓冲池等待拼接...)", groupId, rawMessage);
            debounceMessage(senderId, groupId, messageType, rawMessage);
        }
        else {
            // 普通群消息，不冒泡
        }
    }

    /**
     * 核心防抖合并逻辑：Debounce
     */
    private void debounceMessage(long senderId, long groupId, String messageType, String rawMessage) {
        // 使用 senderId_groupId 作为唯一键，防止不同人、不同群的消息串台
        String key = senderId + "_" + groupId;

        // 1. 将新消息追加到该用户的缓冲池中（用换行符隔开）
        messageBuffer.computeIfAbsent(key, k -> new StringBuilder()).append(rawMessage).append("\n");

        // 2. 如果之前有正在倒计时的任务，取消它！(重置倒计时)
        ScheduledFuture<?> existingTask = taskMap.get(key);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
        }

        // 3. 创建一个新的倒计时任务：等待 3 秒
        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            // 倒计时结束，取出所有合并的消息并清空该键
            StringBuilder mergedBuilder = messageBuffer.remove(key);
            if (mergedBuilder != null) {
                String mergedMessage = mergedBuilder.toString().trim();
                taskMap.remove(key);
                log.info("【倒计时结束！触发召唤】合并后的长消息:\n{}", mergedMessage);

                // 正式召唤艾夕！
                aixi.qqCall(messageType, senderId, groupId, mergedMessage);
            }
        }, DEBOUNCE_DELAY_SECONDS, TimeUnit.SECONDS);

        taskMap.put(key, newTask);
    }

    private void noticeHandler(Map<String, Object> payload) {
        String subType = (String) payload.get("sub_type");
        if (subType == null) { return; }

        switch (subType) {
            case "input_status" -> log.debug("对方正在输入...");
            case "poke" -> {
                long senderId = Long.parseLong(payload.get("sender_id").toString());
                long targetId = Long.parseLong(payload.get("target_id").toString());
                log.info("[{}] 戳了戳 [{}]", senderId, targetId);

                // 戳一戳不需要防抖等待，直接召唤
                aixi.qqCall("private", senderId, 0L, "[系统消息] 用户戳了你一下");
            }
        }
    }
}