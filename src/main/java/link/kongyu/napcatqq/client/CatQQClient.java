package link.kongyu.napcatqq.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.JSONBody;
import com.dtflys.forest.annotation.Post;
import org.springframework.stereotype.Component;

/**
 * @author Luojun
 * @version v1.0.0
 * @since 2026/3/21
 */

@BaseRequest(
        baseURL = "http://127.0.0.1:3000/",
        headers = {"Authorization: Bearer COj5PKivLVPRvgOn"}
)
@Component
public interface CatQQClient {

    @Post("/send_private_msg")
    String sendPrivateMsg(@JSONBody("user_id") long userId, @JSONBody("message") String message);

    @Post("/send_group_msg")
    String sendGroupMsg(@JSONBody("group_id") long groupId, @JSONBody("message") String message);

    // 发送“正在输入...”状态 (NapCat / OneBot V11 非标准扩展)
    // 状态: 1=正在输入, 2=输入结束/取消
    @Post("/set_input_status")
    String setInputStatus(@JSONBody("user_id") long userId, @JSONBody("event_type") int status);
}