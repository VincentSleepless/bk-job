/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.manage.common;

import com.tencent.bk.job.common.cc.model.InstanceTopologyDTO;
import com.tencent.bk.job.common.cc.sdk.CmdbClientFactory;
import com.tencent.bk.job.common.cc.service.CloudAreaService;
import com.tencent.bk.job.common.constant.CcNodeTypeEnum;
import com.tencent.bk.job.common.constant.ErrorCode;
import com.tencent.bk.job.common.constant.ResourceScopeTypeEnum;
import com.tencent.bk.job.common.exception.InternalException;
import com.tencent.bk.job.common.gse.service.AgentStateClient;
import com.tencent.bk.job.common.gse.v2.model.resp.AgentState;
import com.tencent.bk.job.common.model.dto.ApplicationDTO;
import com.tencent.bk.job.common.model.dto.ApplicationHostDTO;
import com.tencent.bk.job.common.model.dto.DynamicGroupWithHost;
import com.tencent.bk.job.common.util.JobContextUtil;
import com.tencent.bk.job.manage.dao.ApplicationDAO;
import com.tencent.bk.job.manage.model.web.vo.CcTopologyNodeVO;
import com.tencent.bk.job.manage.model.web.vo.DynamicGroupInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 拓扑信息缓存、更新工具类
 *
 * @since 18/12/2019 11:23
 */
@Slf4j
@Component
public class TopologyHelper {

    private static final BlockingQueue<InstanceTopologyDTO> TOPOLOGY_INFO_QUEUE = new ArrayBlockingQueue<>(10);

    private static final Map<Long, Map<String, Map<Long, String>>> BIZ_NODE_TYPE_NAME_MAP = new ConcurrentHashMap<>();

    private final AgentStateClient agentStateClient;
    private final ApplicationDAO applicationDAO;
    private final CloudAreaService cloudAreaService;

    @Autowired
    public TopologyHelper(ApplicationDAO applicationDAO,
                          AgentStateClient agentStateClient,
                          CloudAreaService cloudAreaService) {
        this.applicationDAO = applicationDAO;
        this.agentStateClient = agentStateClient;
        this.cloudAreaService = cloudAreaService;
    }

    /**
     * 递归遍历查找节点路径
     *
     * @param treeNode   节点树
     * @param targetNode 目标节点
     * @return 节点路径
     */
    public static List<InstanceTopologyDTO> findTopoPath(
        InstanceTopologyDTO treeNode,
        InstanceTopologyDTO targetNode
    ) {
        if (treeNode == null || targetNode == null) return null;
        if (treeNode.equals(targetNode)) return Collections.singletonList(treeNode);
        List<InstanceTopologyDTO> childList = treeNode.getChild();
        if (childList == null || childList.isEmpty()) return null;
        for (InstanceTopologyDTO childNode : childList) {
            List<InstanceTopologyDTO> subPath = findTopoPath(childNode, targetNode);
            if (subPath != null) {
                List<InstanceTopologyDTO> pathNodeList = new ArrayList<>();
                pathNodeList.add(treeNode);
                pathNodeList.addAll(subPath);
                return pathNodeList;
            }
        }
        return null;
    }

    /**
     * 递归遍历查找多个节点路径
     *
     * @param treeNode    节点树
     * @param targetNodes 要查找的目标节点
     * @return 节点路径列表
     */
    public static List<List<InstanceTopologyDTO>> findTopoPaths(InstanceTopologyDTO treeNode,
                                                                List<InstanceTopologyDTO> targetNodes) {
        if (treeNode == null || targetNodes == null) return null;
        if (targetNodes.isEmpty()) return Collections.emptyList();
        Map<InstanceTopologyDTO, List<InstanceTopologyDTO>> pathMap = new HashMap<>();
        Set<InstanceTopologyDTO> targetNodeSet = new HashSet<>(targetNodes);
        if (targetNodeSet.contains(treeNode)) {
            List<InstanceTopologyDTO> path = new ArrayList<>();
            path.add(treeNode);
            pathMap.put(treeNode, path);
        }
        if (pathMap.size() == targetNodeSet.size()) {
            return genResult(pathMap, targetNodes);
        }
        List<InstanceTopologyDTO> childList = treeNode.getChild();
        if (childList == null || childList.isEmpty()) return genResult(pathMap, targetNodes);
        for (InstanceTopologyDTO childNode : childList) {
            List<InstanceTopologyDTO> notFoundTargetNodes = new ArrayList<>(targetNodes);
            notFoundTargetNodes.removeAll(pathMap.keySet());
            List<List<InstanceTopologyDTO>> subPathList = findTopoPaths(childNode, notFoundTargetNodes);
            for (List<InstanceTopologyDTO> subPath : subPathList) {
                if (subPath != null) {
                    InstanceTopologyDTO targetNode = subPath.get(subPath.size() - 1);
                    subPath.add(0, treeNode);
                    pathMap.put(targetNode, subPath);
                    if (pathMap.size() == targetNodeSet.size()) {
                        return genResult(pathMap, targetNodes);
                    }
                }
            }
        }
        return genResult(pathMap, targetNodes);
    }

