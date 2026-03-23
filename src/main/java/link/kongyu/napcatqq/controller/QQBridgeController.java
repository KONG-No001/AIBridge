package link.kongyu.napcatqq.controller;

import link.kongyu.napcatqq.service.QQBridgeService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    QQBridgeService bridgeService;

    // 接收 NapCat 推送的 QQ 消息
    @PostMapping("/webhook")
    public void receiveMessage(@RequestBody Map<String, Object> payload) {
        bridgeService.receiveMessage(payload);
    }

}
