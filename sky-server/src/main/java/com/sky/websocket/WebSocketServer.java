package com.sky.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务端
 * <p>
 * 客户端连接地址：ws://ip:port/ws/{sid}，sid为客户端自己起的标识，只用来区分不同的连接。
 * （管理端前端传的是随机字符串，不需要与用户id、订单号有任何关系；
 * 若将来要做「只推给某一个客户端」，调用sendToClient时传对应的sid即可。）
 * <p>
 * 注意：本类实例由WebSocket容器在每次握手时创建，不是普通的Spring单例，
 * 因此不要在本类中依赖@Autowired注入的成员变量，共享状态统一放在静态字段中。
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    /**
     * 存放会话对象，key为客户端标识sid
     */
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("客户端：{} 建立连接", sid);
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息后调用的方法
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到来自客户端：{} 的信息：{}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("连接断开：{}", sid);
        sessionMap.remove(sid);
    }

    /**
     * 群发消息（如来单提醒、客户催单）
     *
     * @param message 消息内容，一般为JSON字符串
     */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            try {
                // 同步发送，一个客户端的异常不影响其它客户端
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("向客户端发送消息失败：{}", e.getMessage());
            }
        }
    }

    /**
     * 向指定客户端发送消息
     *
     * @param sid     客户端标识
     * @param message 消息内容，一般为JSON字符串
     */
    public void sendToClient(String sid, String message) {
        Session session = sessionMap.get(sid);
        if (session == null) {
            log.warn("客户端 {} 尚未建立连接，消息发送失败", sid);
            return;
        }
        try {
            session.getBasicRemote().sendText(message);
        } catch (Exception e) {
            log.error("向客户端 {} 发送消息失败：{}", sid, e.getMessage());
        }
    }
}