    private static List<List<InstanceTopologyDTO>> genResult(
        Map<InstanceTopologyDTO, List<InstanceTopologyDTO>> pathMap,
        List<InstanceTopologyDTO> targetNodes
    ) {
        List<List<InstanceTopologyDTO>> resultList = new ArrayList<>();
        for (InstanceTopologyDTO targetNode : targetNodes) {
            List<InstanceTopologyDTO> pathList = pathMap.get(targetNode);
            resultList.add(pathList);
        }
        return resultList;
    }

    /**
     * 将 CMDB 返回数据转换成作业平台拓扑结构
     *
     * @param bizInstTopo CMDB 返回数据
     * @return 作业平台拓扑结构
     */
    public static CcTopologyNodeVO convertToCcTopologyTree(InstanceTopologyDTO bizInstTopo) {
        CcTopologyNodeVO node = new CcTopologyNodeVO();
        node.setObjectId(bizInstTopo.getObjectId());
        node.setObjectName(bizInstTopo.getObjectName());
        node.setInstanceId(bizInstTopo.getInstanceId());
        node.setInstanceName(bizInstTopo.getInstanceName());
        // 模块不需要展开,其他的默认展开
        if (CcNodeTypeEnum.MODULE.getType().equals(bizInstTopo.getObjectId())) {
            node.setExpanded(false);
        }
        List<CcTopologyNodeVO> children = new ArrayList<>();
        if (bizInstTopo.getChild() != null && !bizInstTopo.getChild().isEmpty()) {
            for (InstanceTopologyDTO childBizInstTopo : bizInstTopo.getChild()) {
                children.add(convertToCcTopologyTree(childBizInstTopo));
            }
        }
        node.setChild(children);
        return node;
    }

    /**
     * 将作业平台内部动态分组信息转换为展示用动态分组信息
     *
     * @param dynamicGroupWithHost 作业平台内部动态分组信息
     * @return 展示用动态分组信息
     */
    public static DynamicGroupInfoVO convertToDynamicGroupInfoVO(DynamicGroupWithHost dynamicGroupWithHost) {
        if (dynamicGroupWithHost == null) {
            return null;
        }
        DynamicGroupInfoVO dynamicGroupInfoVO = new DynamicGroupInfoVO();
        dynamicGroupInfoVO.setScopeType(ResourceScopeTypeEnum.BIZ.getValue());
        dynamicGroupInfoVO.setScopeId(dynamicGroupWithHost.getBizId().toString());
        dynamicGroupInfoVO.setScopeName(dynamicGroupWithHost.getBizName());
        dynamicGroupInfoVO.setId(dynamicGroupWithHost.getId());
        dynamicGroupInfoVO.setOwner(dynamicGroupWithHost.getOwner());
        dynamicGroupInfoVO.setOwnerName(dynamicGroupWithHost.getOwnerName());
        dynamicGroupInfoVO.setName(dynamicGroupWithHost.getName());
        dynamicGroupInfoVO.setType(dynamicGroupWithHost.getType());
        if (dynamicGroupWithHost.getIpListStatus() != null) {
            dynamicGroupInfoVO.setIpListStatus(dynamicGroupWithHost.getIpListStatus().parallelStream()
                .map(ApplicationHostDTO::toVO).collect(Collectors.toList()));
        } else {
            dynamicGroupInfoVO.setIpListStatus(null);
        }
        return dynamicGroupInfoVO;
    }

    @PostConstruct
    private void initCache() {
        TopologyNameCacheThread topologyNameCacheThread = new TopologyNameCacheThread();
        topologyNameCacheThread.start();

    }

