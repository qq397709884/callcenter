package org.zhongweixian.cc.fs;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.StringUtils;
import org.cti.cc.constant.Constants;
import org.cti.cc.constant.FsConstant;
import org.cti.cc.entity.RouteGetway;
import org.cti.cc.entity.Station;
import org.cti.cc.enums.StationType;
import org.cti.cc.mapper.StationMapper;
import org.cti.cc.po.CallInfo;
import org.cti.cc.po.CommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.zhongweixian.cc.EventType;
import org.zhongweixian.cc.cache.CacheService;
import org.zhongweixian.cc.configration.Handler;
import org.zhongweixian.cc.configration.HandlerContext;
import org.zhongweixian.cc.fs.event.FsHangupEvent;
import org.zhongweixian.cc.fs.event.base.FsBaseEvent;
import org.zhongweixian.cc.util.RandomUtil;
import org.zhongweixian.esl.inbound.Client;
import org.zhongweixian.esl.inbound.IEslEventListener;
import org.zhongweixian.esl.internal.Context;
import org.zhongweixian.esl.internal.IModEslApi;
import org.zhongweixian.esl.transport.CommandResponse;
import org.zhongweixian.esl.transport.SendMsg;
import org.zhongweixian.esl.transport.event.EslEvent;
import org.zhongweixian.esl.transport.message.EslMessage;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Create by caoliang on 2020/8/23
 */
@Component
public class FsListen {
    private Logger logger = LoggerFactory.getLogger(FsListen.class);

    private Station station;

    @Value("${spring.application.id}")
    private Integer applicationId;

    @Value("${fs.thread.num:16}")
    private Integer threadNum;

    @Autowired
    private StationMapper stationMapper;

    @Value("${audio.codecs:^^:G729:PCMU:PCMA}")
    private String codecs;

    private static final String SPLIT = ",";


