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

package com.tencent.bk.job.execute.dao.impl;

import com.tencent.bk.job.common.constant.Bool;
import com.tencent.bk.job.common.constant.JobConstants;
import com.tencent.bk.job.common.constant.Order;
import com.tencent.bk.job.execute.dao.GseAgentTaskDAO;
import com.tencent.bk.job.execute.engine.consts.IpStatus;
import com.tencent.bk.job.execute.model.GseAgentTaskDTO;
import com.tencent.bk.job.execute.model.ResultGroupBaseDTO;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.SelectSeekStep1;
import org.jooq.generated.tables.GseTaskIpLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tencent.bk.job.common.constant.Order.DESCENDING;
import static org.jooq.impl.DSL.count;

@Repository
public class GseAgentTaskDAOImpl implements GseAgentTaskDAO {

    private static final GseTaskIpLog T_GSE_AGENT_TASK = GseTaskIpLog.GSE_TASK_IP_LOG;
    private final DSLContext CTX;

    @Autowired
    public GseAgentTaskDAOImpl(@Qualifier("job-execute-dsl-context") DSLContext CTX) {
        this.CTX = CTX;
    }

    @Override
    public void batchSaveGseAgentTasks(List<GseAgentTaskDTO> gseAgentTasks) {
        String sql = "replace into gse_task_ip_log (step_instance_id, execute_count, ip, status, start_time, " +
            "end_time, total_time, error_code, exit_code, tag, log_offset, display_ip, is_target,is_source) values " +
            "(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        Object[][] params = new Object[gseAgentTasks.size()][14];
        int batchCount = 0;
        for (GseAgentTaskDTO gseAgentTask : gseAgentTasks) {
            Object[] param = new Object[14];
            param[0] = gseAgentTask.getStepInstanceId();
            param[1] = gseAgentTask.getExecuteCount();
            param[2] = gseAgentTask.getCloudAreaAndIp();
            param[3] = gseAgentTask.getStatus();
            param[4] = gseAgentTask.getStartTime();
            param[5] = gseAgentTask.getEndTime();
            param[6] = gseAgentTask.getTotalTime();
            param[7] = gseAgentTask.getErrorCode();
            param[8] = gseAgentTask.getExitCode();
            param[9] = StringUtils.truncate(gseAgentTask.getTag(), JobConstants.RESULT_GROUP_TAG_MAX_LENGTH);
            param[10] = gseAgentTask.getScriptLogOffset();
            param[11] = gseAgentTask.getDisplayIp();
            param[12] = gseAgentTask.isTargetServer() ? 1 : 0;
            param[13] = gseAgentTask.isSourceServer() ? 1 : 0;
            params[batchCount++] = param;
        }
        CTX.batch(sql, params).execute();
    }

    @Override
    public void batchUpdateGseAgentTasks(long stepInstanceId, int executeCount, Collection<String> cloudAreaAndIps,
                                         Long startTime, Long endTime, IpStatus ipStatus) {
        String sql = "update gse_task_ip_log set start_time = ?,end_time = ?,status = ? where step_instance_id = ? " +
            "and execute_count = ? and ip = ?";
        Object[][] params = new Object[cloudAreaAndIps.size()][6];
        int batchCount = 0;
        for (String ip : cloudAreaAndIps) {
            Object[] param = new Object[6];
            param[0] = startTime;
            param[1] = endTime;
            param[2] = ipStatus.getValue();
            param[3] = stepInstanceId;
            param[4] = executeCount;
            param[5] = ip;
            params[batchCount++] = param;
        }
        CTX.batch(sql, params).execute();
    }

    @Override
    public int getSuccessIpCount(long stepInstanceId, int executeCount) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;

