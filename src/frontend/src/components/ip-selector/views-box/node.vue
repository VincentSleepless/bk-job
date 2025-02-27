<template>
    <div
        v-bkloading="{ isLoading }"
        class="ip-selector-view-node">
        <collapse-box
            ref="collapseBoxRef"
            name="node">
            <template #title>
                <span style="font-weight: bold;">【动态拓扑】</span>
                <span>
                    - 共
                    <span class="bk-ip-selector-number">{{ tableData.length }}</span>
                    个
                </span>
                <span v-if="newNodeNum">
                    ，新增
                    <span class="bk-ip-selector-number-success">{{ newNodeNum }}</span>
                    个
                </span>
                <span v-if="removedNodeList.length">
                    ，删除
                    <span class="bk-ip-selector-number-error">{{ removedNodeList.length }}</span>
                    个
                </span>
            </template>
            <template
                v-if="!context.readonly"
                #action>
                <extend-action>
                    <div @click="handleRemoveAll">
                        清除所有
                    </div>
                </extend-action>
            </template>
            <table>
                <tr
                    v-for="row in renderData"
                    :key="row.id"
                    :class="diffMap[genNodeKey(row)]">
                    <td
                        style="width: 30%;"
                        @click="handleShowHostList(row)">
                        <div class="cell">
                            <div class="cell-text">
                                {{ nodeNamePathMap[genNodeKey(row)] || `#${row.instance_id}` }}
                            </div>
                            <diff-tag :value="diffMap[genNodeKey(row)]" />
                        </div>
                    </td>
                    <td @click="handleShowHostList(row)">
                        <render-agent-statistics
                            :data="nodeAgentStaticMap[genNodeKey(row)]"
                            :loading="isAgentStatisticsLoading" />
                    </td>
                    <td
                        v-if="!context.readonly"
                        style="width: 60px;">
                        <bk-button
                            text
                            theme="primary"
                            @click="handleRemove(row)">
                            移除
                        </bk-button>
                    </td>
                </tr>
            </table>
            <div
                v-if="isShowPagination"
                style="padding: 0 10px 8px 0;">
                <bk-pagination
                    style="margin-top: 8px;"
                    v-bind="pagination"
                    @change="handlePaginationCurrentChange"
                    @limit-change="handlePaginationLimitChange" />
            </div>
        </collapse-box>
        <bk-dialog
            v-model="isShowNodeHostList"
            :draggable="false"
            header-position="left"
            :title="`【${nodeNamePathMap[genNodeKey(selectedNode)]}】动态拓扑主机预览`"
            :width="dialogWidth">
            <node-host-list
                :is-show="isShowNodeHostList"
                :node="selectedNode" />
            <template #footer>
                <bk-button
                    theme="primary"
                    @click="handleHideHostList">
                    关闭
                </bk-button>
            </template>
        </bk-dialog>
    </div>
