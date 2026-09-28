package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WebSocketServer webSocketServer;


    //商家端取消订单
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Orders orders = new Orders();                                    // 1. 创建对象
        orders.setId(ordersCancelDTO.getId());                           // 2. 设置 id
        orders.setStatus(Orders.CANCELLED);                              // 3. 改状态
        orders.setCancelReason(ordersCancelDTO.getCancelReason());       // 4. 记录原因
        orders.setCancelTime(LocalDateTime.now());                       // 5. 记录时间
        orderMapper.update(orders);
    }

    //统计个状态的订单
    @Override
    public OrderStatisticsVO statistics() {
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed =orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed(toBeConfirmed);
        vo.setConfirmed(confirmed);
        vo.setDeliveryInProgress(deliveryInProgress);
        return vo;
    }
    //完成订单
    @Override
    public void complete(Long id) {
        // 只有「派送中」的订单才能完成，其余状态（含已完成、已取消）一律拒绝。
        // 用 equals 而不是 ==：Orders 的状态常量是 Integer，== 比的是引用，只靠 Integer 缓存侥幸成立。
        Orders orderDB = orderMapper.getById(id);
        if (orderDB == null || !Orders.DELIVERY_IN_PROGRESS.equals(orderDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 3. 构造更新对象，只设置需要修改的字段
        Orders orders = new Orders();
        orders.setId(id);
        orders.setStatus(Orders.COMPLETED);                 // 状态改为“已完成”
        orders.setDeliveryTime(LocalDateTime.now());        // 记录送达时间

        // 4. 执行更新
        orderMapper.update(orders);

    }
    //拒单
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        // 只有「待接单」的订单才能拒单
        Orders orderDB = orderMapper.getById(ordersRejectionDTO.getId());
        if (orderDB == null || !Orders.TO_BE_CONFIRMED.equals(orderDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = new Orders();
        orders.setId(ordersRejectionDTO.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    //接单
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 只有「待接单」的订单才能接单
        Orders orderDB = orderMapper.getById(ordersConfirmDTO.getId());
        if (orderDB == null || !Orders.TO_BE_CONFIRMED.equals(orderDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders =Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    @Override
    public void delivery(Long id) {
        // 只有「已接单」的订单才能派送（待接单需先接单）
        Orders orderDB = orderMapper.getById(id);
        if (orderDB == null || !Orders.CONFIRMED.equals(orderDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = new Orders();
        orders.setId(id);
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(orders);
    }

    //搜索订单
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());

        Page<Orders> page =orderMapper.pageQuery(ordersPageQueryDTO);
        List<Orders> ordersList = new ArrayList<>();
        if (page != null && !CollectionUtils.isEmpty(page.getResult())) {
            for (Orders orders : page.getResult()) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                String orderDishes =getOrderDishesStr(orders);
                orderVO.setOrderDishes(orderDishes);

                ordersList.add(orderVO);
            }
        }return new PageResult(page.getTotal(),ordersList);

    }

    @Override
    public void reminder(Long id) {
        //1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        //   订单不存在、或不属于当前登录用户时，统一提示"订单不存在"
        //   （不区分这两种情况，避免通过提示语探测出别人订单的id是否存在）
        if (ordersDB == null || !BaseContext.getCurrentId().equals(ordersDB.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //2. 只有待接单、已接单、派送中的订单才需要催单
        //   已完成、已取消的订单催了也没有意义，避免商家收到无效提醒
        Integer status = ordersDB.getStatus();
        if (!Orders.TO_BE_CONFIRMED.equals(status)
                && !Orders.CONFIRMED.equals(status)
                && !Orders.DELIVERY_IN_PROGRESS.equals(status)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //3. 通过 WebSocket 推送催单消息
        Map map = new HashMap();
        map.put("type", 2);                                 // 2 表示客户催单
        map.put("orderId", id);
        map.put("content", "订单号：" + ordersDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    private String getOrderDishesStr(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        List<String> orderDishList = orderDetailList.stream()
                .map(x -> x.getName() + "*" + x.getNumber())
                .collect(Collectors.toList());
        return String.join(";", orderDishList);
    }

    /**
     * 用户下单
     * <p>
     * 一次请求要写 3 张表（orders 插 1 条、order_detail 插 n 条、shopping_cart 清空），
     * 必须放在同一个事务里，否则中途失败会留下脏数据（比如订单建了、购物车没清）
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //1. 校验收货地址是否存在
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //2. 校验购物车是否为空
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //3. 向订单表插入 1 条数据
        //   下面 4 个字段前端都可能不传，必须兜底，否则全被 GlobalExceptionHandler 兜成"未知错误"：
        //   ① packAmount / tablewareNumber：Orders 里是基本类型 int，而 DTO 里是 Integer。
        //      BeanUtils.copyProperties 内部不做 null 判断，会把 null 直接塞给 int 的 setter 并抛
        //      FatalBeanException —— 它不是 BaseException。小程序未点过「餐具数量」弹窗时
        //      tablewareNumber 就是 null。
        //   ② tablewareStatus / deliveryStatus：DTO 里是 Integer、可以为 null，但 orders 表这两列是
        //      NOT NULL，且 OrderMapper.xml 的 insert 是无条件写入 —— 传 null 会撞
        //      "Column 'tableware_status' cannot be null"。
        //      默认值跟建表 DEFAULT 对齐：deliveryStatus=1 立即送出，tablewareStatus=1 按餐量提供。
        if (ordersSubmitDTO.getPackAmount() == null) {
            ordersSubmitDTO.setPackAmount(0);
        }
        if (ordersSubmitDTO.getTablewareNumber() == null) {
            ordersSubmitDTO.setTablewareNumber(0);
        }
        if (ordersSubmitDTO.getDeliveryStatus() == null) {
            ordersSubmitDTO.setDeliveryStatus(1);
        }
        if (ordersSubmitDTO.getTablewareStatus() == null) {
            ordersSubmitDTO.setTablewareStatus(1);
        }

        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);            // 未支付
        orders.setStatus(Orders.PENDING_PAYMENT);       // 待付款
        orders.setNumber(String.valueOf(System.currentTimeMillis()));  // 订单号
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());
        orders.setUserId(userId);
        orderMapper.insert(orders);

        //4. 向订单明细表插入 n 条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            // 忽略 id：购物车的自增主键不能带进 order_detail，否则两张表的 id 会撞车导致主键冲突
            BeanUtils.copyProperties(cart, orderDetail, "id");
            orderDetail.setOrderId(orders.getId());    // 关联订单 id（依赖 insert 回填主键）
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //5. 清空当前用户的购物车
        shoppingCartMapper.deleteByUserId(userId);

        //6. 封装返回结果
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }

    /**
     * 订单支付
     * <p>
     * 【模拟支付】本机 application-dev.yml 的 sky.wechat 只配了 appid/secret，
     * 没有 mchid / mchSerialNo / apiV3Key / 证书，WeChatPayUtil 一调用就会因为属性缺失抛异常；
     * 而且微信支付成功后的异步回调（notify）在本地也不存在。
     * 所以这里不调微信：直接返回一组结构合法的预支付参数，并同步把订单落库为「已支付/待接单」。
     * 将来接入真实微信支付时，把 paySuccess(...) 这一行挪到 /notify/paySuccess 回调里即可。
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {
        //1. 查询订单并校验状态
        Long userId = BaseContext.getCurrentId();
        Orders ordersDB = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }

        //2. 模拟支付成功，直接落库
        paySuccess(ordersPaymentDTO.getOrderNumber());

        //3. 返回合法的预支付参数（前端拿到后还会调 wx.requestPayment，参数仅作占位）
        return OrderPaymentVO.builder()
                .nonceStr(UUID.randomUUID().toString().replace("-", ""))
                .paySign("mock_pay_sign")
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .signType("RSA")
                .packageStr("prepay_id=mock_" + ordersPaymentDTO.getOrderNumber())
                .build();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo 商户订单号
     */
    @Override
    public void paySuccess(String outTradeNo) {
        //1. 根据订单号查询当前用户的订单
        Long userId = BaseContext.getCurrentId();
        Orders ordersDB = orderMapper.getByNumberAndUserId(outTradeNo, userId);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //2. 更新订单状态、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)     // 待接单
                .payStatus(Orders.PAID)             // 已支付
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

        // 3. 推送来单提醒 ← 加在这里
        Map map = new HashMap();
        map.put("type", 1);
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号：" + ordersDB.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 用户端历史订单分页查询
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   订单状态，可为 null
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        //1. 设置分页
        PageHelper.startPage(pageNum, pageSize);

        //2. 只查当前用户的订单
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //3. 逐条补上菜品明细，封装成 OrderVO（小程序历史订单列表每条都要展示菜品）
        List<OrderVO> list = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page == null ? 0L : page.getTotal(), list);
    }

    /**
     * 查询订单详情
     *
     * @param id 订单id
     * @return 订单及其明细
     */
    @Override
    public OrderVO details(Long id) {
        //1. 根据id查询订单
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            // 不加这个判断，下面 getByOrderId(null) 会 NPE，被兜底处理器吞成"未知错误"
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //2. 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //3. 封装成 OrderVO 返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     * <p>
     * 模拟支付模式下没有真实商户号，这里只做状态流转（待接单取消 → 支付状态置为退款），
     * 不调用 WeChatPayUtil.refund —— 一调就会因为 sky.wechat 缺配置而抛异常。
     *
     * @param id 订单id
     */
    @Override
    public void userCancelById(Long id) {
        //1. 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //2. 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        //   只有待付款、待接单可以取消
        if (ordersDB.getStatus() > Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        //3. 待接单状态下取消，说明用户已付款，支付状态置为退款
        if (Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            orders.setPayStatus(Orders.REFUND);
        }

        //4. 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单：把该订单的菜品重新加回购物车
     *
     * @param id 订单id
     */
    @Override
    public void repetition(Long id) {
        //1. 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        //2. 根据订单id查询订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //3. 把订单明细转换成购物车对象（忽略明细主键 id，让购物车自己生成）
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());

        //4. 批量写回购物车
        shoppingCartMapper.insertBatch(shoppingCartList);
    }
}