        return CTX.selectCount().from(t)
            .where(t.STATUS.in(IpStatus.LAST_SUCCESS.getValue(), IpStatus.SUCCESS.getValue()))
            .and(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.IS_TARGET.eq((byte)Bool.TRUE.getValue()))
            .fetchOne(0, Integer.class);
    }

    @Override
    public Map<IpStatus, Integer> countStepGseAgentTaskGroupByStatus(long stepInstanceId, int executeCount) {
        Result<?> result = CTX.select(T_GSE_AGENT_TASK.STATUS, count().as("ip_count")).from(T_GSE_AGENT_TASK)
            .where(T_GSE_AGENT_TASK.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(T_GSE_AGENT_TASK.EXECUTE_COUNT.eq(executeCount))
            .and(T_GSE_AGENT_TASK.IS_TARGET.eq((byte)Bool.TRUE.getValue()))
            .groupBy(T_GSE_AGENT_TASK.STATUS)
            .fetch();

        Map<IpStatus, Integer> agentTaskCountMap = new HashMap<>();
        if (result.size() != 0) {
            result.forEach(record -> {
                IpStatus status = IpStatus.valueOf(record.get(T_GSE_AGENT_TASK.STATUS));
                Integer count = (int) record.get("ip_count");
                agentTaskCountMap.put(status, count);
            });
        }
        return agentTaskCountMap;
    }

    @Override
    public List<GseAgentTaskDTO> getSuccessGseTaskIp(long stepInstanceId, int executeCount) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Result result = CTX.select(t.STEP_INSTANCE_ID, t.EXECUTE_COUNT, t.IP, t.STATUS, t.START_TIME, t.END_TIME,
            t.TOTAL_TIME, t.ERROR_CODE, t.EXIT_CODE, t.TAG, t.LOG_OFFSET, t.DISPLAY_IP, t.IS_TARGET, t.IS_SOURCE)
            .from(t)
            .where(t.STATUS.in(3, 9))
            .and(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.IS_TARGET.eq(Byte.valueOf("1")))
            .fetch();
        List<GseAgentTaskDTO> successGseIpList = new ArrayList<>();
        result.into(record -> {
            successGseIpList.add(extract(record));
        });
        return successGseIpList;
    }

    @Override
    public List<ResultGroupBaseDTO> listResultGroups(long stepInstanceId, int executeCount) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Result result = CTX.select(t.STATUS, t.TAG, count().as("ip_count")).from(t)
            .where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.IS_TARGET.eq(Byte.valueOf("1")))
            .groupBy(t.STATUS, t.TAG)
            .orderBy(t.STATUS.asc())
            .fetch();

        List<ResultGroupBaseDTO> resultGroups = new ArrayList<>();
        result.into(record -> {
            ResultGroupBaseDTO resultGroup = new ResultGroupBaseDTO();
            resultGroup.setResultType(record.get(t.STATUS));
            resultGroup.setTag(record.get(t.TAG));
            resultGroup.setAgentTaskCount((int) record.get("ip_count"));
            resultGroups.add(resultGroup);
        });
        return resultGroups;
    }

    @Override
    public List<GseAgentTaskDTO> listAgentTaskByResultType(Long stepInstanceId, Integer executeCount, Integer resultType,
                                                           String tag) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Result result = CTX.select(t.STEP_INSTANCE_ID, t.EXECUTE_COUNT, t.IP, t.STATUS, t.START_TIME, t.END_TIME,
            t.TOTAL_TIME, t.ERROR_CODE, t.EXIT_CODE, t.TAG, t.LOG_OFFSET, t.DISPLAY_IP, t.IS_TARGET, t.IS_SOURCE)
            .from(t)
            .where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.STATUS.eq(resultType))
            .and(t.TAG.eq(tag == null ? "" : tag))
            .and(t.IS_TARGET.eq(Byte.valueOf("1")))
            .fetch();

        List<GseAgentTaskDTO> ipLogs = new ArrayList<>();
        if (result.size() > 0) {
            result.into(record -> {
                ipLogs.add(extract(record));
            });
        }
        return ipLogs;
    }

    @Override
    public List<GseAgentTaskDTO> listAgentTaskByResultType(Long stepInstanceId, Integer executeCount, Integer resultType,
                                                           String tag, Integer limit, String orderField, Order order) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;

        List<Condition> conditions = new ArrayList<>();
        conditions.add(t.STEP_INSTANCE_ID.eq(stepInstanceId));
        conditions.add(t.EXECUTE_COUNT.eq(executeCount));
        conditions.add(t.STATUS.eq(resultType));
        conditions.add(t.TAG.eq(tag == null ? "" : tag));
        conditions.add(t.IS_TARGET.eq(Byte.valueOf("1")));

        SelectConditionStep select = CTX.select(t.STEP_INSTANCE_ID, t.EXECUTE_COUNT, t.IP, t.STATUS, t.START_TIME,
            t.END_TIME,
            t.TOTAL_TIME, t.ERROR_CODE, t.EXIT_CODE, t.TAG, t.LOG_OFFSET, t.DISPLAY_IP, t.IS_TARGET, t.IS_SOURCE)
            .from(t)
            .where(conditions);

        SelectSeekStep1 selectSeekStep = null;
        OrderField orderFieldEntity = buildOrderField(orderField, order);
        if (orderFieldEntity != null) {
            selectSeekStep = select.orderBy(orderFieldEntity);
        }

        SelectLimitPercentStep selectLimitPercentStep = null;
        if (limit != null && limit > 0) {
            if (selectSeekStep != null) {
                selectLimitPercentStep = selectSeekStep.limit(limit);
            } else {
                selectLimitPercentStep = select.limit(limit);
            }
        }

        List<GseAgentTaskDTO> ipLogs = new ArrayList<>();
        Result result;
        if (selectLimitPercentStep != null) {
            result = selectLimitPercentStep.fetch();
        } else if (selectSeekStep != null) {
            result = selectSeekStep.fetch();
        } else {
            result = select.fetch();
        }

        if (result.size() > 0) {
            result.into(record -> {
                ipLogs.add(extract(record));
            });
        }
        return ipLogs;
    }

    private OrderField buildOrderField(String field, Order order) {
        OrderField orderField = null;
        if (StringUtils.isNotBlank(field)) {
            if (field.equals(GseTaskIpLog.GSE_TASK_IP_LOG.TOTAL_TIME.getName())) {
                if (order == DESCENDING) {
                    orderField = GseTaskIpLog.GSE_TASK_IP_LOG.TOTAL_TIME.desc();
                } else {
                    orderField = GseTaskIpLog.GSE_TASK_IP_LOG.TOTAL_TIME.asc();
                }
            } else if (field.equals(GseTaskIpLog.GSE_TASK_IP_LOG.EXIT_CODE.getName())) {
                if (order == DESCENDING) {
                    orderField = GseTaskIpLog.GSE_TASK_IP_LOG.EXIT_CODE.desc();
                } else {
                    orderField = GseTaskIpLog.GSE_TASK_IP_LOG.EXIT_CODE.asc();
                }
            } else if (field.equals(GseTaskIpLog.GSE_TASK_IP_LOG.IP.getName())) {
                if (order == DESCENDING) {
                    orderField = GseTaskIpLog.GSE_TASK_IP_LOG.IP.desc();
                } else {
                    orderField = GseTaskIpLog.GSE_TASK_IP_LOG.IP.asc();
                }
            }
        }
        return orderField;
    }

    @Override
    public List<GseAgentTaskDTO> listGseAgentTasks(Long stepInstanceId, Integer executeCount, boolean onlyTargetIp) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        SelectConditionStep selectConditionStep = CTX.select(t.STEP_INSTANCE_ID, t.EXECUTE_COUNT, t.IP, t.STATUS,
            t.START_TIME, t.END_TIME,
            t.TOTAL_TIME, t.ERROR_CODE, t.EXIT_CODE, t.TAG, t.LOG_OFFSET, t.DISPLAY_IP, t.IS_TARGET, t.IS_SOURCE)
            .from(t)
            .where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount));
        if (onlyTargetIp) {
            selectConditionStep.and(t.IS_TARGET.eq(Byte.valueOf("1")));
        }
        Result result = selectConditionStep.fetch();
        List<GseAgentTaskDTO> ipLogList = new ArrayList<>();
        if (result.size() != 0) {
            result.map(record -> {
                ipLogList.add(extract(record));
                return null;
            });
        }
        return ipLogList;
    }

    private GseAgentTaskDTO extract(Record record) {
        if (record == null) {
            return null;
        }
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        GseAgentTaskDTO ipLog = new GseAgentTaskDTO();
        ipLog.setStepInstanceId(record.get(t.STEP_INSTANCE_ID));
        ipLog.setExecuteCount(record.get(t.EXECUTE_COUNT));
        ipLog.setCloudAreaAndIp(record.get(t.IP));
        ipLog.setStatus(record.get(t.STATUS));
        ipLog.setStartTime(record.get(t.START_TIME));
        ipLog.setEndTime(record.get(t.END_TIME));
        ipLog.setTotalTime(record.get(t.TOTAL_TIME));
        ipLog.setErrorCode(record.get(t.ERROR_CODE));
        ipLog.setExitCode(record.get(t.EXIT_CODE, Integer.class));
        ipLog.setTag(record.get(t.TAG));
        ipLog.setScriptLogOffset(record.get(t.LOG_OFFSET));
        ipLog.setDisplayIp(record.get(t.DISPLAY_IP));
        String[] cloudAreaIdAndIpArray = record.get(t.IP).split(":");
        ipLog.setCloudAreaId(Long.valueOf(cloudAreaIdAndIpArray[0]));
        ipLog.setIp(cloudAreaIdAndIpArray[1]);
        ipLog.setTargetServer(record.get(t.IS_TARGET) == 1);
        ipLog.setSourceServer(record.get(t.IS_SOURCE) == 1);
        return ipLog;
    }

    @Override
    public GseAgentTaskDTO getGseAgentTaskByIp(Long stepInstanceId, Integer executeCount, String ip) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Record record = CTX.select(t.STEP_INSTANCE_ID, t.EXECUTE_COUNT, t.IP, t.STATUS, t.START_TIME, t.END_TIME,
            t.TOTAL_TIME, t.ERROR_CODE, t.EXIT_CODE, t.TAG, t.LOG_OFFSET, t.DISPLAY_IP, t.IS_TARGET, t.IS_SOURCE)
            .from(t)
            .where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.IP.eq(ip))
            .fetchOne();
        return extract(record);
    }

    @Override
    public List<GseAgentTaskDTO> listAgentTasksByIps(Long stepInstanceId, Integer executeCount, String[] ipArray) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Result result = CTX.select(t.STEP_INSTANCE_ID, t.EXECUTE_COUNT, t.IP, t.STATUS, t.START_TIME, t.END_TIME,
            t.TOTAL_TIME, t.ERROR_CODE, t.EXIT_CODE, t.TAG, t.LOG_OFFSET, t.DISPLAY_IP, t.IS_TARGET, t.IS_SOURCE)
            .from(t)
            .where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.IP.in(ipArray))
            .and(t.IS_TARGET.eq(Byte.valueOf("1")))
            .fetch();
        List<GseAgentTaskDTO> ipLogList = new ArrayList<>();
        if (result.size() != 0) {
            result.into(record -> {
                ipLogList.add(extract(record));
            });
        }
        return ipLogList;
    }

    @Override
    public void deleteAllGseAgentTasks(long stepInstanceId, int executeCount) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        CTX.deleteFrom(t).where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq((executeCount)))
            .execute();
    }

    @Override
    public int getSuccessRetryCount(long stepInstanceId, String cloudAreaAndIp) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Record record = CTX.select(t.EXECUTE_COUNT).from(t).where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.IP.eq(cloudAreaAndIp))
            .and(t.STATUS.eq(IpStatus.SUCCESS.getValue()))
            .orderBy(t.EXECUTE_COUNT.desc())
            .limit(1)
            .fetchOne();
        if (record != null && record.size() > 0) {
            return record.getValue(t.EXECUTE_COUNT);
        } else {
            return 0;
        }
    }

    @Override
    public List<String> getTaskFileSourceIps(Long stepInstanceId, Integer executeCount) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Result result = CTX.select(t.IP).from(t).where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.IS_SOURCE.eq(Byte.valueOf("1")))
            .fetch();
        List<String> ips = new ArrayList<>();
        if (result != null && result.size() > 0) {
            result.into(record -> ips.add(record.getValue(t.IP)));
        }
        return ips;
    }

    @Override
    public List<String> fuzzySearchTargetIpsByIp(Long stepInstanceId, Integer executeCount, String searchIp) {
        GseTaskIpLog t = GseTaskIpLog.GSE_TASK_IP_LOG;
        Result result = CTX.select(t.IP)
            .from(t)
            .where(t.STEP_INSTANCE_ID.eq(stepInstanceId))
            .and(t.EXECUTE_COUNT.eq(executeCount))
            .and(t.DISPLAY_IP.like("%" + searchIp + "%"))
            .and(t.IS_TARGET.eq(Byte.valueOf("1")))
            .fetch();
        if (result == null || result.size() == 0) {
            return Collections.emptyList();
        }
        List<String> cloudIps = new ArrayList<>();
        result.into(record -> cloudIps.add(record.getValue(t.IP)));
        return cloudIps;
    }
}