</template>
<script setup>
    import _ from 'lodash';
    import {
        ref,
        shallowRef,
        watch,
    } from 'vue';

    import DiffTag from '../common/diff-tag.vue';
    import ExtendAction from '../common/extend-action.vue';
    import useDialogSize from '../hooks/use-dialog-size';
    import useIpSelector from '../hooks/use-ip-selector';
    import useLocalPagination from '../hooks/use-local-pagination';
    import Manager from '../manager';
    import {
        genNodeKey,
        getInvalidNodeList,
        getNodeDiffMap,
        getRemoveNodeList,
        groupNodeList,
    } from '../utils';

    import RenderAgentStatistics from './components/agent-statistics.vue';
    import CollapseBox from './components/collapse-box.vue';
    import NodeHostList from './components/node-host-list.vue';

    const props = defineProps({
        data: {
            type: Array,
            required: true,
        },
    });

    const emits = defineEmits(['change']);

    const collapseBoxRef = ref();
    const isLoading = ref(false);
    const isAgentStatisticsLoading = ref(false);
    const tableData = shallowRef([]);
    const nodeNamePathMap = shallowRef({});
    const nodeAgentStaticMap = shallowRef({});

    const validNodeList = shallowRef([]);
    const removedNodeList = shallowRef([]);
    const invalidNodeList = shallowRef([]);

    const resultList = shallowRef([]);

    const isShowNodeHostList = ref(false);
    const selectedNode = shallowRef({});
    const diffMap = shallowRef({});
    const newNodeNum = ref(0);

    let isInnerChange = false;

    const context = useIpSelector();

    const {
        isShowPagination,
        pagination,
        data: renderData,
        handlePaginationCurrentChange,
        handlePaginationLimitChange,
    } = useLocalPagination(tableData);

    const {
        width: dialogWidth,
    } = useDialogSize();

    const fetchData = () => {
        isLoading.value = true;
        const params = {
            [Manager.nameStyle('nodeList')]: props.data.map(item => ({
                [Manager.nameStyle('objectId')]: item.object_id,
                [Manager.nameStyle('instanceId')]: item.instance_id,
                [Manager.nameStyle('meta')]: item.meta,
            })),
        };
        Manager.service.fetchNodesQueryPath(params)
            .then((data) => {
                const validData = [];
                const namePathMap = {};
                data.forEach((item) => {
                    const tailNode = _.last(item);
                    validData.push(tailNode);
                    namePathMap[genNodeKey(tailNode)] = item.map(nodeData => nodeData.instance_name).join(' / ');
                });
                nodeNamePathMap.value = namePathMap;
                validNodeList.value = validData;
            })
            .finally(() => {
                isLoading.value = false;
            });
    };

    watch(() => props.data, () => {
        if (isInnerChange) {
            isInnerChange = false;
            return;
        }
        if (props.data.length > 0) {
            fetchData();
        } else {
            tableData.value = [];
        }
    }, {
        immediate: true,
    });

    watch(renderData, _.debounce((currentPageData) => {
        const params = {
            [Manager.nameStyle('nodeList')]: currentPageData.map(item => ({
                [Manager.nameStyle('objectId')]: item.object_id,
                [Manager.nameStyle('instanceId')]: item.instance_id,
                [Manager.nameStyle('meta')]: item.meta,
            })),
        };
        isAgentStatisticsLoading.value = true;
        Manager.service.fetchHostAgentStatisticsNodes(params)
            .then((data) => {
                const staticMap = {};
                data.forEach((item) => {
                    staticMap[genNodeKey(item.node)] = item.agent_statistics;
                });
                nodeAgentStaticMap.value = staticMap;
            })
            .finally(() => {
                isAgentStatisticsLoading.value = false;
            });
    }), 300);

    watch([validNodeList, resultList], () => {
        invalidNodeList.value = getInvalidNodeList(props.data, validNodeList.value);
        removedNodeList.value = getRemoveNodeList(props.data, context.originalValue);
        diffMap.value = getNodeDiffMap(props.data, context.originalValue, invalidNodeList.value);

        const {
            newList,
            originalList,
        } = groupNodeList(validNodeList.value, diffMap.value);

        newNodeNum.value = newList.legnth;

        tableData.value = [
            ...invalidNodeList.value,
            ...newList,
            ...removedNodeList.value,
            ...originalList,
        ];
        pagination.count = tableData.value.length;
    });

    const triggerChange = () => {
        isInnerChange = true;
        emits('change', 'nodeList', resultList.value);
    };

    // 删除指定节点
    const handleRemove = (nodeData) => {
        resultList.value = props.data.reduce((result, item) => {
            if (genNodeKey(item) !== genNodeKey(nodeData)) {
                result.push(item);
            }
            return result;
        }, []);
        if (diffMap.value[genNodeKey(nodeData)] !== 'invalid') {
            validNodeList.value = validNodeList.value.reduce((result, item) => {
                if (genNodeKey(item) !== genNodeKey(nodeData)) {
                    result.push(item);
                }
                return result;
            }, []);
        }
        triggerChange();
    };

    // 移除所有
    const handleRemoveAll = () => {
        resultList.value = [];
        validNodeList.value = [];
        triggerChange();
    };

    // 查看节点的主机列表
    const handleShowHostList = (node) => {
        isShowNodeHostList.value = true;
        selectedNode.value = node;
    };

    const handleHideHostList = () => {
        isShowNodeHostList.value = false;
    };

    defineExpose({
        refresh () {
            fetchData();
        },
        collapseToggle (toggle) {
            collapseBoxRef.value.toggle(toggle);
        },
    });
</script>
<style lang="postcss">
    @import "../styles/table.mixin.css";

    .ip-selector-view-node {
        @include table;
        @include view-table;
    }
</style>