    public InstanceTopologyDTO getTopologyTreeByApplication(ApplicationDTO applicationInfo) {
        InstanceTopologyDTO instanceTopology = CmdbClientFactory.getCmdbClient(JobContextUtil.getUserLang())
            .getBizInstTopology(Long.parseLong(applicationInfo.getScope().getId()));
        if (instanceTopology == null) {
            return null;
        }
        if (StringUtils.isEmpty(instanceTopology.getInstanceName())) {
            instanceTopology.setInstanceName(applicationInfo.getName());
        }
        boolean addFlag = TOPOLOGY_INFO_QUEUE.offer(instanceTopology);
        if (!addFlag) {
            log.warn("Fail to add topologyTree into cache queue");
        }
        return instanceTopology;
    }

    public List<Long> getBizSetSubBizIds(ApplicationDTO appInfo) {
        if (appInfo.isAllBizSet()) {
            return applicationDAO.listAllBizAppBizIds();
        } else if (appInfo.isBizSet()) {
            return appInfo.getSubBizIds();
        } else {
            FormattingTuple msg = MessageFormatter.format(
                "app {} is not bizSet app, please check, detail:{}",
                appInfo.getId(),
                appInfo
            );
            throw new InternalException(msg.getMessage(), ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 根据 IP 地址列表批量获取机器 Agent 状态
     *
     * @param bizId       业务 ID
     * @param cloudIpList IP 地址列表
     * @return 机器 Agent 状态信息列表
     */
    public List<ApplicationHostDTO> getIpStatusListByIps(long bizId, List<String> cloudIpList) {
        List<ApplicationHostDTO> ipInfoList = new ArrayList<>();
        if (CollectionUtils.isEmpty(cloudIpList)) {
            return ipInfoList;
        }

        Map<String, AgentState> agentStateMap = agentStateClient.batchGetAgentState(cloudIpList);
        for (String cloudIp : cloudIpList) {
            ApplicationHostDTO ipInfo = new ApplicationHostDTO();
            ipInfo.setCloudAreaId(Long.valueOf(cloudIp.split(":")[0]));
            ipInfo.setBizId(bizId);
            ipInfo.setIp(cloudIp.split(":")[1]);
            AgentState agentState = agentStateMap.get(cloudIp);
            if (agentState != null) {
                ipInfo.setGseAgentStatus(agentState.getStatusCode());
            } else {
                log.warn("Cannot find agentState by ip {}", cloudIp);
            }
            ipInfoList.add(ipInfo);
        }
        return ipInfoList;
    }

    /**
     * 更新拓扑节点名称信息缓存
     *
     * @param topology        拓扑节点
     * @param nodeTypeNameMap 待更新的节点名称对应表
     */
    private void processTopologyNodeName(
        InstanceTopologyDTO topology,
        Map<String, Map<Long, String>> nodeTypeNameMap
    ) {
        if (CcNodeTypeEnum.BIZ.getType().equals(topology.getObjectId())) {
            Long appId = topology.getInstanceId();
            if (BIZ_NODE_TYPE_NAME_MAP.get(appId) == null) {
                BIZ_NODE_TYPE_NAME_MAP.put(appId, new ConcurrentHashMap<>(3));
            }
            // Root node, app, find all child
            if (CollectionUtils.isNotEmpty(topology.getChild())) {
                topology.getChild().forEach(child -> processTopologyNodeName(child, BIZ_NODE_TYPE_NAME_MAP.get(appId)));
            }
        } else {
            // Wrong topology tree
            if (nodeTypeNameMap == null) {
                throw new RuntimeException("Uninitialized node type name map!");
            }
            // Check and init node name map for current type
            Map<Long, String> nodeNameMap = nodeTypeNameMap.get(topology.getObjectId());
            if (nodeNameMap == null) {
                nodeNameMap = new ConcurrentHashMap<>(1);
                nodeTypeNameMap.put(topology.getObjectId(), nodeNameMap);
            }
            if (StringUtils.isNotBlank(topology.getInstanceName())) {
                nodeNameMap.put(topology.getInstanceId(), topology.getInstanceName());
            }
            if (CollectionUtils.isNotEmpty(topology.getChild())) {
                topology.getChild().forEach(child -> processTopologyNodeName(child, nodeTypeNameMap));
            }
        }
    }

    class TopologyNameCacheThread extends Thread {
        @Override
        public void run() {
            this.setName("Topology-Name-Update-Thread");
            while (true) {
                try {
                    InstanceTopologyDTO topology = TOPOLOGY_INFO_QUEUE.take();
                    processTopologyNodeName(topology, null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Error while process topology name!", e);
                }
            }
        }
    }
}
