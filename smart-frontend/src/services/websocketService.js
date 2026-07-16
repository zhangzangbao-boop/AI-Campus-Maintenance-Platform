/**
 * WebSocket 通知服务
 * 使用 SockJS + STOMP 协议连接到后端 WebSocket 服务
 */
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

class WebSocketService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 5000;
    this.subscriptions = new Map();
    this.userId = null;
  }

  /**
   * 连接 WebSocket
   * @param {string} token - JWT Token
   * @param {string} userId - 用户ID
   * @param {function} onMessage - 消息回调函数
   * @param {function} onConnect - 连接成功回调
   * @param {function} onError - 错误回调
   */
  connect(token, userId, onMessage, onConnect, onError) {
    if (this.connected) {
      console.log('[WebSocket] 已连接，跳过重复连接');
      return;
    }

    this.userId = userId;
    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8070/ws';

    console.log('[WebSocket] 正在连接:', wsUrl);

    // 创建 STOMP 客户端
    this.client = new Client({
      // 使用 SockJS 作为 WebSocket 工厂
      webSocketFactory: () => new SockJS(wsUrl),

      // 连接头（携带 JWT Token）
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },

      // 心跳配置
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      // 重连延迟
      reconnectDelay: this.reconnectDelay,

      // 连接成功
      onConnect: () => {
        console.log('[WebSocket] 连接成功');
        this.connected = true;
        this.reconnectAttempts = 0;

        // 订阅用户通知频道
        this.subscribe(`/topic/user/${userId}`, (message) => {
          try {
            const notification = JSON.parse(message.body);
            console.log('[WebSocket] 收到通知:', notification);
            onMessage(notification);
          } catch (e) {
            console.error('[WebSocket] 解析消息失败:', e);
          }
        });

        if (onConnect) onConnect();
      },

      // 连接失败
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP 错误:', frame);
        this.connected = false;
        if (onError) onError(frame);
      },

      // WebSocket 错误
      onWebSocketError: (event) => {
        console.error('[WebSocket] WebSocket 错误:', event);
        this.connected = false;
      },

      // WebSocket 关闭
      onWebSocketClose: () => {
        console.log('[WebSocket] 连接关闭');
        this.connected = false;
        this.subscriptions.clear();
      },

      // 连接断开
      onDisconnect: () => {
        console.log('[WebSocket] 断开连接');
        this.connected = false;
      },
    });

    // 激活连接
    this.client.activate();
  }

  /**
   * 订阅频道
   * @param {string} destination - 目标频道
   * @param {function} callback - 消息回调
   */
  subscribe(destination, callback) {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] 未连接，无法订阅:', destination);
      return;
    }

    if (this.subscriptions.has(destination)) {
      console.log('[WebSocket] 已订阅频道:', destination);
      return;
    }

    const subscription = this.client.subscribe(destination, callback);
    this.subscriptions.set(destination, subscription);
    console.log('[WebSocket] 订阅频道成功:', destination);
  }

  /**
   * 取消订阅
   * @param {string} destination - 目标频道
   */
  unsubscribe(destination) {
    const subscription = this.subscriptions.get(destination);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(destination);
      console.log('[WebSocket] 取消订阅:', destination);
    }
  }

  /**
   * 断开连接
   */
  disconnect() {
    if (this.client) {
      console.log('[WebSocket] 主动断开连接');

      // 取消所有订阅
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();

      // 断开连接
      this.client.deactivate();
      this.client = null;
      this.connected = false;
      this.userId = null;
    }
  }

  /**
   * 检查连接状态
   */
  isConnected() {
    return this.connected;
  }
}

// 导出单例
export default new WebSocketService();