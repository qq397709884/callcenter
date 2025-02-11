package org.zhongweixian.cc.service.impl;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.cti.cc.constant.Constants;
import org.cti.cc.entity.Agent;
import org.cti.cc.entity.AgentStateLog;
import org.cti.cc.mapper.AgentGroupMapper;
import org.cti.cc.mapper.AgentMapper;
import org.cti.cc.mapper.AgentSipMapper;
import org.cti.cc.mapper.AgentStateLogMapper;
import org.cti.cc.mapper.base.BaseMapper;
import org.cti.cc.po.AgentInfo;
import org.cti.cc.po.AgentState;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zhongweixian.cc.cache.CacheService;
import org.zhongweixian.cc.command.GroupHandler;
import org.zhongweixian.cc.service.AgentService;
import org.zhongweixian.cc.websocket.response.AgentStateResppnse;

import java.util.List;

/**
 * Create by caoliang on 2020/10/28
 */
@Component
public class AgentServiceImpl extends BaseServiceImpl<Agent> implements AgentService {

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private AgentGroupMapper agentGroupMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    protected AgentStateLogMapper agentStateLogMapper;

    @Autowired
    private AgentSipMapper agentSipMapper;

    @Autowired
    protected GroupHandler groupHandler;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Override
    BaseMapper<Agent> baseMapper() {
        return agentMapper;
    }

    @Value("${spring.application.id:}")
    private Integer appId;

    @Override
    public AgentInfo getAgentInfo(String agentKey) {
        AgentInfo agentInfo = cacheService.getAgentInfo(agentKey);
        Agent agent = agentMapper.selectAgent(agentKey);
        if (agent == null) {
            return null;
        }
        if (agentInfo == null) {
            agentInfo = new AgentInfo();
        }
        BeanUtils.copyProperties(agent, agentInfo);
        agentInfo.setSips(this.getAgentSips(agent.getId()));
        return agentInfo;
    }


    @Override
    public List<Long> getAgentGroups(Long agentId) {
        return agentGroupMapper.selectByAgent(agentId);
    }

    @Override
    public List<String> getAgentSips(Long agentId) {
        return agentSipMapper.selectByAgent(agentId);
    }

    @Override
    public void syncAgentStateMessage(AgentInfo agentInfo) {
        try {
            if (StringUtils.isBlank(agentInfo.getHost())) {
                return;
            }
            if (agentInfo.getAgentState() == AgentState.READY) {
                groupHandler.agentFree(agentInfo);
            }
            if (agentInfo.getBeforeState() == AgentState.READY) {
                groupHandler.agentNotReady(agentInfo);
            }
            if (agentInfo.getAgentState() == AgentState.AFTER || agentInfo.getBeforeState() == AgentState.TALKING) {
                //更新坐席服务时间
                agentInfo.setServiceTime(agentInfo.getStateTime());
            }
            AgentStateResppnse response = new AgentStateResppnse();
            response.setId(agentInfo.getId());
            response.setAgentKey(agentInfo.getAgentKey());
            response.setCompanyId(agentInfo.getCompanyId());
            response.setSipPhone(agentInfo.getSipPhone());
            response.setGroupId(agentInfo.getGroupId());
            response.setHost(agentInfo.getHost());
            response.setGroupIds(agentInfo.getGroupIds());
            response.setSips(agentInfo.getSips());
            response.setCallId(agentInfo.getCallId());
            response.setLoginTime(agentInfo.getLoginTime());
            response.setAgentState(agentInfo.getAgentState());
            response.setLoginType(agentInfo.getLoginType());
            response.setWorkType(agentInfo.getWorkType());
            response.setStateTime(agentInfo.getStateTime());
            response.setBeforeState(agentInfo.getBeforeState());
            response.setBeforeTime(agentInfo.getBeforeTime());
            response.setMaxReadyTime(agentInfo.getMaxReadyTime());
            response.setTotalReadyTime(agentInfo.getTotalReadyTime());
            response.setMaxTalkTime(agentInfo.getMaxTalkTime());
            response.setTotalTalkTime(agentInfo.getTotalTalkTime());
            response.setTotalRingTimes(agentInfo.getTotalRingTimes());
            response.setTotalAnswerTimes(agentInfo.getTotalAnswerTimes());
            response.setReadyTimes(agentInfo.getReadyTimes());
            response.setNotReadyTimes(agentInfo.getNotReadyTimes());
            response.setTotalAfterTime(agentInfo.getTotalAfterTime());
            response.setAppId(appId);

            logger.info("send mq agent:{} state:{}", agentInfo.getAgentKey(), agentInfo.getAgentState());
            rabbitTemplate.convertAndSend(Constants.AGENT_STATE_EXCHANGE, Constants.DEFAULT_KEY, JSON.toJSONString(response));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        saveAgentLog(agentInfo);

    }

    @Override
    public Agent getAgentBySip(String sip) {
        return agentMapper.selectAgentBySip(sip);
    }

    /**
     * 更新到数据库
     *
     * @param agentInfo
     * @return
     */
    @Override
    public void saveAgentLog(AgentInfo agentInfo) {
        AgentStateLog agentStateLog = new AgentStateLog();
        agentStateLog.setAgentId(agentInfo.getId());
        agentStateLog.setCallId(agentInfo.getCallId());
        agentStateLog.setAgentKey(agentInfo.getAgentKey());
        agentStateLog.setBeforeTime(agentInfo.getBeforeTime());
        agentStateLog.setBeforeState(agentInfo.getBeforeState().name());
        agentStateLog.setState(agentInfo.getAgentState().name());
        agentStateLog.setStateTime(agentInfo.getStateTime());
        agentStateLog.setHost(agentInfo.getHost());
        agentStateLog.setCompanyId(agentInfo.getCompanyId());
        agentStateLog.setGroupId(agentInfo.getGroupId());
        agentStateLog.setCts(agentInfo.getStateTime() / 1000);
        agentStateLog.setUts(agentInfo.getStateTime() / 1000);
        agentStateLog.setLoginType(agentInfo.getLoginType());
        agentStateLog.setWorkType(agentInfo.getWorkType());
        agentStateLog.setRemoteAddress(agentInfo.getRemoteAddress());
        //持续时长
        agentStateLog.setDuration(agentInfo.getBeforeTime() == 0 ? 0 : (int) (agentInfo.getStateTime() - agentInfo.getBeforeTime()));
        rabbitTemplate.convertAndSend(Constants.AGENT_STATE_LOG_EXCHANGE, Constants.DEFAULT_KEY, JSON.toJSONString(agentStateLog));
    }
}
