package link.kongyu.aiBridge.controller;

import link.kongyu.aiBridge.service.QQBridgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author Luojun
 * @version v1.0.0
 * @since 2026/3/21
 */

@RestController
@RequestMapping("/qq")
public class QQBridgeController {

    private static final Logger log = LoggerFactory.getLogger(QQBridgeController.class);

    @Autowired
    QQBridgeService bridgeService;

    // 接收 NapCat 推送的 QQ 消息
    @PostMapping("/webhook")
    public void receiveMessage(@RequestBody Map<String, Object> payload) {
        log.info("Received payload from NapCat: {}", payload);
        bridgeService.receiveMessage(payload);
    }

    // 健康检查接口，用于验证 Bridge 是否存活
    @GetMapping("/health")
    public String healthCheck() {
        return "AI Bridge is alive!";
    }

}