    private ScheduledExecutorService checkFsThread = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("check-freeswitch-pool-%d").build());

    /**
     * 媒体集合
     */
    private Map<String, Client> fsClient = new HashMap<>();

    /**
     * 线程组
     */
    private Map<Integer, ThreadPoolExecutor> executorMap = new ConcurrentHashMap<>();

    /**
     * 多线程发送消息
     */
    private ThreadPoolExecutor sendThread = new ThreadPoolExecutor(8, 8, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactoryBuilder().setNameFormat("freeswitch-send-%d").build());


    @Autowired
    private HandlerContext handlerContext;

    @Autowired
    private CacheService cacheService;


    public void start() {
        station = stationMapper.selectByAppId(applicationId);
        for (int i = 0; i < threadNum; i++) {
            ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("fs-pool-" + i).build();
            ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), threadFactory);
            executorMap.put(i, executor);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("applicationType", StationType.FS_MEDIA.getType());
        params.put("applicationGroup", station.getApplicationGroup());
        List<Station> fsStations = stationMapper.selectListByMap(params);

        if (CollectionUtils.isEmpty(fsStations)) {
            return;
        }
        for (Station station : fsStations) {
            connect(station.getApplicationHost(), station.getApplicationPort(), station.getPwd());
        }
        checkFsThread.scheduleAtFixedRate(() -> {
            checkConnect();
        }, 2, 1, TimeUnit.MINUTES);
    }

    /**
     * 连接freeswitch
     *
     * @param host
     * @param port
     * @param password
     * @return
     */
    private Client connect(String host, Integer port, String password) {
        Client client = new Client();
        try {
            fsClient.put(host + ":" + port, client);
            client.connect(new InetSocketAddress(host, port), password, 2);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
        client.setEventSubscriptions(IModEslApi.EventFormat.PLAIN, "all");
        IEslEventListener listener = new IEslEventListener() {
            @Override
            public void onEslEvent(Context ctx, EslEvent event) {
                String eventName = event.getEventName();
                /**
                 * https://freeswitch.org/confluence/display/FREESWITCH/Event+List
                 */
                switch (event.getEventName()) {
                    case FsConstant.RE_SCHEDULE:
                        return;
                    case FsConstant.API:
                        return;
                    case FsConstant.HEARTBEAT:
                        return;
                    case FsConstant.RECV_RTCP_MESSAGE:
                        return;
                    case FsConstant.CHANNEL_CREATE:
                        break;
                    case FsConstant.CHANNEL_ORIGINATE:
                        break;
                    case FsConstant.CHANNEL_STATE:
                        return;
                    case FsConstant.CHANNEL_PROGRESS:
                        break;
                    case FsConstant.CHANNEL_CALLSTATE:
                        break;
                    case FsConstant.CALL_UPDATE:
                        break;
                    case FsConstant.CHANNEL_EXECUTE:
                        logger.debug("CHANNEL_EXECUTE:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.CHANNEL_PARK:
                        logger.debug("CHANNEL_PARK:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.CHANNEL_UNPARK:
                        break;
                    case FsConstant.PRIVATE_COMMAND:
                        break;
                    case FsConstant.CHANNEL_EXECUTE_COMPLETE:
                        logger.debug("CHANNEL_EXECUTE_COMPLETE:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.CHANNEL_HANGUP:
                        logger.debug("CHANNEL_HANGUP:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.CHANNEL_HANGUP_COMPLETE:
                        logger.debug("CHANNEL_HANGUP_COMPLETE:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.CHANNEL_OUTGOING:
                        logger.debug("CHANNEL_OUTGOING:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.CHANNEL_ANSWER:
                        logger.debug("CHANNEL_ANSWER :  {}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.CHANNEL_DESTROY:
                        break;
                    case FsConstant.CHANNEL_BRIDGE:
                        logger.debug("CHANNEL_BRIDGE :  {}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    case FsConstant.RECORD_START:
                        break;

                    case FsConstant.MEDIA_BUG_START:
                        break;

                    case FsConstant.MEDIA_BUG_STOP:
                        break;

                    case FsConstant.PLAYBACK_START:
                        logger.debug("PLAYBACK_START :{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;

                    case FsConstant.PLAYBACK_STOP:
                        logger.debug("PLAYBACK_STOP :{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;

                    case FsConstant.CHANNEL_UNBRIDGE:
                        break;

                    case FsConstant.CODEC:
                        return;

                    case FsConstant.RECV_INFO:
                        logger.debug("RECV_INFO:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        return;

                    case FsConstant.DTMF:
                        break;

                    case FsConstant.CHANNEL_PROGRESS_MEDIA:
                        break;

                    case FsConstant.RECORD_STOP:
                        break;
                    case FsConstant.CUSTOM:
                        break;
                    case FsConstant.RING_ASR:
                        //媒体识别回铃音
                        logger.debug("RING_ASR:{}", JSONObject.toJSONString(event.getEventHeaders()));
                        break;
                    default:
                        logger.debug("event:{}, hander:{}", event.getEventName(), event.getEventHeaders().toString());
                        return;
                }
                logger.debug("receive media:{} event:{}", event.getEventHeaders().get("ClpMS-IPv4"), event.getEventName());

                FsBaseEvent formatEvent = formatEvent(ctx, event, eventName);
                if (formatEvent == null) {
                    return;
                }
                formatEvent.setHostname(host + ":" + port);

                /**
                 * 一个callId挂机处理必须使用一个相同的线程
                 */
                ExecutorService executorService = null;
                if (formatEvent instanceof FsHangupEvent) {
                    CallInfo callInfo = cacheService.getCallInfo(formatEvent.getDeviceId());
                    if (callInfo == null) {
                        return;
                    }
                    executorService = executorMap.get(RandomUtil.getNum(callInfo.getCallId().toString(), threadNum));
                }
                if (executorService == null) {
                    executorService = executorMap.get(RandomUtil.getNum(formatEvent.getDeviceId(), threadNum));
                }
                executorService.execute(() -> {
                    try {
                        if (logger.isDebugEnabled()) {
                            logger.debug("ctx:{}, event:{}", ctx, event);
                        }
                        Handler handler = handlerContext.getInstance(formatEvent.getEventName());
                        if (handler != null) {
                            handler.handleEvent(formatEvent);
                        }
                    } catch (Exception e) {
                        logger.error("deviceId:{} eventName:{} errorMessage:{}", event.getEventHeaders().get("Unique-ID"), eventName, e.getMessage(), e);
                    }
                });
            }

            @Override
            public void onClose() {
                client.close();
            }
        };

        client.addEventListener(listener);
        logger.info("connect {}:{} success", host, port);
        return client;
    }

    private FsBaseEvent formatEvent(Context context, EslEvent eslEvent, String eventName) {
        try {
            Class clzss = EventType.getClassByCmd(eventName);
            if (clzss == null) {
                return null;
            }
            Map map = eslEvent.getEventHeaders();
            JSONObject json = new JSONObject(map);
            if (StringUtils.isBlank(eventName)) {
                return null;
            }
            FsBaseEvent event = (FsBaseEvent) JSON.toJavaObject(json, clzss);
            event.setEventName(eventName);
            event.setContext(context);
            return event;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     *
     */
    private void checkConnect() {
        Map<String, Object> params = new HashMap<>();
        params.put("applicationType", 4);
        params.put("applicationGroup", station.getApplicationGroup());
        List<Station> fsStations = stationMapper.selectListByMap(params);

        for (Station station : fsStations) {
            try {
                Client client = fsClient.get(station.getHost());
                if (!client.isActivate()) {
                    logger.info("freeswitch {}:{} is close", station.getApplicationHost(), station.getApplicationPort());
                    client.close();
                    connect(station.getApplicationHost(), station.getApplicationPort(), station.getPwd());
                }
            } catch (Exception e) {

            }
        }
    }

    /**
     *
     */
    public void stop() {
        if (!fsClient.isEmpty()) {
            logger.info("freeswitch client stop");
            fsClient.forEach((i, client) -> {
                if (client.isActivate()) {
                    client.close();
                }
            });
        }
        checkFsThread.shutdown();
        executorMap.forEach((i, executor) -> {
            executor.shutdown();
        });
    }


    /**
     * 发起呼叫
     *
     * @param routeGetway
     * @param display
     * @param called
     * @param deviceId
     * @param sipHeaders
     */
    public void makeCall(RouteGetway routeGetway, String display, String called, String deviceId, String... sipHeaders) {
        String media = RandomUtil.getRandomKey(fsClient.keySet());
        makeCall(media, routeGetway, display, called, deviceId, sipHeaders);
    }

    /**
     * 发起呼叫
     * <p>
     * bgapi originate {return_ring_ready=true,sip_contact_user=01017818388,ring_asr=true,absolute_codec_string=^^:G729:PCMU:PCMA,origination_caller_id_number=01017718388,origination_caller_id_name=01017718388,origination_uuid=192.168.1.1-25-5f8fe34b-1bb-76,sip_auto_answer=true}sofia/external/8731279@192.168.177.183:8880 &park()
     *
     * @param media
     * @param routeGetway
     * @param display
     * @param called
     * @param deviceId
     * @param sipHeaders
     */
    public void makeCall(String media, RouteGetway routeGetway, String display, String called, String deviceId, String... sipHeaders) {
        called = called + Constants.AT + routeGetway.getMediaHost() + Constants.CO + routeGetway.getMediaPort();
        if (StringUtils.isNotBlank(routeGetway.getCallerPrefix())) {
            display = routeGetway.getCallerPrefix() + display;
        }
        if (StringUtils.isNotBlank(routeGetway.getCalledPrefix())) {
            called = routeGetway.getCalledPrefix() + called;
        }
        StringBuffer sipBuffer = new StringBuffer();
        if (sipHeaders != null) {
            for (int i = 0; i < sipHeaders.length; i++) {
                sipBuffer.append(sipHeaders[i]);
                if (i < sipHeaders.length - 1) {
                    sipBuffer.append(SPLIT);
                }
            }
        }
        if (StringUtils.isNoneBlank(routeGetway.getSipHeader1())) {
            if (StringUtils.isNoneBlank(sipBuffer)) {
                sipBuffer.append(SPLIT);
            }
            sipBuffer.append(routeGetway.getSipHeader1());
        }
        if (StringUtils.isNoneBlank(routeGetway.getSipHeader2())) {
            if (StringUtils.isNoneBlank(sipBuffer)) {
                sipBuffer.append(SPLIT);
            }
            sipBuffer.append(routeGetway.getSipHeader2());
        }
        if (StringUtils.isNoneBlank(routeGetway.getSipHeader3())) {
            if (StringUtils.isNoneBlank(sipBuffer)) {
                sipBuffer.append(SPLIT);
            }
            sipBuffer.append(routeGetway.getSipHeader3());
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{return_ring_ready=true").append(SPLIT)
                .append("sip_contact_user=").append(display).append(SPLIT)
                .append("ring_asr=true").append(SPLIT)
                .append("absolute_codec_string=").append(codecs).append(SPLIT)
                .append("origination_caller_id_number=").append(display).append(SPLIT)
                .append("origination_caller_id_name=").append(display).append(SPLIT)
                .append("origination_uuid=").append(deviceId);
        if (StringUtils.isNoneBlank(sipBuffer)) {
            builder.append(SPLIT).append(sipBuffer);
        }
        builder.append("}").append("sofia/" + routeGetway.getProfile() + "/").append(called).append(" &park");
        CompletableFuture future = fsClient.get(media).sendBackgroundApiCommand(FsConstant.ORIGINATE, builder.toString());
        try {
            logger.info("call response: {}", future.get());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 桥接电话
     *
     * @param media
     * @param deviceId1
     * @param deviceId2
     */
    public void callBridge(String media, String deviceId1, String deviceId2) {
        this.sendArgs(media, deviceId1, FsConstant.SET, FsConstant.PARK_AFTER_BRIDGE);
        this.sendArgs(media, deviceId1, FsConstant.SET, FsConstant.HANGUP_AFTER_BRIDGE);
        this.sendArgs(media, deviceId2, FsConstant.SET, FsConstant.HANGUP_AFTER_BRIDGE);
        this.sendArgs(media, deviceId2, FsConstant.SET, FsConstant.PARK_AFTER_BRIDGE);
        StringBuilder builder = new StringBuilder();
        builder.append(deviceId1).append(FsConstant.SPACE).append(deviceId2);
        sendBgapiMessage(media, FsConstant.BRIDGE, builder.toString());
    }

    /**
     * @param media
     * @param from
     * @param to
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void transferCall(String media, String from, String to) {
        StringBuilder builder = new StringBuilder();
        builder.append(from).append("  -both 'set:hangup_after_bridge=false,set:park_after_bridge=true,park:' inline ");
        fsClient.get(media).sendBackgroundApiCommand(FsConstant.TRANSFER, builder.toString());
    }


    /**
     * @param sendMsg
     */
    public void sendMessage(String media, SendMsg sendMsg) {
        if (StringUtils.isBlank(media)) {
            logger.error("send to media is null, sendMsg:{}", sendMsg);
            return;
        }
        sendThread.execute(() -> {
            fsClient.get(media).sendMessage(sendMsg);
        });
    }

    /**
     * 不使用线程池发送
     */
    public void sendSyncMessage(String media, SendMsg sendMsg) {
        fsClient.get(media).sendMessage(sendMsg);
    }

    /**
     * @param media
     * @param cmd
     * @param args
     */
    public void sendBgapiMessage(String media, String cmd, String args) {
        fsClient.get(media).sendBackgroundApiCommand(cmd, args);
    }


    /**
     * 应答
     *
     * @param media
     * @param deviceId
     * @return
     */
    public EslMessage answer(String media, String deviceId) {
        return fsClient.get(media).sendApiCommand("uuid_phone_event", deviceId + " talk");
    }

    /**
     * 放音
     *
     * @param media
     * @param deviceId
     * @param file
     */
    public void playback(String media, String deviceId, String file) {
        SendMsg playback = new SendMsg(deviceId);
        playback.addCallCommand(FsConstant.EXECUTE);
        playback.addExecuteAppName(FsConstant.PLAYBACK);
        playback.addExecuteAppArg(file);
        playback.addAsync();
        this.sendMessage(media, playback);
    }

    /**
     * 停止放音
     *
     * @param media
     * @param deviceId
     */
    public void playbreak(String media, String deviceId) {
        SendMsg playback = new SendMsg(deviceId);
        playback.addCallCommand(FsConstant.EXECUTE);
        playback.addExecuteAppName(FsConstant.BREAK_);
        this.sendMessage(media, playback);
    }

    public void hangupCall(String media, Long callId, String deviceId) {
        SendMsg hangupMsg = new SendMsg(deviceId);
        hangupMsg.addCallCommand(FsConstant.EXECUTE);
        hangupMsg.addExecuteAppName(FsConstant.HANGUP);
        hangupMsg.addExecuteAppArg(FsConstant.NORMAL_CLEARING);
        logger.info("hangup call:{}, device:{}", callId, deviceId);
        this.sendMessage(media, hangupMsg);
    }

    public void sendArgs(String media, String deviceId, String name, String arg) {
        SendMsg msg = new SendMsg(deviceId);
        msg.addCallCommand(FsConstant.EXECUTE);
        msg.addExecuteAppName(name);
        msg.addExecuteAppArg(arg);
        msg.addAsync();
        this.sendMessage(media, msg);
    }

    /**
     * bgapi uuid_transfer  sswitch-301-60f4c396-59-85 -both 'set:hangup_after_bridge=false,set:park_after_bridge=true,park:' inline
     *
     * @param media
     * @param deviceId
     */
    public void hold(String media, String deviceId) {
        StringBuilder builder = new StringBuilder();
        builder.append(deviceId).append("  -both 'set:hangup_after_bridge=false,set:park_after_bridge=true,park:' inline ");
        fsClient.get(media).sendBackgroundApiCommand(FsConstant.TRANSFER, builder.toString());
    }

}
