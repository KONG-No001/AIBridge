package link.kongyu.napcatqq.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import link.kongyu.napcatqq.client.CatQQClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author Luojun
 * @version v1.0.0
 * @since 2026/3/21
 */

@Service
@Slf4j
public class Aixi {

    public static final long QQ_ID = 2062886978L;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    CatQQClient qqClient;

    public void qqCall(String messageType, long senderId, long groupId, String rawMessage) {
        log.info("开始召唤艾夕，QQ Session: {}, 群号: {}, 消息: {}", senderId, groupId, rawMessage);

        try {
            // 1. 构建调用 OpenClaw 的命令，注意 JSON 参数
            // 在 Windows 下运行 Spring Boot，可能需要加 cmd.exe /c，或者指定 openclaw.cmd 的全路径
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

            // 恢复成长期的、固定的主控会话 ID
            // 以后你在 QQ 上跟我聊天，永远都会连接到这个专属的 "qq-master" 会话记录里，不会随网页端刷新而丢失！
            String targetSessionId = (senderId == 2782180957L) ? "qq-master" : "qq-" + senderId;

            // 针对群聊，如果是群聊消息，我们把群号和发送者都带在 session 里，
            // 也可以选择让同一个人的不同群聊天连在一起，所以沿用 senderId。
            
            // 告诉大模型我是 QQ 机器人，要一句话一句话发，而且要口语化，不能太长
            String systemPrompt = "【系统限制】你现在正在通过 QQ 聊天。QQ 的风控极其严格，一次性发送大段文本会被封号。从现在开始，你的回答必须：1. 极其简短口语化，不要发长篇大论。2. 如果话多，请分成好几条短句用 '\\n---分段---\\n' 隔开。3. 不要发超过50个字的单条消息。";
            String finalRawMessage = systemPrompt + "\n\n用户消息：" + rawMessage;

            ProcessBuilder pb;
            if (isWindows) {
                // 如果是 Windows 环境，通过 wsl.exe 跨系统调用 WSL2 里的 openclaw
                pb = new ProcessBuilder(
                        "wsl.exe", "/home/luojun/.npm-global/bin/openclaw",
                        "agent",
                        "--session-id", targetSessionId,
                        "--message", finalRawMessage,
                        "--json"
                );
            }
            else {
                // 如果原生就在 WSL2 或 Linux 下，直接调用
                pb = new ProcessBuilder(
                        "openclaw",
                        "agent",
                        "--session-id", targetSessionId,
                        "--message", finalRawMessage,
                        "--json"
                );
            }

            // 不合并错误流，防止 OpenClaw 插件的警告混入或阻塞管道
            Process process = pb.start();

            // 2. 读取输出 (只读标准输出里的 JSON)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n"); // 加上换行符
                }
            }

            process.waitFor();
            String jsonResult = output.toString().trim();

            if (jsonResult.isEmpty()) {
                log.warn("艾夕没有返回任何话！");
                return;
            }

            // 3. 解析我返回的 JSON 结果
            int jsonStart = jsonResult.indexOf("{");
            if (jsonStart != -1) {
                jsonResult = jsonResult.substring(jsonStart);
            }
            else {
                log.error("OpenClaw 输出中没有找到 JSON: {}", jsonResult);
                return;
            }

            JsonNode rootNode = objectMapper.readTree(jsonResult);
            // OpenClaw CLI 返回的 JSON 结构通常是在 result.payloads[0].text
            if (rootNode.has("result") && rootNode.get("result").has("payloads")) {
                JsonNode payloads = rootNode.get("result").get("payloads");
                if (payloads.isArray() && payloads.size() > 0) {
                    JsonNode firstPayload = payloads.get(0);
                    if (firstPayload.has("text")) {
                        String aixiReply = firstPayload.get("text").asText();
                        if (!aixiReply.isEmpty() && !"NO_REPLY".equals(aixiReply)) {
                            // 暴力截断：如果存在 "</think>"，我们就直接取它后面的部分作为最终回复
                            int endThinkIdx = aixiReply.lastIndexOf("</think>");
                            if (endThinkIdx != -1) {
                                aixiReply = aixiReply.substring(endThinkIdx + 8).trim();
                            } else {
                                // 如果它用的是纯文本的 "Thinking Process:" 格式
                                int thinkingIdx = aixiReply.indexOf("think\nThinking Process:");
                                if (thinkingIdx != -1) {
                                    // 尝试寻找思考过程的结束标志（通常是两个连续换行）
                                    // 或者直接暴力匹配到倒数第一段
                                    int endProcessIdx = aixiReply.indexOf("\n\n", thinkingIdx);
                                    if (endProcessIdx != -1) {
                                        aixiReply = aixiReply.substring(endProcessIdx).trim();
                                    }
                                }
                            }
                            
                            // 保底：去掉遗留标签
                            aixiReply = aixiReply.replaceAll("(?s)<think>.*?</think>", "").trim();
                            
                            log.info("艾夕回复了: {}", aixiReply);

                            // 判断到底是群还是私聊
                            if ("group".equals(messageType) && groupId > 0) {
                                // 1. 清除大模型自己可能瞎编的错误 CQ 码
                                aixiReply = aixiReply.replaceAll("\\[CQ:[^\\]]*\\]", "").trim();
                                // 2. 官方认证：在回复的最前面，强行加上正确的 At 标签！
                                aixiReply = "[CQ:at,qq=" + senderId + "] \n" + aixiReply;
                            }

                            // 把回复切片发送，模拟人类打字的短句
                            String[] replyPieces = aixiReply.split("---分段---|\n\n");
                            for (String piece : replyPieces) {
                                piece = piece.trim();
                                if (piece.isEmpty()) continue;
                                
                                // 在发消息前，先发一个“正在输入...”的状态
                                try {
                                    qqClient.setInputStatus(senderId, 1);
                                } catch (Exception e) {
                                    log.debug("发送正在输入状态失败（可能API不支持）: {}", e.getMessage());
                                }

                                // 模拟人类思考和打字的停顿（延时1.5秒再发下一句）
                                try {
                                    Thread.sleep(1500); 
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                
                                if ("group".equals(messageType) && groupId > 0) {
                                    String res = qqClient.sendGroupMsg(groupId, piece);
                                    log.info("NapCat 返回群聊发送结果: {}", res);
                                }
                                else {
                                    String res = qqClient.sendPrivateMsg(senderId, piece);
                                    log.info("NapCat 返回私聊发送结果: {}", res);
                                }
                                // 发完结束输入状态
                                try {
                                    qqClient.setInputStatus(senderId, 2);
                                } catch (Exception ignored) {}
                            }
                            return; // 成功发送，直接返回
                        }
                    }
                }
            }
            // 如果走到这里，说明没按照预期解析到
            log.warn("解析 JSON 失败，没有找到对应的文本内容: {}", jsonResult);

        }
        catch (Exception e) {
            log.error("召唤艾夕失败了...", e);
            try {
                if ("group".equals(messageType)) {
                    qqClient.sendGroupMsg(groupId, "[系统提示] 艾夕好像有点卡住了，检查一下后台日志吧。");
                }
                else {
                    qqClient.sendPrivateMsg(senderId, "[系统提示] 艾夕好像有点卡住了，检查一下后台日志吧。");
                }
            }
            catch (Exception ex) {
                log.error("发送错误提示也失败了", ex);
            }
        }
    }
}